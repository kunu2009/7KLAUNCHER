package com.sevenk.calcvault

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.sevenk.calcvault.databinding.ActivityVaultBinding
import com.sevenk.calcvault.security.BiometricAuthManager
import java.io.File
import android.app.RecoverableSecurityException

class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private lateinit var adapter: VaultAdapter
    private lateinit var biometricAuthManager: BiometricAuthManager

    private val REQ_PICK_MEDIA = 501
    private val REQ_DELETE_ORIGINALS = 802

    private lateinit var vaultDir: File
    private lateinit var thumbsDir: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        biometricAuthManager = BiometricAuthManager(this) { result ->
            when (result) {
                BiometricAuthManager.BiometricAuthResult.SUCCESS -> {
                    setupVault()
                }
                else -> {
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
        biometricAuthManager.authenticate()
    }

    private fun setupVault() {
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerVault.layoutManager = LinearLayoutManager(this)
        adapter = VaultAdapter(mutableListOf()) { file -> onVaultItemClicked(file) }
        binding.recyclerVault.adapter = adapter

        binding.btnImport.setOnClickListener { launchPicker() }
        binding.btnClose.setOnClickListener { finish() }

        setupStorage()
        refreshList()
    }

    private fun promptDeleteOriginals(uris: List<Uri>) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                startIntentSenderForResult(pi.intentSender, REQ_DELETE_ORIGINALS, null, 0, 0, 0)
            } else if (Build.VERSION.SDK_INT >= 29) {
                for (u in uris) {
                    try {
                        contentResolver.delete(u, null, null)
                    } catch (rse: RecoverableSecurityException) {
                        try {
                            startIntentSenderForResult(rse.userAction.actionIntent.intentSender, REQ_DELETE_ORIGINALS, null, 0, 0, 0)
                            break
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (_: Throwable) { }
    }

    private fun launchPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQ_PICK_MEDIA)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_MEDIA && resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            data?.data?.let { uris.add(it) }
            data?.clipData?.let {
                for (i in 0 until it.itemCount) {
                    it.getItemAt(i)?.uri?.let { uri -> uris.add(uri) }
                }
            }
            if (uris.isNotEmpty()) {
                val flags = (data?.flags ?: 0) and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                for (u in uris) {
                    try { contentResolver.takePersistableUriPermission(u, flags) } catch (_: Throwable) {}
                }
                importAndEncrypt(uris)
                promptDeleteOriginals(uris)
            }
        } else if (requestCode == REQ_DELETE_ORIGINALS) {
            val msg = if (resultCode == Activity.RESULT_OK) "Originals removed" else "Kept originals"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importAndEncrypt(uris: List<Uri>) {
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        for (uri in uris) {
            try {
                val name = queryDisplayName(contentResolver, uri) ?: ("import_" + System.currentTimeMillis())
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val finalFile = uniqueFile(File(vaultDir, "$safe.enc"))
                contentResolver.openInputStream(uri)?.use { input ->
                    EncryptedFile.Builder(this, finalFile, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
                        .openFileOutput().use { out -> input.copyTo(out) }
                }
            } catch (_: Throwable) {}
        }
        refreshList()
    }

    private fun uniqueFile(base: File): File {
        if (!base.exists()) return base
        var idx = 1
        val name = base.nameWithoutExtension
        val ext = base.extension
        while (true) {
            val f = File(base.parentFile, "$name($idx).$ext")
            if (!f.exists()) return f
            idx++
        }
    }

    private fun queryDisplayName(cr: ContentResolver, uri: Uri): String? {
        return try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Throwable) { null }
    }

    private fun refreshList() {
        val files = if (vaultDir.exists()) vaultDir.listFiles()?.toList().orEmpty() else emptyList()
        adapter.submitFiles(files)
    }

    private fun setupStorage() {
        vaultDir = File(filesDir, "vault").apply { if (!exists()) mkdirs() }
        thumbsDir = File(vaultDir, ".thumbs").apply { if (!exists()) mkdirs() }
        try { File(vaultDir, ".nomedia").apply { if (!exists()) createNewFile() } } catch (_: Throwable) {}
        try { File(thumbsDir, ".nomedia").apply { if (!exists()) createNewFile() } } catch (_: Throwable) {}
    }

    private fun onVaultItemClicked(file: File) {
        // This is a simplified preview. A real app would need a more robust solution.
    }
}