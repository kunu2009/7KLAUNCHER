package com.sevenk.launcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java))
            manager.notifyAppWidgetViewDataChanged(ids, R.id.listView)
            onUpdate(context, manager, ids)
        } else if (intent.action == ACTION_TOGGLE) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val index = intent.getIntExtra(EXTRA_INDEX, -1)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && index >= 0) {
                toggleItem(context, appWidgetId, index)
                val mgr = AppWidgetManager.getInstance(context)
                mgr.notifyAppWidgetViewDataChanged(appWidgetId, R.id.listView)
                updateWidget(context, mgr, appWidgetId)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.sevenk.launcher.todo.REFRESH"
        private const val PREFS = "sevenk_launcher_prefs"
        private const val KEY_PREFIX = "todo_widget_" // + appWidgetId
        const val ACTION_TOGGLE = "com.sevenk.launcher.todo.TOGGLE"
        const val EXTRA_INDEX = "extra_index"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_todo)

            // Set remote adapter for ListView
            val svcIntent = Intent(context, TodoWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            views.setRemoteAdapter(R.id.listView, svcIntent)
            views.setEmptyView(R.id.listView, android.R.id.empty)

            // Tap on header opens config activity
            val cfgIntent = Intent(context, TodoWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val cfgPi = PendingIntent.getActivity(
                context, appWidgetId, cfgIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.todoHeader, cfgPi)

            // Tap refresh icon
            val refreshIntent = Intent(context, TodoWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            val refreshPi = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnRefresh, refreshPi)

            // Tap Add opens config
            views.setOnClickPendingIntent(R.id.btnAdd, cfgPi)

            // Set item click pending intent template for toggling
            val toggleIntent = Intent(context, TodoWidgetProvider::class.java).apply { action = ACTION_TOGGLE }
            val togglePi = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.listView, togglePi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun toggleItem(context: Context, appWidgetId: Int, index: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val widgetKey = KEY_PREFIX + appWidgetId
            val rawWidget = prefs.getString(widgetKey, "") ?: ""
            var scopeKey = widgetKey
            var items = rawWidget.split('\n').map { it }.toMutableList()
            if (items.isEmpty() || items.all { it.trim().isEmpty() }) {
                // Fallback to global list
                scopeKey = "todos_global"
                val rawGlobal = prefs.getString(scopeKey, "") ?: ""
                items = rawGlobal.split('\n').map { it }.toMutableList()
            }
            if (index in items.indices) {
                val s = items[index].trim()
                val toggled = if (s.startsWith("[x] ")) s.removePrefix("[x] ") else "[x] $s"
                items[index] = toggled
                prefs.edit().putString(scopeKey, items.joinToString("\n")).apply()
            }
        }
    }
}
