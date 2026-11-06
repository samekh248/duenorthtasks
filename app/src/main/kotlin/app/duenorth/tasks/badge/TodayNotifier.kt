package app.duenorth.tasks.badge

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.duenorth.tasks.MainActivity
import app.duenorth.tasks.R
import app.duenorth.tasks.data.db.TaskWithList

/**
 * Launchers draw their icon badge from the app's notifications, so the count rides on one quiet
 * notification: no sound, no vibration, no pop-up, never on a watch. Its number is the badge.
 * The badge lives and dies with that notification, so it is ongoing ("clear all" leaves it) and
 * a swipe that removes it anyway posts it again through [TodayDismissedReceiver] while tasks are due.
 */
class TodayNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Notifications are allowed for the app and the "today" channel is not turned off. */
    fun allowed(): Boolean {
        if (!permissionGranted() || !manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannelCompat(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    /** The Android 13+ runtime permission; always granted before 13. */
    fun permissionGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun show(today: TodayCount) {
        if (today.count == 0 || !allowed()) {
            clear()
            return
        }
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Fires only when the person removes it, never on our own cancel, so it cannot loop.
        val dismissed = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TodayDismissedReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = today.title()
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        today.titles.forEach { style.addLine(it) }
        if (today.count > today.titles.size) style.setSummaryText("and ${today.count - today.titles.size} more")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_today)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(today.titles.joinToString(", "))
            .setStyle(style)
            .setNumber(today.count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setContentIntent(open)
            .setOngoing(true)
            .setDeleteIntent(dismissed)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission went away between the check and the post; nothing to show then.
        }
    }

    fun clear() = manager.cancel(NOTIFICATION_ID)

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("today")
                .setDescription("How many tasks are due today or overdue. Launchers show it on the app icon.")
                .setShowBadge(true)
                .setSound(null, null)
                .setVibrationEnabled(false)
                .setLightsEnabled(false)
                .build()
        )
    }

    private companion object {
        const val CHANNEL_ID = "today"
        const val NOTIFICATION_ID = 1

        /** The default magenta on light (constitution Principle I). */
        const val ACCENT = 0xFFB0005E.toInt()
    }
}

/** What the badge shows: the today section's count, and the first few titles for the shade. */
data class TodayCount(val count: Int, val titles: List<String>) {
    fun title(): String = if (count == 1) "1 task for today" else "$count tasks for today"

    companion object {
        const val SHOWN_TITLES = 5

        val None = TodayCount(0, emptyList())

        /** The today section's rows: open tasks overdue or due today, in its order. */
        fun of(rows: List<TaskWithList>): TodayCount {
            val open = rows.filter { !it.task.completed }
            return TodayCount(open.size, open.take(SHOWN_TITLES).map { it.task.title })
        }
    }
}
