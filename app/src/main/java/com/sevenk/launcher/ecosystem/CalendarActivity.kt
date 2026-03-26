package com.sevenk.launcher.ecosystem

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarActivity : AppCompatActivity() {

    private val calendar = Calendar.getInstance()
    private val today = Calendar.getInstance()
    private lateinit var monthYearText: TextView
    private lateinit var calendarGrid: GridLayout
    private lateinit var notesText: TextView
    private val prefs by lazy { getSharedPreferences("sevenk_calendar", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Calendar"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        // Header with navigation
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val prevBtn = Button(this).apply {
            text = "‹ Prev"
            setOnClickListener { previousMonth() }
        }

        monthYearText = TextView(this).apply {
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nextBtn = Button(this).apply {
            text = "Next ›"
            setOnClickListener { nextMonth() }
        }

        val todayBtn = Button(this).apply {
            text = "Today"
            setOnClickListener { goToday() }
        }

        headerLayout.addView(prevBtn)
        headerLayout.addView(monthYearText)
        headerLayout.addView(nextBtn)
        headerLayout.addView(todayBtn)

        // Calendar grid
        calendarGrid = GridLayout(this).apply {
            columnCount = 7
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Day labels
        val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        dayLabels.forEach { day ->
            val label = TextView(this).apply {
                text = day
                textSize = 12f
                setTextColor(0xFF0D7377.toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                setPadding(8, 8, 8, 8)
            }
            calendarGrid.addView(label)
        }

        // Notes section
        val notesLabel = TextView(this).apply {
            text = "Daily Notes"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 16, 0, 8)
        }

        notesText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFCCCCCC.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF252525.toInt())
        }

        val addNoteBtn = Button(this).apply {
            text = "Add/Edit Note"
            setOnClickListener { showNoteDialog() }
        }

        val scrollView = ScrollView(this)
        scrollView.addView(calendarGrid)

        root.addView(headerLayout)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        root.addView(notesLabel)
        root.addView(notesText)
        root.addView(addNoteBtn)

        setContentView(root)
        refreshCalendar()
    }

    private fun refreshCalendar() {
        monthYearText.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)

        // Clear old days
        calendarGrid.removeViews(7, calendarGrid.childCount - 7)

        val firstDay = Calendar.getInstance().apply {
            timeInMillis = calendar.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val offset = firstDay.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Empty cells before month start
        repeat(offset) {
            calendarGrid.addView(TextView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 60
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            })
        }

        // Days of month
        for (day in 1..daysInMonth) {
            val dayView = TextView(this).apply {
                text = day.toString()
                textSize = 14f
                val isToday = day == today.get(Calendar.DAY_OF_MONTH) &&
                        calendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                        calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)

                setTextColor(if (isToday) 0xFF00FF00.toInt() else 0xFFFFFFFF.toInt())
                setBackgroundColor(if (isToday) 0xFF1A3A3A.toInt() else 0xFF2D2D2D.toInt())
                setPadding(8, 8, 8, 8)

                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 60
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }

                setOnClickListener {
                    selectDay(day)
                }
            }
            calendarGrid.addView(dayView)
        }

        selectDay(if (calendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                      calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)) today.get(Calendar.DAY_OF_MONTH) else 1)
    }

    private fun selectDay(day: Int) {
        calendar.set(Calendar.DAY_OF_MONTH, day)
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val note = prefs.getString(key, "No notes for this day")
        notesText.text = note
    }

    private fun showNoteDialog() {
        val input = EditText(this).apply {
            val current = notesText.text?.toString().orEmpty()
            setText(if (current == "No notes for this day") "" else current)
        }

        showGlassInputSheet(
            title = "Edit Note",
            input = input,
            primaryLabel = "Save"
        ) {
                val key = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                prefs.edit().putString(key, input.text.toString()).apply()
                refreshCalendar()
                Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showGlassInputSheet(
        title: String,
        input: EditText,
        primaryLabel: String,
        secondaryLabel: String = "Cancel",
        onPrimary: () -> Unit
    ) {
        val sheet = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            setBackgroundColor(0xFF1B1730.toInt())
        }

        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(12))
        })

        input.setTextColor(0xFFFFFFFF.toInt())
        input.setHintTextColor(0x99FFFFFF.toInt())
        input.setBackgroundColor(0xFF2A235F.toInt())
        input.setPadding(dp(12), dp(10), dp(12), dp(10))
        val inputLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
        root.addView(input, inputLp)

        root.addView(TextView(this).apply {
            text = primaryLabel
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFF6C4BFF.toInt())
            foreground = AppCompatResources.getDrawable(this@CalendarActivity, android.R.drawable.list_selector_background)
            setOnClickListener {
                onPrimary()
                sheet.dismiss()
            }
        })

        root.addView(TextView(this).apply {
            text = secondaryLabel
            textSize = 14f
            setTextColor(0xFF80CBC4.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { sheet.dismiss() }
        })

        sheet.setContentView(root)
        sheet.setOnShowListener {
            input.requestFocus()
            input.setSelection(input.text?.length ?: 0)
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun previousMonth() {
        calendar.add(Calendar.MONTH, -1)
        refreshCalendar()
    }

    private fun nextMonth() {
        calendar.add(Calendar.MONTH, 1)
        refreshCalendar()
    }

    private fun goToday() {
        calendar.timeInMillis = System.currentTimeMillis()
        refreshCalendar()
    }
}
