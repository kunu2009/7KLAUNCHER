package com.sevenk.launcher.ecosystem

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class TasksCommanderActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("sevenk_tasks_commander", MODE_PRIVATE) }
    private val tasks = mutableListOf<TaskEntry>()

    private lateinit var performanceText: TextView
    private lateinit var progressRingText: TextView
    private lateinit var scheduleGrid: GridLayout
    private lateinit var timelineContainer: LinearLayout

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Tasks Commander"

        loadTasks()
        if (savedInstanceState == null) {
            consumeIncomingTaskIntent()
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF1D1552.toInt())
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 140)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleText = TextView(this).apply {
            text = "Let's improve\nour performance."
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val bellBtn = Button(this).apply {
            text = "🔔"
            isAllCaps = false
            setBackgroundColor(0xFF2A235F.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        topRow.addView(titleText)
        topRow.addView(bellBtn)

        val performanceCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFF161326.toInt())
        }
        val perfLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        performanceText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFD1C4E9.toInt())
            text = "Great, you're nearly done with today's tasks."
        }
        val viewTasksBtn = Button(this).apply {
            text = "View Tasks"
            isAllCaps = false
            setBackgroundColor(0xFF6C4BFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { showTaskListDialog() }
        }
        perfLeft.addView(performanceText)
        perfLeft.addView(viewTasksBtn)

        progressRingText = TextView(this).apply {
            text = "0%"
            textSize = 22f
            setTextColor(0xFFB39DDB.toInt())
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFF231D44.toInt())
        }
        performanceCard.addView(perfLeft)
        performanceCard.addView(progressRingText)

        val scheduleTitle = TextView(this).apply {
            text = "Today's Schedule"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 20, 0, 10)
        }

        scheduleGrid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
        }

        val reminderBtn = Button(this).apply {
            text = "Set Reminder"
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF6C4BFF.toInt())
            setOnClickListener { showQuickReminderDialog() }
        }

        val timelineTitle = TextView(this).apply {
            text = "Timeline"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 18, 0, 8)
        }

        timelineContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(topRow)
        content.addView(performanceCard)
        content.addView(scheduleTitle)
        content.addView(scheduleGrid)
        content.addView(reminderBtn)
        content.addView(timelineTitle)
        content.addView(timelineContainer)

        scroll.addView(content)
        root.addView(scroll)

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF151221.toInt())
            setPadding(12, 12, 12, 12)
        }
        fun nav(label: String): Button = Button(this).apply {
            text = label
            isAllCaps = false
            setBackgroundColor(0x00000000)
            setTextColor(0xFFBDB3D9.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        bottomBar.addView(nav("◻"))
        bottomBar.addView(nav("📅"))
        bottomBar.addView(nav("📋"))
        bottomBar.addView(nav("👤"))

        val bottomLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }
        root.addView(bottomBar, bottomLp)

        val addFab = Button(this).apply {
            text = "+"
            textSize = 28f
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2E294D.toInt())
            setOnClickListener { showAddTaskDialog() }
        }
        val fabLp = FrameLayout.LayoutParams(140, 140).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 34
        }
        root.addView(addFab, fabLp)

        setContentView(root)
        renderDashboard()
    }

    private fun consumeIncomingTaskIntent() {
        val titleFromIntent = intent?.getStringExtra("prefill_task_title")?.trim().orEmpty()
        val urlFromIntent = intent?.getStringExtra("prefill_task_url")?.trim().orEmpty()
        if (titleFromIntent.isBlank()) return

        val now = System.currentTimeMillis()
        val compactTitle = if (urlFromIntent.isNotBlank()) {
            val host = runCatching { android.net.Uri.parse(urlFromIntent).host.orEmpty() }.getOrDefault("")
            if (host.isNotBlank()) "$titleFromIntent • $host" else titleFromIntent
        } else {
            titleFromIntent
        }

        tasks.add(
            0,
            TaskEntry(
                id = now,
                title = compactTitle,
                priority = 2,
                urgent = false,
                dueAt = 0L,
                recurrenceDays = 0,
                done = false,
                createdAt = now
            )
        )
        saveTasks()
        Toast.makeText(this, "Task added from 7K Browser", Toast.LENGTH_SHORT).show()
    }

    private fun renderDashboard() {
        val total = tasks.size
        val done = tasks.count { it.done }
        val open = tasks.filter { !it.done }
        val percent = if (total == 0) 0 else ((done * 100f) / total).toInt()
        progressRingText.text = "$percent%"
        performanceText.text = "Open $${open.size} missions • Completed $done of $total"

        renderScheduleCards(open)
        renderTimeline(open)
    }

    private fun renderScheduleCards(open: List<TaskEntry>) {
        scheduleGrid.removeAllViews()
        val palette = intArrayOf(
            0xFF6C4BFF.toInt(),
            0xFF1FBF8F.toInt(),
            0xFF2F9DF4.toInt(),
            0xFF7A53D8.toInt()
        )
        val top = open.sortedWith(compareBy<TaskEntry> { it.priority }.thenBy { it.dueAt.takeIf { d -> d > 0 } ?: Long.MAX_VALUE }).take(4)

        if (top.isEmpty()) {
            scheduleGrid.addView(TextView(this).apply {
                text = "No tasks yet. Tap + to add your first mission."
                setTextColor(0xFFD1C4E9.toInt())
                textSize = 14f
                setPadding(10, 10, 10, 10)
            })
            return
        }

        top.forEachIndexed { index, task ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 18, 18, 18)
                setBackgroundColor(palette[index % palette.size])
                setOnClickListener { showTaskActionDialog(task) }
            }
            val name = TextView(this).apply {
                text = task.title
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
            }
            val meta = TextView(this).apply {
                text = taskMeta(task)
                setTextColor(0xFFEDE7F6.toInt())
                textSize = 12f
                setPadding(0, 8, 0, 0)
            }
            card.addView(name)
            card.addView(meta)

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(index % 2, 1f)
                rowSpec = GridLayout.spec(index / 2)
                setMargins(0, 0, 10, 10)
            }
            scheduleGrid.addView(card, lp)
        }
    }

    private fun renderTimeline(open: List<TaskEntry>) {
        timelineContainer.removeAllViews()
        val sorted = open.sortedWith(compareBy<TaskEntry> { it.dueAt.takeIf { d -> d > 0 } ?: Long.MAX_VALUE }.thenBy { it.priority }).take(6)

        if (sorted.isEmpty()) {
            timelineContainer.addView(TextView(this).apply {
                text = "Timeline is clear. Enjoy the calm."
                setTextColor(0xFFD1C4E9.toInt())
                textSize = 13f
            })
            return
        }

        sorted.forEachIndexed { idx, task ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val hour = TextView(this).apply {
                val slot = 9 + idx
                text = String.format("%02d:00", slot)
                setTextColor(0xFFBDB3D9.toInt())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(0xFF1B1730.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { showTaskActionDialog(task) }
            }
            card.addView(TextView(this).apply {
                text = task.title
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
            })
            card.addView(TextView(this).apply {
                text = taskMeta(task)
                setTextColor(0xFFBDB3D9.toInt())
                textSize = 11f
            })
            row.addView(hour)
            row.addView(card)
            timelineContainer.addView(row)
        }
    }

    private fun taskMeta(task: TaskEntry): String {
        val priority = when (task.priority) { 1 -> "High"; 3 -> "Low"; else -> "Normal" }
        val urgent = if (task.urgent) " • Urgent" else ""
        val due = if (task.dueAt > 0) {
            val diff = task.dueAt - System.currentTimeMillis()
            val days = abs(diff) / 86_400_000L
            if (diff >= 0) " • due ${days}d" else " • overdue ${days}d"
        } else ""
        val recur = if (task.recurrenceDays > 0) " • every ${task.recurrenceDays}d" else ""
        return "$priority$urgent$due$recur"
    }

    private fun showQuickReminderDialog() {
        val titleInput = EditText(this).apply {
            hint = "Reminder title"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val dueInput = EditText(this).apply {
            hint = "Due in days (1 default)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleInput)
            addView(dueInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Set Reminder")
            .setView(holder)
            .setPositiveButton("Save") { _, _ ->
                val title = titleInput.text?.toString()?.trim().orEmpty()
                if (title.isBlank()) return@setPositiveButton
                val days = dueInput.text?.toString()?.toIntOrNull()?.coerceIn(0, 3650) ?: 1
                val now = System.currentTimeMillis()
                tasks.add(0, TaskEntry(
                    id = now,
                    title = title,
                    priority = 1,
                    urgent = true,
                    dueAt = now + days * 86_400_000L,
                    recurrenceDays = 0,
                    done = false,
                    createdAt = now
                ))
                saveTasks()
                renderDashboard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddTaskDialog(existing: TaskEntry? = null) {
        val input = EditText(this).apply {
            hint = "Task title"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(existing?.title.orEmpty())
        }
        val priorityInput = EditText(this).apply {
            hint = "Priority (1 high, 2 normal, 3 low)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existing?.priority?.toString() ?: "2")
        }
        val urgentInput = EditText(this).apply {
            hint = "Urgent? (y/n)"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(if (existing?.urgent == true) "y" else "n")
        }
        val dueDaysInput = EditText(this).apply {
            hint = "Due in days (0 none)"
            inputType = InputType.TYPE_CLASS_NUMBER
            val days = if (existing != null && existing.dueAt > 0) {
                ((existing.dueAt - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0)
            } else 0
            setText(days.toString())
        }
        val recurrenceInput = EditText(this).apply {
            hint = "Repeat every N days (0 none)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existing?.recurrenceDays?.toString() ?: "0")
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
            .setTitle(if (existing == null) "New Task" else "Edit Task")
            .setView(holder)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text?.toString()?.trim().orEmpty()
                if (value.isBlank()) return@setPositiveButton
                val now = System.currentTimeMillis()
                val p = priorityInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 3) ?: 2
                val urgent = urgentInput.text?.toString()?.trim()?.lowercase()?.startsWith("y") == true
                val dueDays = dueDaysInput.text?.toString()?.toIntOrNull()?.coerceIn(0, 3650) ?: 0
                val recurrenceDays = recurrenceInput.text?.toString()?.toIntOrNull()?.coerceIn(0, 3650) ?: 0
                val dueAt = if (dueDays > 0) now + dueDays * 86_400_000L else 0L

                if (existing == null) {
                    tasks.add(0, TaskEntry(now, value, p, urgent, dueAt, recurrenceDays, false, now))
                } else {
                    val idx = tasks.indexOfFirst { it.id == existing.id }
                    if (idx >= 0) {
                        tasks[idx] = existing.copy(
                            title = value,
                            priority = p,
                            urgent = urgent,
                            dueAt = dueAt,
                            recurrenceDays = recurrenceDays
                        )
                    }
                }
                saveTasks()
                renderDashboard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTaskActionDialog(task: TaskEntry) {
        val actions = mutableListOf<String>()
        actions.add(if (task.done) "Undo" else "Mark Done")
        actions.add("Edit")
        actions.add("Duplicate")
        actions.add("Delete")

        AlertDialog.Builder(this)
            .setTitle(task.title)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "Mark Done", "Undo" -> toggleDone(task)
                    "Edit" -> showAddTaskDialog(task)
                    "Duplicate" -> {
                        val now = System.currentTimeMillis()
                        tasks.add(0, task.copy(id = now, done = false, createdAt = now))
                        saveTasks()
                        renderDashboard()
                    }
                    "Delete" -> {
                        tasks.removeAll { it.id == task.id }
                        saveTasks()
                        renderDashboard()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleDone(task: TaskEntry) {
        val idx = tasks.indexOfFirst { it.id == task.id }
        if (idx < 0) return
        val old = tasks[idx]
        val now = System.currentTimeMillis()
        val goingDone = !old.done
        tasks[idx] = old.copy(done = goingDone)

        if (goingDone && old.recurrenceDays > 0) {
            val nextDueBase = if (old.dueAt > now) old.dueAt else now
            val nextDue = nextDueBase + old.recurrenceDays * 86_400_000L
            tasks.add(0, old.copy(
                id = now + idx,
                done = false,
                dueAt = nextDue,
                createdAt = now
            ))
        }

        saveTasks()
        renderDashboard()
    }

    private fun showTaskListDialog() {
        val open = tasks.filter { !it.done }
            .sortedWith(compareBy<TaskEntry> { it.priority }.thenBy { it.dueAt.takeIf { d -> d > 0 } ?: Long.MAX_VALUE })

        if (open.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Open Tasks")
                .setMessage("No open tasks. Nice work.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val labels = open.map { t ->
            val pr = when (t.priority) { 1 -> "[H]"; 3 -> "[L]"; else -> "[N]" }
            "$pr ${t.title}${if (t.urgent) " • urgent" else ""}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Open Tasks")
            .setItems(labels) { _, which -> showTaskActionDialog(open[which]) }
            .setPositiveButton("New Task") { _, _ -> showAddTaskDialog() }
            .setNegativeButton("Close", null)
            .show()
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
