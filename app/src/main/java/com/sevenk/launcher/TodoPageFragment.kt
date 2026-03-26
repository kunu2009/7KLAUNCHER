package com.sevenk.launcher

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
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

        // Do NOT blur the full-screen RecyclerView; it can make the whole home look blurred on some devices.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                // Explicitly clear any blur if previously applied
                binding.todoRecycler.setRenderEffect(null)
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
        val context = requireContext()
        val sheet = BottomSheetDialog(context)

        val root = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, dp(20))
            setBackgroundResource(R.drawable.dialog_background)
        }

        val titleView = android.widget.TextView(context).apply {
            text = if (initialText == null) "Add To-Do" else "Edit To-Do"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(12))
        }
        root.addView(titleView)

        val input = EditText(context).apply {
            hint = "New task"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(initialText)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x99FFFFFF.toInt())
            setBackgroundResource(R.drawable.glass_panel)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    val text = this.text?.toString()?.trim().orEmpty()
                    if (text.isNotEmpty()) onSave(text)
                    sheet.dismiss()
                    true
                } else {
                    false
                }
            }
        }
        val inputLp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
        root.addView(input, inputLp)

        val save = android.widget.TextView(context).apply {
            text = "Save"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.glass_panel)
            foreground = AppCompatResources.getDrawable(context, android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) onSave(text)
                sheet.dismiss()
            }
        }
        val saveLp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        root.addView(save, saveLp)

        val cancel = android.widget.TextView(context).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(0xFF80CBC4.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = android.view.Gravity.CENTER
            setOnClickListener { sheet.dismiss() }
        }
        root.addView(cancel)

        sheet.setContentView(root)
        sheet.setOnShowListener {
            input.requestFocus()
            input.setSelection(input.text?.length ?: 0)
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}