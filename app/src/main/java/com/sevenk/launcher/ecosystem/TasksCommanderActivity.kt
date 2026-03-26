package com.sevenk.launcher.ecosystem

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.text.InputType
import kotlin.math.abs

class TasksCommanderActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("sevenk_tasks_commander", MODE_PRIVATE) }
    private val tasks = mutableListOf<TaskEntry>()
    private lateinit var taskListContainer: LinearLayout
    private lateinit var statsText: TextView
    private var activeFilter: Filter = Filter.ALL

    private data class TaskEntry(
        val id: Long,
        val title: String,
        val priority: Int,
        val urgent: Boolean,
        val dueAt: Long,
        val recurrenceDays: Int,
        val done: Boolean,
        val createdAt: Long
    )

    private enum class Filter { ALL, OPEN, DONE, Q1, Q2, Q3, Q4 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Tasks Commander"

        loadTasks()

        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF171717.toInt())
        }

        val heading = TextView(this).apply {
            text = "7K Tasks Commander"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        }

        val sub = TextView(this).apply {
            text = "Offline command board for your daily missions"
            textSize = 13f
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(0, 8, 0, 16)
        }

        statsText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF80CBC4.toInt())
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF202020.toInt())
        }

        val addBtn = Button(this).apply {
            text = "+ Add Task"
            isAllCaps = false
            setOnClickListener { showAddTaskDialog() }
        }

        val clearBtn = Button(this).apply {
            text = "Clear Done"
            isAllCaps = false
            setOnClickListener {
                if (tasks.isNotEmpty()) {
                    tasks.removeAll { it.done }
                    saveTasks()
                    renderTasks()
                }
            }
        }

        val allBtn = Button(this).apply {
            text = "All"
            isAllCaps = false
            setOnClickListener { activeFilter = Filter.ALL; renderTasks() }
        }
        val openBtn = Button(this).apply {
            text = "Open"
            isAllCaps = false
            setOnClickListener { activeFilter = Filter.OPEN; renderTasks() }
        }
        val doneBtn = Button(this).apply {
            text = "Done"
            isAllCaps = false
            setOnClickListener { activeFilter = Filter.DONE; renderTasks() }
        }

        val q1Btn = Button(this).apply {
            text = "Q1"
            isAllCaps = false
            setOnClickListener { activeFilter = Filter.Q1; renderTasks() }
        }
        val q2Btn = Button(this).apply {
            text = "Q2"
            isAllCaps = false
            setOnClickListener { activeFilter = Filter.Q2; renderTasks() }
        }
        val q3Btn = Button(this).apply {
            text = "Q3"
            isAllCaps = false
            setOnClickListener { activeFilter = Filter.Q3; renderTasks() }
        }
        val q4Btn = Button(this).apply {
            text = "Q4"
            isAllCaps = false
            setOnClickListener { activeFilter = Filter.Q4; renderTasks() }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buttons.addView(addBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(clearBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 0)
        }
        filters.addView(allBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        filters.addView(openBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        filters.addView(doneBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val matrixFilters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 0)
        }
        matrixFilters.addView(q1Btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        matrixFilters.addView(q2Btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        matrixFilters.addView(q3Btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        matrixFilters.addView(q4Btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val hint = TextView(this).apply {
            text = "Matrix: Q1 urgent+important • Q2 important • Q3 urgent • Q4 later"
            textSize = 11f
            setTextColor(0xFF90A4AE.toInt())
            setPadding(0, 8, 0, 0)
        }

        taskListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 18, 0, 0)
        }

        content.addView(heading)
        content.addView(sub)
        content.addView(statsText)
        content.addView(buttons)
        content.addView(filters)
        content.addView(matrixFilters)
        content.addView(hint)
        content.addView(taskListContainer)

        root.addView(content)
        setContentView(root)

        renderTasks()
    }

    private fun showAddTaskDialog() {
        val input = EditText(this).apply {
            hint = "Task title"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val priorityInput = EditText(this).apply {
            hint = "Priority (1 high, 2 normal, 3 low)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val urgentInput = EditText(this).apply {
            hint = "Urgent? (y/n, default n)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val dueDaysInput = EditText(this).apply {
            hint = "Due in days (0 none)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val recurrenceInput = EditText(this).apply {
            hint = "Repeat every N days (0 none)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
            addView(priorityInput)
            addView(urgentInput)
            addView(dueDaysInput)
            addView(recurrenceInput)
        }
        AlertDialog.Builder(this)
            .setTitle("New Commander Task")
            .setView(holder)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text?.toString()?.trim().orEmpty()
                if (value.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    val p = priorityInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 3) ?: 2
                    val urgent = urgentInput.text?.toString()?.trim()?.lowercase()?.startsWith("y") == true
                    val dueDays = dueDaysInput.text?.toString()?.toIntOrNull()?.coerceIn(0, 3650) ?: 0
                    val recurrenceDays = recurrenceInput.text?.toString()?.toIntOrNull()?.coerceIn(0, 3650) ?: 0
                    val dueAt = if (dueDays > 0) now + (dueDays * 86_400_000L) else 0L
                    tasks.add(0, TaskEntry(now, value, p, urgent, dueAt, recurrenceDays, false, now))
                    saveTasks()
                    renderTasks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderTasks() {
        taskListContainer.removeAllViews()
        val filtered = when (activeFilter) {
            Filter.ALL -> tasks
            Filter.OPEN -> tasks.filter { !it.done }
            Filter.DONE -> tasks.filter { it.done }
            Filter.Q1 -> tasks.filter { !it.done && quadrantOf(it) == "Q1" }
            Filter.Q2 -> tasks.filter { !it.done && quadrantOf(it) == "Q2" }
            Filter.Q3 -> tasks.filter { !it.done && quadrantOf(it) == "Q3" }
            Filter.Q4 -> tasks.filter { !it.done && quadrantOf(it) == "Q4" }
        }.sortedWith(compareBy<TaskEntry> { it.done }.thenBy { it.priority }.thenByDescending { it.createdAt })

        val openCount = tasks.count { !it.done }
        val doneCount = tasks.count { it.done }
        val q1 = tasks.count { !it.done && quadrantOf(it) == "Q1" }
        val q2 = tasks.count { !it.done && quadrantOf(it) == "Q2" }
        val q3 = tasks.count { !it.done && quadrantOf(it) == "Q3" }
        val q4 = tasks.count { !it.done && quadrantOf(it) == "Q4" }
        val recurring = tasks.count { !it.done && it.recurrenceDays > 0 }
        statsText.text = "Total ${tasks.size} • Open $openCount • Done $doneCount • Q1:$q1 Q2:$q2 Q3:$q3 Q4:$q4 • Recur:$recurring • Filter ${activeFilter.name}"

        if (filtered.isEmpty()) {
            taskListContainer.addView(TextView(this).apply {
                text = "No missions for this filter."
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 14f
            })
            return
        }

        filtered.forEachIndexed { idx, task ->
            val q = quadrantOf(task)
            val dueText = dueLabel(task)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(if (task.done) 0xFF1F3A2A.toInt() else 0xFF242424.toInt())
            }

            val tv = TextView(this).apply {
                val pText = when (task.priority) { 1 -> "[H]"; 3 -> "[L]"; else -> "[N]" }
                val urgentText = if (task.urgent) "[U]" else ""
                val recurText = if (task.recurrenceDays > 0) " • repeats ${task.recurrenceDays}d" else ""
                text = "${idx + 1}. $pText$urgentText [$q] ${task.title}$dueText$recurText"
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val doneBtn = Button(this).apply {
                text = if (task.done) "Undo" else "Done"
                isAllCaps = false
                setOnClickListener {
                    val index = tasks.indexOfFirst { it.id == task.id }
                    if (index >= 0) {
                        val old = tasks[index]
                        val now = System.currentTimeMillis()
                        val goingDone = !old.done
                        tasks[index] = old.copy(done = goingDone)
                        if (goingDone && old.recurrenceDays > 0) {
                            val nextDueBase = if (old.dueAt > now) old.dueAt else now
                            val nextDue = nextDueBase + (old.recurrenceDays * 86_400_000L)
                            tasks.add(0, old.copy(
                                id = now + index,
                                done = false,
                                dueAt = nextDue,
                                createdAt = now
                            ))
                        }
                    }
                    saveTasks()
                    renderTasks()
                }
            }

            val deleteBtn = Button(this).apply {
                text = "Delete"
                isAllCaps = false
                setOnClickListener {
                    tasks.removeAll { it.id == task.id }
                    saveTasks()
                    renderTasks()
                }
            }

            val dupBtn = Button(this).apply {
                text = "Duplicate"
                isAllCaps = false
                setOnClickListener {
                    val now = System.currentTimeMillis()
                    tasks.add(0, task.copy(id = now, done = false, createdAt = now))
                    saveTasks()
                    renderTasks()
                }
            }

            val topBtn = Button(this).apply {
                text = "Top"
                isAllCaps = false
                setOnClickListener {
                    val index = tasks.indexOfFirst { it.id == task.id }
                    if (index > 0) {
                        val item = tasks.removeAt(index)
                        tasks.add(0, item)
                        saveTasks()
                        renderTasks()
                    }
                }
            }

            row.addView(tv)
            row.addView(topBtn)
            row.addView(dupBtn)
            row.addView(doneBtn)
            row.addView(deleteBtn)

            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 10
            taskListContainer.addView(row, lp)
        }
    }

    private fun quadrantOf(task: TaskEntry): String {
        val important = task.priority == 1
        val urgent = task.urgent
        return when {
            urgent && important -> "Q1"
            !urgent && important -> "Q2"
            urgent && !important -> "Q3"
            else -> "Q4"
        }
    }

    private fun dueLabel(task: TaskEntry): String {
        if (task.dueAt <= 0L) return ""
        val diff = task.dueAt - System.currentTimeMillis()
        val days = abs(diff) / 86_400_000L
        return if (diff >= 0) " • due ${days}d" else " • overdue ${days}d"
    }

    private fun loadTasks() {
        val raw = prefs.getString("tasks", "") ?: ""
        tasks.clear()
        if (raw.isBlank()) return
        raw.split("||").mapNotNull { token ->
            val p = token.split("\t")
            if (p.size < 5) return@mapNotNull null
            val id = p[0].toLongOrNull() ?: return@mapNotNull null
            val title = p[1]
            val priority = p[2].toIntOrNull()?.coerceIn(1, 3) ?: 2
            val done = p[3] == "1"
            val createdAt = p[4].toLongOrNull() ?: id
            val urgent = p.getOrNull(5) == "1"
            val dueAt = p.getOrNull(6)?.toLongOrNull() ?: 0L
            val recurrenceDays = p.getOrNull(7)?.toIntOrNull()?.coerceIn(0, 3650) ?: 0
            TaskEntry(id, title, priority, urgent, dueAt, recurrenceDays, done, createdAt)
        }.let { tasks.addAll(it) }
    }

    private fun saveTasks() {
        val raw = tasks.joinToString("||") {
            listOf(
                it.id.toString(),
                it.title.replace("\t", " "),
                it.priority.toString(),
                if (it.done) "1" else "0",
                it.createdAt.toString(),
                if (it.urgent) "1" else "0",
                it.dueAt.toString(),
                it.recurrenceDays.toString()
            ).joinToString("\t")
        }
        prefs.edit().putString("tasks", raw).apply()
    }
}
