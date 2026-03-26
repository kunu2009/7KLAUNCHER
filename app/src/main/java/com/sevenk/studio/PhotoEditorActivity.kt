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
import android.view.MotionEvent
import android.widget.Button
import java.io.InputStream
import java.io.OutputStream

class PhotoEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoEditorBinding
    private var currentImageUri: Uri? = null
    private var currentBitmap: Bitmap? = null
    private var brightnessProgress: Int = 100 // 0..200, default 100
    private var contrastProgress: Int = 100 // 0..200, default 100
    private var saturationProgress: Int = 100 // 0..200, default 100
    private var currentFilter: Filter = Filter.NONE
    private val undoStack = ArrayDeque<EditorState>()
    private val redoStack = ArrayDeque<EditorState>()
    private val maxHistory = 16
    private var suppressHistory = false

    private enum class Filter { NONE, WARM, COOL, BW, VIVID }

    private data class EditorState(
        val bitmap: Bitmap?,
        val brightness: Int,
        val contrast: Int,
        val saturation: Int,
        val filter: Filter
    )

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                currentImageUri = it
                loadBitmapFromUri(it)?.let { bmp ->
                    currentBitmap = bmp
                    undoStack.clear()
                    redoStack.clear()
                    resetAdjustments()
                    renderPreview()
                    updateHistoryButtons()
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
                    pushUndoState()
                    currentBitmap = bmp
                    resetAdjustments()
                    renderPreview()
                    updateHistoryButtons()
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
        val resetBtn: android.view.View? = try { binding.root.findViewById(R.id.btnReset) } catch (_: Throwable) { null }
        resetBtn?.setOnClickListener {
            pushUndoState()
            resetAdjustments()
            renderPreview()
            updateHistoryButtons()
            Toast.makeText(this, "Adjustments reset", Toast.LENGTH_SHORT).show()
        }

        val undoBtn: android.view.View? = try { binding.root.findViewById(R.id.btnUndo) } catch (_: Throwable) { null }
        undoBtn?.setOnClickListener { undoEdit() }
        val redoBtn: android.view.View? = try { binding.root.findViewById(R.id.btnRedo) } catch (_: Throwable) { null }
        redoBtn?.setOnClickListener { redoEdit() }

        // Hold image to compare with original
        binding.previewImage.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    currentBitmap?.let {
                        binding.previewImage.colorFilter = null
                        binding.previewImage.setImageBitmap(it)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    renderPreview()
                    true
                }
                else -> false
            }
        }

        // Adjustments UI
        configureResponsiveButtonLabels()
        setupAdjustmentsUI()
        setupFilterButtons()
        setupPresetButtons()
        applyTemplateIfProvided()
        updateHistoryButtons()
    }

    private fun configureResponsiveButtonLabels() {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val compact = widthDp < 390f

        fun setBtn(id: Int, normal: String, compactText: String, desc: String) {
            val btn = try { binding.root.findViewById<Button>(id) } catch (_: Throwable) { null } ?: return
            btn.text = if (compact) compactText else normal
            btn.contentDescription = desc
            btn.textSize = if (compact) 11f else 13f
            btn.isAllCaps = false
        }

        setBtn(R.id.btnPick, "Import", "Import", "Import photo from device")
        setBtn(R.id.btnCrop, "Crop", "Crop", "Crop photo")
        setBtn(R.id.btnRotate, "Rotate", "Rotate", "Rotate photo")
        setBtn(R.id.btnFlipH, "Flip H", "FlipH", "Flip photo horizontally")
        setBtn(R.id.btnFlipV, "Flip V", "FlipV", "Flip photo vertically")
        setBtn(R.id.btnEdit, "Edit", "Edit", "Open crop editor")
        setBtn(R.id.btnUndo, "Undo", "Undo", "Undo last edit")
        setBtn(R.id.btnRedo, "Redo", "Redo", "Redo last undone edit")
        setBtn(R.id.btnReset, "Reset", "Reset", "Reset photo adjustments")
        setBtn(R.id.btnSave, "Save", "Save", "Save edited photo")
    }

    private fun applyTemplateIfProvided() {
        val template = intent.getStringExtra("studio_template_name")?.trim()?.lowercase() ?: return
        val targetButtonId = when {
            "cinematic" in template -> R.id.btnPresetCinematic
            "portrait" in template -> R.id.btnPresetPortrait
            "vibrant" in template -> R.id.btnPresetVibrant
            "night" in template -> R.id.btnPresetNight
            else -> null
        } ?: return

        try {
            binding.root.findViewById<Button>(targetButtonId)?.performClick()
            Toast.makeText(this, "Template applied: ${intent.getStringExtra("studio_template_name")}", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {}
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
            pushUndoState()
            val m = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            currentBitmap = rotated
            renderPreview()
            updateHistoryButtons()
        } catch (t: Throwable) {
            Toast.makeText(this, "Rotate failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun flipImage(horizontal: Boolean) {
        val src = currentBitmap ?: return
        try {
            pushUndoState()
            val m = Matrix().apply { postScale(if (horizontal) -1f else 1f, if (horizontal) 1f else -1f, src.width / 2f, src.height / 2f) }
            val flipped = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            currentBitmap = flipped
            renderPreview()
            updateHistoryButtons()
        } catch (t: Throwable) {
            Toast.makeText(this, "Flip failed", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Adjustments & Filters ----
    private fun setupAdjustmentsUI() {
        val seekB: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekBrightness) } catch (_: Throwable) { null }
        val seekC: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekContrast) } catch (_: Throwable) { null }
        val seekS: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekSaturation) } catch (_: Throwable) { null }
        seekB?.progress = brightnessProgress
        seekC?.progress = contrastProgress
        seekS?.progress = saturationProgress
        val listener = object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (suppressHistory) return
                when (sb?.id) {
                    R.id.seekBrightness -> brightnessProgress = progress
                    R.id.seekContrast -> contrastProgress = progress
                    R.id.seekSaturation -> saturationProgress = progress
                }
                renderPreview()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {
                if (!suppressHistory) pushUndoState()
            }
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                updateHistoryButtons()
            }
        }
        seekB?.setOnSeekBarChangeListener(listener)
        seekC?.setOnSeekBarChangeListener(listener)
        seekS?.setOnSeekBarChangeListener(listener)
    }

    private fun setupFilterButtons() {
        fun setFilter(f: Filter) {
            pushUndoState()
            currentFilter = f
            renderPreview()
            updateHistoryButtons()
        }
        try { binding.root.findViewById<android.view.View>(R.id.btnFilterNone)?.setOnClickListener { setFilter(Filter.NONE) } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnFilterWarm)?.setOnClickListener { setFilter(Filter.WARM) } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnFilterCool)?.setOnClickListener { setFilter(Filter.COOL) } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnFilterBW)?.setOnClickListener { setFilter(Filter.BW) } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnFilterVivid)?.setOnClickListener { setFilter(Filter.VIVID) } } catch (_: Throwable) {}
    }

    private fun setupPresetButtons() {
        fun applyPreset(b: Int, c: Int, s: Int, f: Filter, label: String) {
            pushUndoState()
            brightnessProgress = b.coerceIn(0, 200)
            contrastProgress = c.coerceIn(0, 200)
            saturationProgress = s.coerceIn(0, 200)
            currentFilter = f
            val seekB: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekBrightness) } catch (_: Throwable) { null }
            val seekC: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekContrast) } catch (_: Throwable) { null }
            val seekS: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekSaturation) } catch (_: Throwable) { null }
            suppressHistory = true
            seekB?.progress = brightnessProgress
            seekC?.progress = contrastProgress
            seekS?.progress = saturationProgress
            suppressHistory = false
            renderPreview()
            updateHistoryButtons()
            Toast.makeText(this, "$label preset applied", Toast.LENGTH_SHORT).show()
        }

        try { binding.root.findViewById<android.view.View>(R.id.btnPresetCinematic)?.setOnClickListener { applyPreset(90, 125, 115, Filter.COOL, "Cinematic") } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnPresetPortrait)?.setOnClickListener { applyPreset(108, 110, 102, Filter.WARM, "Portrait") } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnPresetVibrant)?.setOnClickListener { applyPreset(112, 125, 140, Filter.VIVID, "Vibrant") } } catch (_: Throwable) {}
        try { binding.root.findViewById<android.view.View>(R.id.btnPresetNight)?.setOnClickListener { applyPreset(82, 112, 92, Filter.BW, "Night") } } catch (_: Throwable) {}
    }

    private fun resetAdjustments() {
        brightnessProgress = 100
        contrastProgress = 100
        saturationProgress = 100
        currentFilter = Filter.NONE
        val seekB: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekBrightness) } catch (_: Throwable) { null }
        val seekC: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekContrast) } catch (_: Throwable) { null }
        val seekS: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekSaturation) } catch (_: Throwable) { null }
        suppressHistory = true
        seekB?.progress = brightnessProgress
        seekC?.progress = contrastProgress
        seekS?.progress = saturationProgress
        suppressHistory = false
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

        // Saturation (1f = normal). Map 0..200 -> 0..2
        val s = saturationProgress / 100f
        val satM = ColorMatrix()
        satM.setSaturation(s)
        cm.postConcat(satM)

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
        val out = Bitmap.createBitmap(src.width, src.height, src.config)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun pushUndoState() {
        if (suppressHistory) return
        undoStack.addLast(snapshotState())
        while (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun snapshotState(): EditorState {
        val bmpCopy = currentBitmap?.let { copyBitmap(it) }
        return EditorState(
            bitmap = bmpCopy,
            brightness = brightnessProgress,
            contrast = contrastProgress,
            saturation = saturationProgress,
            filter = currentFilter
        )
    }

    private fun copyBitmap(src: Bitmap): Bitmap {
        return src.copy(src.config, true)
    }

    private fun applyState(state: EditorState) {
        currentBitmap = state.bitmap?.let { copyBitmap(it) }
        brightnessProgress = state.brightness
        contrastProgress = state.contrast
        saturationProgress = state.saturation
        currentFilter = state.filter
        val seekB: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekBrightness) } catch (_: Throwable) { null }
        val seekC: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekContrast) } catch (_: Throwable) { null }
        val seekS: android.widget.SeekBar? = try { binding.root.findViewById(R.id.seekSaturation) } catch (_: Throwable) { null }
        suppressHistory = true
        seekB?.progress = brightnessProgress
        seekC?.progress = contrastProgress
        seekS?.progress = saturationProgress
        suppressHistory = false
        renderPreview()
    }

    private fun undoEdit() {
        if (undoStack.isEmpty()) return
        val current = snapshotState()
        val prev = undoStack.removeLast()
        redoStack.addLast(current)
        applyState(prev)
        updateHistoryButtons()
    }

    private fun redoEdit() {
        if (redoStack.isEmpty()) return
        val current = snapshotState()
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        applyState(next)
        updateHistoryButtons()
    }

    private fun updateHistoryButtons() {
        val undoBtn: android.view.View? = try { binding.root.findViewById(R.id.btnUndo) } catch (_: Throwable) { null }
        val redoBtn: android.view.View? = try { binding.root.findViewById(R.id.btnRedo) } catch (_: Throwable) { null }
        undoBtn?.isEnabled = undoStack.isNotEmpty()
        redoBtn?.isEnabled = redoStack.isNotEmpty()
        undoBtn?.alpha = if (undoStack.isNotEmpty()) 1f else 0.5f
        redoBtn?.alpha = if (redoStack.isNotEmpty()) 1f else 0.5f
    }
}