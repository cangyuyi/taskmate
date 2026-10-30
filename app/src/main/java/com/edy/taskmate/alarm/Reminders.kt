package com.edy.taskmate.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.edy.taskmate.MainActivity
import com.edy.taskmate.R
import com.edy.taskmate.data.Dates
import com.edy.taskmate.data.Task
import com.edy.taskmate.data.TaskOps
import com.edy.taskmate.data.TaskStore

object Notifications {
    const val CHANNEL_ID = "reminders"

    const val ACTION_COMPLETE = "com.edy.taskmate.action.COMPLETE"
    const val ACTION_SNOOZE = "com.edy.taskmate.action.SNOOZE"
    const val ACTION_OPEN = "com.edy.taskmate.action.OPEN"

    const val EXTRA_TASK_ID = "task_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "任务提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "到点提醒你该做什么"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** 立刻发一条测试通知，验证渠道和权限。 */
    fun postTest(context: Context) {
        val notification = build(context, "test-reminder", "测试提醒", "看到这条就说明通知渠道正常。", canAct = false)
        runCatching {
            NotificationManagerCompat.from(context).notify(9_100, notification)
        }
    }

    fun build(context: Context, taskId: String, title: String, body: String, canAct: Boolean) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_task)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setContentIntent(openIntent(context, taskId))
        .apply {
            if (canAct) {
                addAction(0, "完成", actionIntent(context, taskId, ACTION_COMPLETE, 1))
                addAction(0, "10 分钟后", actionIntent(context, taskId, ACTION_SNOOZE, 2))
            }
        }
        .build()

    private fun openIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_task_id", taskId)
        }
        return PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(context: Context, taskId: String, action: String, salt: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode() + salt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * 用 AlarmManager 排系统级闹钟：App 被杀掉也会响。
 */
object ReminderScheduler {

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(context: Context): Boolean {
        val manager = alarmManager(context)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    fun syncAll(context: Context, tasks: List<Task>) {
        tasks.forEach { task ->
            if (task.canRemind) schedule(context, task) else cancel(context, task.id)
        }
    }

    fun schedule(context: Context, task: Task) {
        val at = task.remindAt ?: return
        if (!task.canRemind) {
            cancel(context, task.id)
            return
        }
        val manager = alarmManager(context)
        val pending = pendingIntent(context, task.id, task.title, task.notes, task.dueAt ?: 0L)

        runCatching {
            if (canScheduleExact(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }

    /** 60 秒后发一条测试提醒，验证「闹钟 → 通知」整条链路。 */
    fun scheduleTest(context: Context, seconds: Int = 60) {
        val manager = alarmManager(context)
        val at = System.currentTimeMillis() + seconds * 1000L
        val pending = pendingIntent(context, "test-reminder", "测试提醒", "如果你看到这条，说明到点提醒是通的。", 0L)
        runCatching {
            if (canScheduleExact(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }

    fun cancel(context: Context, taskId: String) {
        val manager = alarmManager(context)
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(
        context: Context,
        taskId: String,
        title: String,
        body: String,
        dueAt: Long
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("due_at", dueAt)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "任务提醒" }
        val notes = intent.getStringExtra("body").orEmpty()
        val dueAt = intent.getLongExtra("due_at", 0L)

        Notifications.ensureChannel(context)

        val body = when {
            notes.isNotBlank() -> notes
            dueAt > 0L -> "${Dates.relativeDayText(dueAt)} ${Dates.timeText(dueAt)} 到期"
            else -> "该做这件事了"
        }

        val notification = Notifications.build(
            context = context,
            taskId = taskId,
            title = title,
            body = body,
            canAct = !taskId.startsWith("test-")
        )

        runCatching {
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        }
    }
}

/**
 * 通知上的「完成 / 10 分钟后」按钮。
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(Notifications.EXTRA_TASK_ID) ?: return
        val action = intent.action ?: return
        if (taskId.startsWith("test-")) return

        val pending = goAsync()
        Thread {
            try {
                val store = TaskStore(context)
                val all = store.load()
                val updated = when (action) {
                    Notifications.ACTION_COMPLETE -> TaskOps.complete(all, taskId)
                    Notifications.ACTION_SNOOZE -> TaskOps.snooze(
                        all,
                        taskId,
                        System.currentTimeMillis() + 10 * 60 * 1000L
                    )
                    else -> all
                }
                if (updated !== all) {
                    store.save(updated)
                    ReminderScheduler.syncAll(context, updated)
                }
                runCatching {
                    NotificationManagerCompat.from(context).cancel(taskId.hashCode())
                }
                TaskWidget.update(context)
            } catch (error: Exception) {
                // 通知操作失败就静默放弃，下次打开 App 会以本地数据为准
            } finally {
                pending.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val tasks = runCatching { TaskStore(context).load() }.getOrDefault(emptyList())
        ReminderScheduler.syncAll(context, tasks)
        TaskWidget.update(context)
    }
}