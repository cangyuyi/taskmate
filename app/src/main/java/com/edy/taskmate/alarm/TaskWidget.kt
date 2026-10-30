package com.edy.taskmate.alarm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.edy.taskmate.MainActivity
import com.edy.taskmate.R
import com.edy.taskmate.data.Dates
import com.edy.taskmate.data.Grouping
import com.edy.taskmate.data.TaskStore

/**
 * 桌面小组件：今天还剩几件 + 前三条任务，点一下进 App。
 */
class TaskWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, build(context))
        }
    }

    companion object {

        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.task_widget)
            val tasks = runCatching { TaskStore(context).load() }.getOrDefault(emptyList())
            val buckets = Grouping.today(tasks)
            val remaining = buckets.overdue + buckets.today

            views.setTextViewText(
                R.id.widget_headline,
                when {
                    remaining.isEmpty() && buckets.doneCount > 0 -> "今天都完成了"
                    remaining.isEmpty() -> "今天没有安排"
                    else -> "还有 ${remaining.size} 件事"
                }
            )

            val lines = listOf(R.id.widget_line1, R.id.widget_line2, R.id.widget_line3)
            val preview = remaining.take(3)

            lines.forEachIndexed { index, viewId ->
                val task = preview.getOrNull(index)
                views.setTextViewText(
                    viewId,
                    task?.let {
                        val time = it.dueAt?.takeIf { due -> Dates.isToday(due) }?.let { due -> Dates.timeText(due) + "  " }
                            ?: ""
                        "· $time${it.title}"
                    } ?: ""
                )
            }

            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                7_001,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}