package com.sevenk.calcvault

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import android.provider.OpenableColumns
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

class ShareImportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI; do work then finish
        val action = intent?.action
        val type = intent?.type
        val uris = mutableListOf<Uri>()
        if (Intent.ACTION_SEND == action && type != null) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
        } else if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (list != null) uris.addAll(list)
        }
        if (uris.isEmpty()) {
            finish(); return
        }
        val imported = importAndEncrypt(uris)
        if (imported > 0) {
            Toast.makeText(this, "Imported $imported to Vault", Toast.LENGTH_SHORT).show()
            promptDeleteOriginals(uris)
        }
        // Optionally open vault UI after import
        startActivity(Intent(this, VaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun importAndEncrypt(uris: List<Uri>): Int {
        val vaultDir = File(filesDir, "vault").apply { if (!exists()) mkdirs() }
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        var count = 0
        for (uri in uris) {
            try {
                val name = queryDisplayName(uri) ?: ("import_" + System.currentTimeMillis())
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val outFile = File(vaultDir, "$safe.enc")
                val finalFile = uniqueFile(outFile)
                contentResolver.openInputStream(uri)?.use { input ->
                    val enc = EncryptedFile.Builder(
                        this,
                        finalFile,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()
                    enc.openFileOutput().use { out -> input.copyTo(out) }
                }
                count++
            } catch (_: Throwable) { /* ignore failed item */ }
        }
        return count
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Throwable) { null }
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

    private fun promptDeleteOriginals(uris: List<Uri>) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                startIntentSenderForResult(pi.intentSender, 802, null, 0, 0, 0)
            } else {
                // Best-effort delete for older versions
                for (u in uris) try { contentResolver.delete(u, null, null) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) { /* ignore */ }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 802) {
            val msg = if (resultCode == Activity.RESULT_OK) "Originals removed" else "Kept originals"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
