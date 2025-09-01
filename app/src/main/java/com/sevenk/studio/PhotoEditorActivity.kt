package com.sevenk.studio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.sevenk.launcher.R
import com.sevenk.launcher.databinding.ActivityPhotoEditorBinding
import com.yalantis.ucrop.UCrop
import java.io.File

class PhotoEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoEditorBinding
    private var currentImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                currentImageUri = it
                binding.previewImage.setImageURI(it)
            }
        }
    }

    private val ucropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { UCrop.getOutput(it) }?.let {
                currentImageUri = it
                binding.previewImage.setImageURI(it)
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            result.data?.let { UCrop.getError(it) }?.let {
                Toast.makeText(this, it.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPick.setOnClickListener { pickImage() }
        binding.btnEdit.setOnClickListener { startCrop() } // Renamed from btnCrop
        binding.btnSave.setOnClickListener { saveToGallery() }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        imagePickerLauncher.launch(intent)
    }

    private fun startCrop() {
        val sourceUri = currentImageUri ?: run {
            Toast.makeText(this, "Pick an image first", Toast.LENGTH_SHORT).show()
            return
        }

        val destinationFileName = "studio_crop_${System.currentTimeMillis()}.png"
        val destinationUri = Uri.fromFile(File(cacheDir, destinationFileName))

        val ucrop = UCrop.of(sourceUri, destinationUri)
        // You can add more uCrop options here if needed
        ucropLauncher.launch(ucrop.getIntent(this))
    }

    private fun saveToGallery() {
        val uri = currentImageUri ?: run {
            Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("Could not open input stream")
            val name = "7kstudio_${System.currentTimeMillis()}.png"
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/7KSTUDIO")
            }

            val dest = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (dest != null) {
                contentResolver.openOutputStream(dest)?.use { out -> input.copyTo(out) }
                Toast.makeText(this, "Saved to Gallery/7KSTUDIO", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            }
        } catch (t: Throwable) {
            Toast.makeText(this, "Save error: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }
}