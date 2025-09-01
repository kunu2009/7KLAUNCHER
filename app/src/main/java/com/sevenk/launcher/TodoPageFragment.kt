package com.sevenk.launcher

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.sevenk.launcher.data.TodoDatabase
import com.sevenk.launcher.data.TodoItem
import com.sevenk.launcher.data.TodoViewModel
import com.sevenk.launcher.data.TodoViewModelFactory
import com.sevenk.launcher.databinding.FragmentTodoPageBinding

class TodoPageFragment : Fragment() {

    private var _binding: FragmentTodoPageBinding? = null
    private val binding get() = _binding!!

    private val todoViewModel: TodoViewModel by viewModels {
        TodoViewModelFactory(TodoDatabase.getDatabase(requireContext()).todoDao())
    }

    private lateinit var adapter: TodoListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTodoPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        binding.addTodo.setOnClickListener { promptAddItem() }

        todoViewModel.allTodos.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }

        // Apply subtle runtime blur to reinforce translucent glass on Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val effect = android.graphics.RenderEffect.createBlurEffect(12f, 12f, android.graphics.Shader.TileMode.CLAMP)
                binding.todoRecycler.setRenderEffect(effect)
            } catch (_: Throwable) { }
        }
    }

    private fun setupRecyclerView() {
        adapter = TodoListAdapter(
            onToggle = { item ->
                val updatedItem = item.copy(isCompleted = !item.isCompleted)
                todoViewModel.update(updatedItem)
            },
            onEdit = { item -> promptEditItem(item) },
            onDelete = { item -> todoViewModel.delete(item) }
        )
        binding.todoRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.todoRecycler.adapter = adapter
    }

    private fun promptAddItem() {
        showTodoDialog(null) { text ->
            todoViewModel.insert(TodoItem(task = text))
        }
    }

    private fun promptEditItem(item: TodoItem) {
        showTodoDialog(item.task) { text ->
            val updatedItem = item.copy(task = text)
            todoViewModel.update(updatedItem)
        }
    }

    private fun showTodoDialog(initialText: String?, onSave: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = "New task"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(initialText)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (initialText == null) "Add To-Do" else "Edit To-Do")
            .setView(input)
            .setPositiveButton("Save") { d, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    onSave(text)
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}