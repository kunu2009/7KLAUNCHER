package com.sevenk.launcher.notes.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sevenk.launcher.R
import com.sevenk.launcher.databinding.ActivityNoteEditorBinding
import com.sevenk.launcher.notes.data.Note
import com.sevenk.launcher.notes.data.NotesRepository

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private lateinit var repo: NotesRepository
    private var note: Note? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repo = NotesRepository.get(this)
        val id = intent.getStringExtra(EXTRA_NOTE_ID)
        note = id?.let { repo.getById(it) }
        if (note == null) finish()

        binding.editTitle.setText(note?.title ?: "")
        binding.editContent.setText(note?.content ?: "")
        applyCardColor(note?.color ?: "#FFFFFF")

        binding.btnColor.setOnClickListener {
            showColorPicker()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_note_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { saveAndFinish(); true }
            R.id.action_save -> { saveAndFinish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun applyCardColor(color: String) {
        try { binding.card.setCardBackgroundColor(android.graphics.Color.parseColor(color)) } catch (_: Throwable) {}
    }

    private fun showColorPicker() {
        val colorHex = arrayOf("#FFFFFF", "#FFFDE7", "#E3F2FD", "#E8F5E9", "#FCE4EC", "#EDE7F6")
        val colorNames = arrayOf(
            getString(R.string.color_white),
            getString(R.string.color_light_yellow),
            getString(R.string.color_light_blue),
            getString(R.string.color_light_green),
            getString(R.string.color_light_pink),
            getString(R.string.color_light_purple)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pick_color)
            .setItems(colorNames) { _, which ->
                val hex = colorHex[which]
                note = note?.copy(color = hex)
                applyCardColor(hex)
            }
            .show()
    }

    private fun saveAndFinish() {
        val n = note ?: return
        val updated = n.copy(
            title = binding.editTitle.text?.toString() ?: "",
            content = binding.editContent.text?.toString() ?: ""
        )
        repo.upsert(updated)
        finish()
    }

    companion object {
        private const val EXTRA_NOTE_ID = "extra_note_id"
        fun start(context: Context, id: String) {
            val i = Intent(context, NoteEditorActivity::class.java)
            i.putExtra(EXTRA_NOTE_ID, id)
            context.startActivity(i)
        }
    }
}
