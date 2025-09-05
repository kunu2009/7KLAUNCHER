package com.sevenk.launcher.notes.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class NotesRepository private constructor(private val appContext: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val storageFile: File by lazy { File(appContext.filesDir, "notes.json") }

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    init {
        load()
    }

    fun refresh() { load() }

    fun upsert(note: Note) {
        val current = _notes.value.toMutableList()
        val idx = current.indexOfFirst { it.id == note.id }
        if (idx >= 0) current[idx] = note.copy(updatedAt = System.currentTimeMillis())
        else current.add(note.copy(updatedAt = System.currentTimeMillis()))
        saveList(current)
    }

    fun createNew(title: String = "", content: String = "", color: String = "#FFFFFF"): Note {
        val n = Note(id = UUID.randomUUID().toString(), title = title, content = content, color = color)
        upsert(n)
        return n
    }

    fun delete(id: String) {
        val current = _notes.value.filterNot { it.id == id }
        saveList(current)
    }

    fun togglePin(id: String) {
        val current = _notes.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val n = current[idx]
            current[idx] = n.copy(pinned = !n.pinned, updatedAt = System.currentTimeMillis())
            saveList(current)
        }
    }

    fun getById(id: String): Note? = _notes.value.firstOrNull { it.id == id }

    private fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = runCatching {
                if (storageFile.exists()) {
                    val text = storageFile.readText()
                    json.decodeFromString(ListSerializer(Note.serializer()), text)
                } else emptyList()
            }.getOrElse { emptyList() }
            val sorted = list.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
            _notes.emit(sorted)
        }
    }

    private fun saveList(list: List<Note>) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (storageFile.parentFile?.exists() != true) storageFile.parentFile?.mkdirs()
                val text = json.encodeToString(ListSerializer(Note.serializer()), list)
                storageFile.writeText(text)
            }
            val sorted = list.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
            _notes.emit(sorted)
        }
    }

    companion object {
        @Volatile private var INSTANCE: NotesRepository? = null
        fun get(context: Context): NotesRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: NotesRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
