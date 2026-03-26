package com.sevenk.launcher.ecosystem

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.app.AppCompatActivity
import android.text.InputType
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BudgetGuardianActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("sevenk_budget_guardian", MODE_PRIVATE) }
    private lateinit var budgetText: TextView
    private lateinit var spentText: TextView
    private lateinit var monthlySpentText: TextView
    private lateinit var leftText: TextView
    private lateinit var alertsText: TextView
    private lateinit var historyContainer: LinearLayout
    private lateinit var categorySummaryText: TextView
    private val history = mutableListOf<ExpenseEntry>()

    private data class ExpenseEntry(
        val id: Long,
        val amount: Float,
        val category: String,
        val note: String,
        val timestamp: Long,
        val recurrenceDays: Int,
        val isTemplate: Boolean,
        val parentTemplateId: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Budget Guardian"

        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF151515.toInt())
        }

        content.addView(TextView(this).apply {
            text = "7K Budget Guardian"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        })
        content.addView(TextView(this).apply {
            text = "Offline monthly budget defender"
            textSize = 13f
            setTextColor(0xFFB0BEC5.toInt())
            setPadding(0, 8, 0, 20)
        })

        budgetText = metricLabel(content, "Budget: ₹0")
        spentText = metricLabel(content, "Spent: ₹0")
        monthlySpentText = metricLabel(content, "This Month: ₹0")
        leftText = metricLabel(content, "Left: ₹0")

        alertsText = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFCC80.toInt())
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF2A241E.toInt())
        }
        content.addView(alertsText)

        val historyTitle = TextView(this).apply {
            text = "Expense History"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 14, 0, 8)
        }

        categorySummaryText = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF80CBC4.toInt())
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF202428.toInt())
        }
        content.addView(categorySummaryText)
        content.addView(historyTitle)

        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(historyContainer)

        val setBudget = Button(this).apply {
            text = "Set Monthly Budget"
            isAllCaps = false
            setOnClickListener { promptNumber("Set Budget", "budget") }
        }
        val addExpense = Button(this).apply {
            text = "Add Expense"
            isAllCaps = false
            setOnClickListener { promptNumber("Add Expense", "expense") }
        }
        val setCategoryLimit = Button(this).apply {
            text = "Set Category Limit"
            isAllCaps = false
            setOnClickListener { showCategoryLimitDialog() }
        }
        val applyRecurring = Button(this).apply {
            text = "Apply Recurring Due"
            isAllCaps = false
            setOnClickListener {
                val added = materializeRecurringExpenses()
                if (added > 0) {
                    saveHistory()
                    refresh()
                }
            }
        }
        val reset = Button(this).apply {
            text = "Reset Month"
            isAllCaps = false
            setOnClickListener {
                val (year, month) = currentYearMonth()
                history.removeAll {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month && !it.isTemplate
                }
                // Keep templates and older records, clear month activity only.
                prefs.edit().putFloat("spent", 0f).apply()
                refresh()
            }
        }

        content.addView(setBudget)
        content.addView(addExpense)
        content.addView(setCategoryLimit)
        content.addView(applyRecurring)
        content.addView(reset)

        root.addView(content)
        setContentView(root)

        loadHistory()
        materializeRecurringExpenses()
        refresh()
    }

    private fun metricLabel(parent: LinearLayout, initial: String): TextView {
        return TextView(this).apply {
            text = initial
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 8, 0, 8)
            setBackgroundColor(0xFF242424.toInt())
            setPadding(14, 14, 14, 14)
        }.also {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 8
            parent.addView(it, lp)
        }
    }

    private fun promptNumber(title: String, mode: String) {
        val input = EditText(this).apply {
            hint = "Enter amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val categoryInput = EditText(this).apply {
            hint = "Category (Food, Travel, Rent...)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val noteInput = EditText(this).apply {
            hint = "Optional note"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val recurrenceInput = EditText(this).apply {
            hint = "Repeat every N days (0 none)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
            if (mode == "expense") {
                addView(categoryInput)
                addView(noteInput)
                addView(recurrenceInput)
            }
        }
        showGlassFormSheet(
            title = title,
            content = holder,
            primaryLabel = "Save"
        ) {
                val amount = input.text?.toString()?.toFloatOrNull() ?: 0f
                if (amount <= 0f) return@showGlassFormSheet
                when (mode) {
                    "budget" -> prefs.edit().putFloat("budget", amount).apply()
                    "expense" -> {
                        prefs.edit().putFloat("spent", prefs.getFloat("spent", 0f) + amount).apply()
                        val now = System.currentTimeMillis()
                        val recurrenceDays = recurrenceInput.text?.toString()?.toIntOrNull()?.coerceIn(0, 3650) ?: 0
                        history.add(
                            0,
                            ExpenseEntry(
                                id = now,
                                amount = amount,
                                category = categoryInput.text?.toString()?.trim().orEmpty().ifBlank { "General" },
                                note = noteInput.text?.toString()?.trim().orEmpty(),
                                timestamp = now,
                                recurrenceDays = recurrenceDays,
                                isTemplate = recurrenceDays > 0,
                                parentTemplateId = 0L
                            )
                        )
                        saveHistory()
                    }
                }
                refresh()
        }
    }

    private fun showCategoryLimitDialog() {
        val categoryInput = EditText(this).apply {
            hint = "Category"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val amountInput = EditText(this).apply {
            hint = "Monthly limit amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(categoryInput)
            addView(amountInput)
        }
        showGlassFormSheet(
            title = "Set Category Limit",
            content = holder,
            primaryLabel = "Save"
        ) {
                val category = categoryInput.text?.toString()?.trim().orEmpty().ifBlank { "General" }
                val amount = amountInput.text?.toString()?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
                val limits = loadCategoryLimits().toMutableMap()
                limits[category] = amount
                saveCategoryLimits(limits)
                refresh()
        }
    }

    private fun showGlassFormSheet(
        title: String,
        content: LinearLayout,
        primaryLabel: String,
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

        styleFormInputs(content)
        val contentLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
        root.addView(content, contentLp)

        root.addView(TextView(this).apply {
            text = primaryLabel
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFF6C4BFF.toInt())
            foreground = AppCompatResources.getDrawable(this@BudgetGuardianActivity, android.R.drawable.list_selector_background)
            setOnClickListener {
                onPrimary()
                sheet.dismiss()
            }
        })

        root.addView(TextView(this).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(0xFF80CBC4.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { sheet.dismiss() }
        })

        sheet.setContentView(root)
        sheet.setOnShowListener {
            val firstInput = (0 until content.childCount)
                .map { content.getChildAt(it) }
                .firstOrNull { it is EditText } as? EditText
            firstInput?.requestFocus()
            firstInput?.setSelection(firstInput.text?.length ?: 0)
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(firstInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        sheet.show()
    }

    private fun styleFormInputs(container: LinearLayout) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is EditText) {
                child.setTextColor(0xFFFFFFFF.toInt())
                child.setHintTextColor(0x99FFFFFF.toInt())
                child.setBackgroundColor(0xFF2A235F.toInt())
                child.setPadding(dp(12), dp(10), dp(12), dp(10))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                child.layoutParams = lp
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun refresh() {
        val budget = prefs.getFloat("budget", 0f)
        val spent = history.sumOf { it.amount.toDouble() }.toFloat()
        val monthlySpent = currentMonthHistory().sumOf { it.amount.toDouble() }.toFloat()
        val left = budget - monthlySpent

        budgetText.text = "Budget: ₹${"%.2f".format(budget)}"
        spentText.text = "Spent: ₹${"%.2f".format(spent)}"
        monthlySpentText.text = "This Month: ₹${"%.2f".format(monthlySpent)}"
        leftText.text = "Left: ₹${"%.2f".format(left)}"
        leftText.setTextColor(if (left >= 0f) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        alertsText.text = buildAlertSummary(budget, monthlySpent)
        updateCategorySummary()
        renderHistory()
    }

    private fun updateCategorySummary() {
        val month = currentMonthHistory()
        if (month.isEmpty()) {
            categorySummaryText.text = "Category Insights: No expense data yet"
            return
        }
        val byCategory = month.groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.amount.toDouble() }.toFloat() }
            .toList()
            .sortedByDescending { it.second }

        val limits = loadCategoryLimits()
        val breaches = byCategory.filter { (cat, total) ->
            val limit = limits[cat] ?: return@filter false
            limit > 0f && total > limit
        }

        val top = byCategory.take(3)
        val summary = top.joinToString(" • ") { "${it.first}: ₹${"%.0f".format(it.second)}" }
        val breachText = if (breaches.isEmpty()) "" else {
            "\nLimit Alerts: " + breaches.joinToString(" • ") { (cat, total) ->
                val limit = limits[cat] ?: 0f
                "$cat ${"%.0f".format(total)}/${"%.0f".format(limit)}"
            }
        }
        categorySummaryText.text = "Category Insights: $summary$breachText"
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()
        if (history.isEmpty()) {
            historyContainer.addView(TextView(this).apply {
                text = "No expenses yet."
                textSize = 13f
                setTextColor(0xFF9E9E9E.toInt())
            })
            return
        }

        history.take(20).forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(if (item.isTemplate) 0xFF22303A.toInt() else 0xFF202020.toInt())
            }
            row.addView(TextView(this).apply {
                val tag = when {
                    item.isTemplate -> " • recurring template/${item.recurrenceDays}d"
                    item.parentTemplateId != 0L -> " • recurring"
                    else -> ""
                }
                text = "₹${"%.2f".format(item.amount)} • ${item.category}${tag}"
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
            })
            row.addView(TextView(this).apply {
                val ts = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(java.util.Date(item.timestamp))
                text = ts
                textSize = 11f
                setTextColor(0xFF9E9E9E.toInt())
                setPadding(0, 2, 0, 0)
            })
            if (item.note.isNotBlank()) {
                row.addView(TextView(this).apply {
                    text = item.note
                    textSize = 12f
                    setTextColor(0xFFB0BEC5.toInt())
                    setPadding(0, 4, 0, 0)
                })
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 8
            historyContainer.addView(row, lp)
        }
    }

    private fun loadHistory() {
        val raw = prefs.getString("history", "") ?: ""
        history.clear()
        if (raw.isBlank()) return
        raw.split("||").mapNotNull { token ->
            val p = token.split("\t")
            if (p.size < 5) return@mapNotNull null
            val id = p[0].toLongOrNull() ?: return@mapNotNull null
            val amount = p[1].toFloatOrNull() ?: return@mapNotNull null
            val category = p[2]
            val note = p[3]
            val timestamp = p[4].toLongOrNull() ?: id
            val recurrenceDays = p.getOrNull(5)?.toIntOrNull()?.coerceIn(0, 3650) ?: 0
            val isTemplate = p.getOrNull(6) == "1"
            val parentTemplateId = p.getOrNull(7)?.toLongOrNull() ?: 0L
            ExpenseEntry(id, amount, category, note, timestamp, recurrenceDays, isTemplate, parentTemplateId)
        }.let { history.addAll(it) }
    }

    private fun saveHistory() {
        val raw = history.joinToString("||") {
            listOf(
                it.id.toString(),
                it.amount.toString(),
                it.category.replace("\t", " "),
                it.note.replace("\t", " "),
                it.timestamp.toString(),
                it.recurrenceDays.toString(),
                if (it.isTemplate) "1" else "0",
                it.parentTemplateId.toString()
            ).joinToString("\t")
        }
        prefs.edit().putString("history", raw).apply()
    }

    private fun currentYearMonth(): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

    private fun currentMonthHistory(): List<ExpenseEntry> {
        val (year, month) = currentYearMonth()
        return history.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
        }
    }

    private fun loadCategoryLimits(): Map<String, Float> {
        val raw = prefs.getString("category_limits", "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split(";").mapNotNull { token ->
            val idx = token.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val k = token.substring(0, idx)
            val v = token.substring(idx + 1).toFloatOrNull() ?: return@mapNotNull null
            k to v
        }.toMap()
    }

    private fun saveCategoryLimits(map: Map<String, Float>) {
        val raw = map.entries.joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit().putString("category_limits", raw).apply()
    }

    private fun buildAlertSummary(budget: Float, monthlySpent: Float): String {
        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val maxDay = now.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(day)
        val dailyBurn = monthlySpent / day
        val projected = dailyBurn * maxDay
        val overshoot = projected - budget
        val projectionLine = if (budget > 0f) {
            if (overshoot > 0f) "Projection: ₹${"%.0f".format(projected)} (over by ₹${"%.0f".format(overshoot)})"
            else "Projection: ₹${"%.0f".format(projected)} (under by ₹${"%.0f".format(-overshoot)})"
        } else {
            "Projection: set a monthly budget to enable alerts"
        }
        val recurringTemplates = history.count { it.isTemplate && it.recurrenceDays > 0 }
        return "Daily burn: ₹${"%.0f".format(dailyBurn)} • $projectionLine • Recurring templates: $recurringTemplates"
    }

    private fun materializeRecurringExpenses(): Int {
        val templates = history.filter { it.isTemplate && it.recurrenceDays > 0 }
        if (templates.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val generated = mutableListOf<ExpenseEntry>()
        templates.forEach { t ->
            val period = t.recurrenceDays * 86_400_000L
            if (period <= 0L) return@forEach

            val latestExisting = history
                .filter { it.parentTemplateId == t.id }
                .maxOfOrNull { it.timestamp }
                ?: t.timestamp

            var cursor = latestExisting + period
            while (cursor <= now) {
                generated.add(
                    ExpenseEntry(
                        id = cursor + t.id,
                        amount = t.amount,
                        category = t.category,
                        note = if (t.note.isBlank()) "Auto recurring" else t.note,
                        timestamp = cursor,
                        recurrenceDays = 0,
                        isTemplate = false,
                        parentTemplateId = t.id
                    )
                )
                cursor += period
            }
        }
        if (generated.isEmpty()) return 0
        history.addAll(generated)
        history.sortByDescending { it.timestamp }
        return generated.size
    }
}
