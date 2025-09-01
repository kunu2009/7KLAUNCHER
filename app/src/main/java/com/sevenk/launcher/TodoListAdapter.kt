package com.sevenk.launcher

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sevenk.launcher.data.TodoItem
import com.sevenk.launcher.databinding.WidgetTodoItemBinding

class TodoListAdapter(
    private val onToggle: (TodoItem) -> Unit,
    private val onEdit: (TodoItem) -> Unit,
    private val onDelete: (TodoItem) -> Unit
) : ListAdapter<TodoItem, TodoListAdapter.TodoViewHolder>(TodoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val binding = WidgetTodoItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TodoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onToggle, onEdit, onDelete)
    }

    class TodoViewHolder(private val binding: WidgetTodoItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TodoItem, onToggle: (TodoItem) -> Unit, onEdit: (TodoItem) -> Unit, onDelete: (TodoItem) -> Unit) {
            binding.todoText.text = item.task
            binding.todoCheck.isChecked = item.isCompleted

            if (item.isCompleted) {
                binding.todoText.paintFlags = binding.todoText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.todoText.paintFlags = binding.todoText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            binding.todoCheck.setOnClickListener { onToggle(item) }
            binding.root.setOnClickListener { onEdit(item) }
            binding.deleteButton.setOnClickListener { onDelete(item) }
        }
    }
}

class TodoDiffCallback : DiffUtil.ItemCallback<TodoItem>() {
    override fun areItemsTheSame(oldItem: TodoItem, newItem: TodoItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: TodoItem, newItem: TodoItem): Boolean {
        return oldItem == newItem
    }
}