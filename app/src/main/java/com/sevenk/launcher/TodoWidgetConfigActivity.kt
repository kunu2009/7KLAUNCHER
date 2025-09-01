package com.sevenk.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class TodoWidgetConfigActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE) }
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_todo_widget_config)

        // Get widget ID
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_CANCELED)
            finish(); return
        }

        val edit = findViewById<EditText>(R.id.editTasks)
        val existing = prefs.getString("todo_widget_" + appWidgetId, "") ?: ""
        edit.setText(existing)

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit().putString("todo_widget_" + appWidgetId, edit.text.toString()).apply()
            // Update widget
            val mgr = AppWidgetManager.getInstance(this)
            TodoWidgetProvider.updateWidget(this, mgr, appWidgetId)
            setResult(Activity.RESULT_OK)
            finish()
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
