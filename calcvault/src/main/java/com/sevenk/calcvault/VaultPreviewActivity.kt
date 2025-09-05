package com.sevenk.calcvault

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.BaseColumns
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.sevenk.calcvault.R
import java.io.File

class VaultPreviewActivity : AppCompatActivity() {
    private lateinit var files: MutableList<File>
    private var index: Int = 0

    private lateinit var imgView: ImageView
    private lateinit var videoView: VideoView
    private lateinit var txtTitle: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var btnRestore: Button
    private lateinit var btnRestoreRemove: Button
    private lateinit var btnDelete: Button
    private lateinit var btnClose: Button

    private lateinit var masterKey: MasterKey

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault_preview)

        @Suppress("DEPRECATION")
        val list = intent.getStringArrayListExtra("files") ?: arrayListOf()
        files = list.map { File(it) }.toMutableList()
        index = intent.getIntExtra("index", 0).coerceIn(0, (files.size - 1).coerceAtLeast(0))

        masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

        imgView = findViewById(R.id.previewImage)
        videoView = findViewById(R.id.previewVideo)
        txtTitle = findViewById(R.id.txtTitle)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnRestore = findViewById(R.id.btnRestore)
        btnRestoreRemove = findViewById(R.id.btnRestoreRemove)
        btnDelete = findViewById(R.id.btnDelete)
        btnClose = findViewById(R.id.btnClose)

        btnPrev.setOnClickListener { move(-1) }
        btnNext.setOnClickListener { move(1) }
        btnClose.setOnClickListener { finish() }
        btnDelete.setOnClickListener { confirmDelete() }
        btnRestore.setOnClickListener { restoreCurrent(keepInVault = true) }
        btnRestoreRemove.setOnClickListener { restoreCurrent(keepInVault = false) }

        render()
    }

    private fun move(delta: Int) {
        if (files.isEmpty()) return
        index = (index + delta).mod(files.size)
        render()
    }

    private fun render() {
        if (files.isEmpty()) { finish(); return }
        val f = files[index]
        txtTitle.text = f.nameWithoutExtension

        val baseName = f.nameWithoutExtension.lowercase()
        val isImage = baseName.endsWith(".jpg") || baseName.endsWith(".jpeg") || baseName.endsWith(".png") || baseName.endsWith(".webp") || baseName.endsWith(".gif")
        val isVideo = baseName.endsWith(".mp4") || baseName.endsWith(".3gp") || baseName.endsWith(".mkv") || baseName.endsWith(".webm")

        if (isImage) {
            showImage(f)
        } else if (isVideo) {
            showVideo(f)
        } else {
            imgView.visibility = View.GONE
            videoView.visibility = View.GONE
            Toast.makeText(this, "Unknown type", Toast.LENGTH_SHORT).show()
        }

        btnPrev.isEnabled = files.size > 1
        btnNext.isEnabled = files.size > 1
    }

    private fun showImage(encFile: File) {
        try {
            val ef = EncryptedFile.Builder(this, encFile, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
            ef.openFileInput().use { input ->
                val bytes = input.readBytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                imgView.setImageBitmap(bmp)
            }
            imgView.visibility = View.VISIBLE
            videoView.visibility = View.GONE
        } catch (t: Throwable) {
            imgView.visibility = View.GONE
            videoView.visibility = View.GONE
            Toast.makeText(this, "Failed to preview image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVideo(encFile: File) {
        try {
            // Decrypt to temp file in cache and play
            val temp = File(cacheDir, encFile.nameWithoutExtension)
            val ef = EncryptedFile.Builder(this, encFile, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
            ef.openFileInput().use { input -> temp.outputStream().use { out -> input.copyTo(out) } }
            videoView.setVideoPath(temp.absolutePath)
            videoView.visibility = View.VISIBLE
            imgView.visibility = View.GONE
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                videoView.start()
            }
        } catch (t: Throwable) {
            imgView.visibility = View.GONE
            videoView.visibility = View.GONE
            Toast.makeText(this, "Failed to preview video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete() {
        val f = files.getOrNull(index) ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete from Vault?")
            .setMessage(f.name)
            .setPositiveButton("Delete") { _, _ -> deleteCurrent() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCurrent() {
        val f = files.getOrNull(index) ?: return
        try {
            // Attempt to also remove any copies from gallery by display name
            val originalName = f.nameWithoutExtension
            val lower = originalName.lowercase()
            val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")
            val isVideo = lower.endsWith(".mp4") || lower.endsWith(".3gp") || lower.endsWith(".mkv") || lower.endsWith(".webm")
            fun deleteByName(collection: Uri) {
                try {
                    val proj = arrayOf(BaseColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                    val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
                    val args = arrayOf(originalName)
                    contentResolver.query(collection, proj, sel, args, null)?.use { c ->
                        val ids = mutableListOf<Long>()
                        val idIdx = c.getColumnIndexOrThrow(BaseColumns._ID)
                        while (c.moveToNext()) ids.add(c.getLong(idIdx))
                        if (ids.isNotEmpty()) {
                            val uris = ids.map { id -> Uri.withAppendedPath(collection, id.toString()) }
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= 30) {
                                    val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                                    startIntentSenderForResult(pi.intentSender, 902, null, 0, 0, 0)
                                } else {
                                    for (u in uris) try { contentResolver.delete(u, null, null) } catch (_: Throwable) {}
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
            }
            if (isImage) deleteByName(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            if (isVideo) deleteByName(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

            val thumbs = File(f.parentFile, ".thumbs")
            val thumbFile = File(thumbs, f.nameWithoutExtension + ".jpg")
            if (thumbFile.exists()) thumbFile.delete()
            f.delete()
            files.removeAt(index)
            if (files.isEmpty()) { finish(); return }
            if (index >= files.size) index = files.size - 1
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            render()
        } catch (_: Throwable) {
            Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreCurrent(keepInVault: Boolean) {
        val f = files.getOrNull(index) ?: return
        val name = f.nameWithoutExtension // original name with extension
        val lower = name.lowercase()
        val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")
        val isVideo = lower.endsWith(".mp4") || lower.endsWith(".3gp") || lower.endsWith(".mkv") || lower.endsWith(".webm")
        val collection: Uri
        val mime: String
        if (isImage) {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mime = when {
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".webp") -> "image/webp"
                lower.endsWith(".gif") -> "image/gif"
                else -> "image/jpeg"
            }
        } else if (isVideo) {
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mime = "video/mp4"
        } else {
            Toast.makeText(this, "Unknown type", Toast.LENGTH_SHORT).show(); return
        }
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (isImage) "Pictures/7KVAULT" else "Movies/7KVAULT")
            }
            val uri = contentResolver.insert(collection, values) ?: throw IllegalStateException("No uri")
            val ef = EncryptedFile.Builder(this, f, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
            contentResolver.openOutputStream(uri)?.use { out ->
                ef.openFileInput().use { input -> input.copyTo(out) }
            }
            // Trigger media scan (helpful on some devices)
            MediaScannerConnection.scanFile(this, arrayOf(name), arrayOf(mime), null)

            Toast.makeText(this, "Restored to gallery", Toast.LENGTH_SHORT).show()
            if (!keepInVault) {
                deleteCurrent()
            }
        } catch (t: Throwable) {
            Toast.makeText(this, "Restore failed: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }
}
