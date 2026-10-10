package dev.calendarquiet.automation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.calendarquiet.MainActivity
import dev.calendarquiet.R
import dev.calendarquiet.core.Session

class ReminderNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun post(session: Session): Boolean {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
            !manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(NotificationChannel("reminders", "Calendar reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Custom reminders for your calendar rules"
        })
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = session.rule.reminderText.replace("{event}", session.event.title)
        val notification = Notification.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(session.event.title)
            .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER).setTimeoutAfter(maxOf(1, session.end - System.currentTimeMillis()))
            .build()
        manager.notify(session.reminderKey, 1, notification)
        return true
    }
}
