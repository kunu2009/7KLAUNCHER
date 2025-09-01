package com.sevenk.launcher

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return Factory(applicationContext, appWidgetId)
    }

    private class Factory(private val context: Context, private val appWidgetId: Int) : RemoteViewsFactory {
        private val prefs = context.getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE)
        private var items: MutableList<String> = mutableListOf()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            val widgetRaw = prefs.getString("todo_widget_" + appWidgetId, "") ?: ""
            var list = widgetRaw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (list.isEmpty()) {
                val globalRaw = prefs.getString("todos_global", "") ?: ""
                list = globalRaw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            }
            items = list.toMutableList()
        }

        override fun onDestroy() {
            items.clear()
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val item = items[position]
            val checked = item.startsWith("[x] ")
            val label = if (checked) item.removePrefix("[x] ") else item
            val prefix = if (checked) "☑ " else "☐ "
            val rv = RemoteViews(context.packageName, R.layout.widget_todo_item)
            rv.setTextViewText(R.id.todo_text, prefix + label)

            // Fill-in intent for toggle
            val fill = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(TodoWidgetProvider.EXTRA_INDEX, position)
            }
            rv.setOnClickFillInIntent(R.id.itemRoot, fill)
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true
    }
}
