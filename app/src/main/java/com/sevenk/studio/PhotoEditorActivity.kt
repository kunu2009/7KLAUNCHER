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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Canvas
import android.graphics.Paint
import java.io.InputStream
import java.io.OutputStream

class PhotoEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoEditorBinding
    private var currentImageUri: Uri? = null
    private var currentBitmap: Bitmap? = null
    private var brightnessProgress: Int = 100 // 0..200, default 100
    private var contrastProgress: Int = 100 // 0..200, default 100
    private var currentFilter: Filter = Filter.NONE

    private enum class Filter { NONE, WARM, COOL, BW, VIVID }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                currentImageUri = it
                loadBitmapFromUri(it)?.let { bmp ->
                    currentBitmap = bmp
                    resetAdjustments()
                    renderPreview()
                } ?: run {
                    binding.previewImage.setImageURI(it)
                }
            }
        }
    }

    private val ucropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { UCrop.getOutput(it) }?.let {
                currentImageUri = it
                loadBitmapFromUri(it)?.let { bmp ->
                    currentBitmap = bmp
                    resetAdjustments()
                    renderPreview()
                } ?: run {
                    binding.previewImage.setImageURI(it)
                }
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
        // Support both Edit and Crop buttons for now
        binding.btnEdit.setOnClickListener { startCrop() }
        val cropBtn: android.view.View? = try { binding.root.findViewById(R.id.btnCrop) } catch (_: Throwable) { null }
        cropBtn?.setOnClickListener { startCrop() }
        binding.btnSave.setOnClickListener { saveToGallery() }

        // Editing operations
        val rotateBtn: android.view.View? = try { binding.root.findViewById(R.id.btnRotate) } catch (_: Throwable) { null }
        rotateBtn?.setOnClickListener { rotateImage(90f) }
        val flipHBtn: android.view.View? = try { binding.root.findViewById(R.id.btnFlipH) } catch (_: Throwable) { null }
        flipHBtn?.setOnClickListener { flipImage(horizontal = true) }
        val flipVBtn: android.view.View? = try { binding.root.findViewById(R.id.btnFlipV) } catch (_: Throwable) { null }
        flipVBtn?.setOnClickListener { flipImage(horizontal = false) }

        // Adjustments UI
        setupAdjustmentsUI()
        setupFilterButtons()
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
        val bitmap = currentBitmap
        if (bitmap == null) {
            // Fallback to copying picked/cropped URI
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
            return
        }

        try {
            val name = "7kstudio_${System.currentTimeMillis()}.png"
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/7KSTUDIO")
            }
            val dest = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (dest != null) {
                // Apply adjustments + filter to a new bitmap before saving
                val processed = applyMatrixToBitmap(bitmap, buildCombinedColorMatrix())
                contentResolver.openOutputStream(dest)?.use { out ->
                    processed.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Toast.makeText(this, "Saved to Gallery/7KSTUDIO", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            }
        } catch (t: Throwable) {
            Toast.makeText(this, "Save error: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Helpers ----
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            // Sample down if very large
            val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsBounds) }
            val reqW = 2048
            val reqH = 2048
            val inSample = computeInSampleSize(optsBounds.outWidth, optsBounds.outHeight, reqW, reqH)
            val opts = BitmapFactory.Options().apply { inSampleSize = inSample.coerceAtLeast(1) }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var inSample = 1
        var halfW = srcW / 2
        var halfH = srcH / 2
        while ((halfW / inSample) >= reqW && (halfH / inSample) >= reqH) {
            inSample *= 2
        }
        return inSample
    }

    private fun rotateImage(degrees: Float) {
        val src = currentBitmap ?: return
        try {
            val m = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            currentBitmap = rotated
            renderPreview()
        } catch (t: Throwable) {
            Toast.makeText(this, "Rotate failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun flipImage(horizontal: Boolean) {
        val src = currentBitmap ?: return
        try {
            val m = Matrix().apply { postScale(if (horizontal) -1f else 1f, if (horizontal) 1f else -1f, src.width / 2f, src.height / 2f) }
            val flipped = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            currentBitmap = flipped
            renderPreview()
        } catch (t: Throwable) {
            Toast.makeText(this, "Flip failed", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Adjustments & Filters ----
    private fun setupAdjustmentsUI() {
        val seekB: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekBrightness) } catch (_: Throwable) { null }
        val seekC: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekContrast) } catch (_: Throwable) { null }
        seekB?.progress = brightnessProgress
        seekC?.progress = contrastProgress
        val listener = object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                when (sb?.id) {
                    R.id.seekBrightness -> brightnessProgress = progress
                    R.id.seekContrast -> contrastProgress = progress
                }
                renderPreview()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        }
        seekB?.setOnSeekBarChangeListener(listener)
        seekC?.setOnSeekBarChangeListener(listener)
    }

    private fun setupFilterButtons() {
        fun setFilter(f: Filter) { currentFilter = f; renderPreview() }
        try { binding.root.findViewById<android.view.View>(R.id.btnFilterNone)?.setOnClickListener { setFilter(Filter.NONE) } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnFilterWarm)?.setOnClickListener { setFilter(Filter.WARM) } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnFilterCool)?.setOnClickListener { setFilter(Filter.COOL) } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnFilterBW)?.setOnClickListener { setFilter(Filter.BW) } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnFilterVivid)?.setOnClickListener { setFilter(Filter.VIVID) } } catch (_: Throwable) {}
    }

    private fun resetAdjustments() {
        brightnessProgress = 100
        contrastProgress = 100
        currentFilter = Filter.NONE
        val seekB: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekBrightness) } catch (_: Throwable) { null }
        val seekC: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekContrast) } catch (_: Throwable) { null }
        seekB?.progress = brightnessProgress
        seekC?.progress = contrastProgress
    }

    private fun renderPreview() {
        val src = currentBitmap ?: return
        // For preview, avoid creating a new bitmap by using ColorFilter on ImageView when possible
        val matrix = buildCombinedColorMatrix()
        val filter = ColorMatrixColorFilter(matrix)
        binding.previewImage.colorFilter = filter
        binding.previewImage.setImageBitmap(src)
    }

    private fun buildCombinedColorMatrix(): ColorMatrix {
        val cm = ColorMatrix()
        // Contrast
        val c = contrastProgress / 100f // 0..2, 1=normal
        val contrast = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, 0f,
                0f, c, 0f, 0f, 0f,
                0f, 0f, c, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrast)

        // Brightness (translate). Map 0..200 -> -100..+100 -> -255..+255 approx scale
        val bUser = brightnessProgress - 100 // -100..+100
        val b = (bUser * 2.55f)
        val bright = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(bright)

        // Filter preset
        val f = when (currentFilter) {
            Filter.NONE -> null
            Filter.WARM -> ColorMatrix(floatArrayOf(
                1.1f, 0f,   0f,   0f, 10f,
                0f,   1.0f, 0f,   0f, 0f,
                0f,   0f,   0.9f, 0f, -10f,
                0f,   0f,   0f,   1f, 0f
            ))
            Filter.COOL -> ColorMatrix(floatArrayOf(
                0.9f, 0f,   0f,   0f, -10f,
                0f,   1.0f, 0f,   0f, 0f,
                0f,   0f,   1.1f, 0f, 10f,
                0f,   0f,   0f,   1f, 0f
            ))
            Filter.BW -> {
                val bw = ColorMatrix()
                bw.setSaturation(0f)
                bw
            }
            Filter.VIVID -> ColorMatrix(floatArrayOf(
                1.2f, 0f,   0f,   0f, 0f,
                0f,   1.2f, 0f,   0f, 0f,
                0f,   0f,   1.2f, 0f, 0f,
                0f,   0f,   0f,   1f, 0f
            ))
        }
        if (f != null) cm.postConcat(f)
        return cm
    }

    private fun applyMatrixToBitmap(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}