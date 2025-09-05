package com.sevenk.launcher.notes.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.databinding.ItemNoteBinding
import com.sevenk.launcher.notes.data.Note

class NotesListAdapter(private val listener: NoteClickListener) :
    ListAdapter<Note, NotesListAdapter.VH>(DIFF) {

    val current: List<Note> get() = currentList

    interface NoteClickListener {
        fun onNoteClick(note: Note)
        fun onNotePinToggle(note: Note)
        fun onNoteDelete(note: Note)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val note = getItem(bindingAdapterPosition)
                listener.onNoteClick(note)
            }
            binding.btnPin.setOnClickListener {
                val note = getItem(bindingAdapterPosition)
                listener.onNotePinToggle(note)
            }
            binding.btnDelete.setOnClickListener {
                val note = getItem(bindingAdapterPosition)
                listener.onNoteDelete(note)
            }
        }
        fun bind(n: Note) {
            binding.title.text = if (n.title.isBlank()) "Untitled" else n.title
            binding.content.text = n.content.trim()
            binding.pinnedLabel.visibility = if (n.pinned) View.VISIBLE else View.GONE
            try { binding.card.setCardBackgroundColor(android.graphics.Color.parseColor(n.color)) } catch (_: Throwable) {}
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean = oldItem == newItem
        }
    }
}
