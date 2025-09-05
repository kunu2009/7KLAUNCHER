package com.sevenk.launcher.notes.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.color.DynamicColors
import com.sevenk.launcher.R
import com.sevenk.launcher.databinding.ActivityNotesBinding
import com.sevenk.launcher.notes.data.Note
import com.sevenk.launcher.notes.data.NotesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotesActivity : AppCompatActivity(), NotesListAdapter.NoteClickListener {

    private lateinit var binding: ActivityNotesBinding
    private lateinit var repo: NotesRepository
    private lateinit var adapter: NotesListAdapter

    private var searchJob: Job? = null
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.title = getString(R.string.notes_title)

        repo = NotesRepository.get(this)
        adapter = NotesListAdapter(this)
        binding.recycler.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.notes_grid_columns))
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener {
            val note = repo.createNew()
            NoteEditorActivity.start(this, note.id)
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim() ?: ""
                applyFilter()
            }
        })

        lifecycleScope.launch {
            repo.notes.collectLatest { list ->
                binding.emptyView.isVisible = list.isEmpty()
                applyFilter(list)
            }
        }
    }

    private fun applyFilter(source: List<Note>? = null) {
        val list = source ?: adapter.current
        val filtered = if (query.isBlank()) list else list.filter { it.matchesQuery(query) }
        adapter.submitList(filtered)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_notes, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> { repo.refresh(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNoteClick(note: Note) {
        NoteEditorActivity.start(this, note.id)
    }

    override fun onNotePinToggle(note: Note) {
        repo.togglePin(note.id)
    }

    override fun onNoteDelete(note: Note) {
        repo.delete(note.id)
    }
}
