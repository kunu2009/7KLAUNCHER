package com.sevenk.launcher.photoeditor

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.sevenk.launcher.R
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoEditorActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private var originalUri: Uri? = null
    private var workingBitmap: Bitmap? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers may not support persistable grants; continue regardless
            }
            loadBitmap(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_editor)

        imageView = findViewById(R.id.previewImage)

        findViewById<View>(R.id.btnPick).setOnClickListener { startPick() }
        findViewById<View>(R.id.btnRotate).setOnClickListener { rotate90() }
        findViewById<View>(R.id.btnFlipH).setOnClickListener { flip(horizontal = true) }
        findViewById<View>(R.id.btnFlipV).setOnClickListener { flip(horizontal = false) }
        findViewById<View>(R.id.btnSave).setOnClickListener { saveCopy() }
        findViewById<View>(R.id.btnCrop).setOnClickListener {
            Toast.makeText(this, "Crop coming soon (uCrop integration)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPick() {
        try {
            pickImage.launch(arrayOf("image/*"))
        } catch (t: Throwable) {
            Toast.makeText(this, "Picker failed: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBitmap(uri: Uri) {
        try {
            originalUri = uri
            val src = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            workingBitmap?.recycle()
            workingBitmap = src.copy(Bitmap.Config.ARGB_8888, true)
            imageView.setImageBitmap(workingBitmap)
        } catch (e: IOException) {
            Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rotate90() {
        val bmp = workingBitmap ?: return
        val m = Matrix().apply { postRotate(90f) }
        workingBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        imageView.setImageBitmap(workingBitmap)
        bmp.recycle()
    }

    private fun flip(horizontal: Boolean) {
        val bmp = workingBitmap ?: return
        val m = Matrix().apply { if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f) }
        workingBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        imageView.setImageBitmap(workingBitmap)
        bmp.recycle()
    }

    private fun saveCopy() {
        val bmp = workingBitmap ?: run {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
            return
        }
        val filename = "IMG_EDIT_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SevenK Editor")
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                    throw IOException("Compress failed")
                }
            }
            Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Cleanup the failed row
            resolver.delete(uri, null, null)
            Toast.makeText(this, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        workingBitmap?.recycle()
        workingBitmap = null
    }
}
