package dev.sharno.due

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TaskScheduler {
    const val ACTION_TASK_DUE = "dev.sharno.due.TASK_DUE"
    const val ACTION_COMPLETE_TASK = "dev.sharno.due.COMPLETE_TASK"
    const val ACTION_NOTIFICATION_DISMISSED = "dev.sharno.due.NOTIFICATION_DISMISSED"
    const val ACTION_WATCHDOG = "dev.sharno.due.WATCHDOG"
    const val EXTRA_TASK_ID = "task_id"

    private const val CHANNEL_ID = "overdue_tasks"
    private const val LEGACY_NOTIFICATION_ID = 7
    private const val WATCHDOG_REQUEST_CODE = 1
    private const val WATCHDOG_INTERVAL_MILLIS = 15 * 60 * 1000L
    private const val NOTIFICATION_TAG_PREFIX = "task:"
    private val dueTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm")

    suspend fun synchronize(context: Context) {
        val repository = TodoRepository(context)
        val settings = SettingsRepository(context).settings.first()
        synchronize(context, repository.all(), settings)
    }

    fun synchronize(
        context: Context,
        todos: List<Todo>,
        settings: DueSettings = DueSettings(),
    ) {
        val now = System.currentTimeMillis()
        todos.forEach { todo ->
            if (settings.remindersEnabled && !todo.completed && todo.dueAtMillis > now) {
                schedule(context, todo)
            } else {
                cancelAlarm(context, todo.id)
            }
        }

        val overdue = todos
            .asSequence()
            .takeIf { settings.remindersEnabled }
            ?.filterNot(Todo::completed)
            ?.filter { it.dueAtMillis <= now }
            ?.sortedBy(Todo::dueAtMillis)
            ?.toList()
            ?: emptyList()

        updateOverdueNotifications(context, overdue)
        if (overdue.isNotEmpty() && canPostNotifications(context)) {
            scheduleWatchdog(context)
        } else {
            cancelWatchdog(context)
        }
    }

    fun cancelAlarm(context: Context, taskId: String) {
        alarmManager(context).cancel(duePendingIntent(context, taskId))
    }

    private fun scheduleWatchdog(context: Context) {
        // This is deliberately inexact: the due alarm is precise, while this
        // only repairs a notification that System UI or an OEM removed.
        alarmManager(context).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + WATCHDOG_INTERVAL_MILLIS,
            watchdogPendingIntent(context),
        )
    }

    private fun cancelWatchdog(context: Context) {
        alarmManager(context).cancel(watchdogPendingIntent(context))
    }

    private fun schedule(context: Context, todo: Todo) {
        val manager = alarmManager(context)
        val operation = duePendingIntent(context, todo.id)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, todo.dueAtMillis, operation)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, todo.dueAtMillis, operation)
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateOverdueNotifications(context: Context, overdue: List<Todo>) {
        if (!canPostNotifications(context)) {
            cancelAllTaskNotifications(context)
            return
        }

        ensureChannel(context)
        val activeTags = overdue.asSequence().map { todo -> notificationTag(todo.id) }.toSet()
        cancelStaleTaskNotifications(context, activeTags)
        overdue.forEach { todo ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Overdue: ${todo.title}")
                .setContentText("Due ${formatDueAt(todo.dueAtMillis)}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(taskLine(todo)))
                .setContentIntent(openAppPendingIntent(context))
                // Some Android versions allow users to swipe ongoing reminders.
                // Re-post immediately when that explicit dismissal happens.
                .setDeleteIntent(notificationDismissedPendingIntent(context, todo.id))
                .addAction(
                    android.R.drawable.checkbox_on_background,
                    "Complete",
                    completePendingIntent(context, todo.id),
                )
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()

            NotificationManagerCompat.from(context).notify(
                notificationTag(todo.id),
                notificationId(todo.id),
                notification,
            )
        }
    }

    private fun cancelStaleTaskNotifications(context: Context, activeTags: Set<String>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // v1.2.0 used one untagged aggregate notification. Remove it once the
        // per-task notification scheme is active.
        manager.cancel(LEGACY_NOTIFICATION_ID)
        manager.activeNotifications
            .asSequence()
            .mapNotNull { notification -> notification.tag }
            .filter { it.startsWith(NOTIFICATION_TAG_PREFIX) && it !in activeTags }
            .forEach { tag ->
                manager.cancel(tag, notificationId(tag.removePrefix(NOTIFICATION_TAG_PREFIX)))
            }
    }

    private fun cancelAllTaskNotifications(context: Context) {
        cancelStaleTaskNotifications(context, emptySet())
    }

    private fun taskLine(todo: Todo): String = "${todo.title} — ${formatDueAt(todo.dueAtMillis)}"

    private fun formatDueAt(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(dueTimeFormatter)

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.overdue_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.overdue_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }

        return true
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun duePendingIntent(context: Context, taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.hashCode(),
        Intent(context, TodoAlarmReceiver::class.java)
            .setAction(ACTION_TASK_DUE)
            .setData(Uri.parse("due://task/$taskId")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun completePendingIntent(context: Context, taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.hashCode(),
        Intent(context, TodoAlarmReceiver::class.java)
            .setAction(ACTION_COMPLETE_TASK)
            .setData(Uri.parse("complete://task/$taskId"))
            .putExtra(EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notificationDismissedPendingIntent(context: Context, taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        notificationId(taskId),
        Intent(context, TodoAlarmReceiver::class.java)
            .setAction(ACTION_NOTIFICATION_DISMISSED)
            .setData(Uri.parse("due://notification/dismissed/$taskId"))
            .putExtra(EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun watchdogPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        WATCHDOG_REQUEST_CODE,
        Intent(context, TodoAlarmReceiver::class.java)
            .setAction(ACTION_WATCHDOG)
            .setData(Uri.parse("due://notification/watchdog")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openAppPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notificationTag(taskId: String): String = "$NOTIFICATION_TAG_PREFIX$taskId"

    private fun notificationId(taskId: String): Int = when (val hash = taskId.hashCode()) {
        0 -> 1
        LEGACY_NOTIFICATION_ID -> LEGACY_NOTIFICATION_ID + 1
        else -> hash
    }
}
