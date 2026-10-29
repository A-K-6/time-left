package dev.calendarquiet.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import dev.calendarquiet.MainActivity
import dev.calendarquiet.R
import dev.calendarquiet.core.Countdown
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun finishLabel(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

class TimerNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private fun open() = PendingIntent.getActivity(context, 700, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun allowed(): Boolean = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED && manager.areNotificationsEnabled()

    fun running(timer: Countdown, boot: Int) {
        if (!allowed()) return
        manager.createNotificationChannel(NotificationChannel("timer_running", "Running timer", NotificationManager.IMPORTANCE_LOW))
        val finish = timer.finishTime(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot)
        val public = Notification.Builder(context, "timer_running").setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Timer running").setWhen(finish).setUsesChronometer(true).setChronometerCountDown(true).build()
        val notification = Notification.Builder(context, "timer_running").setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(timer.title).setContentText("Ends ${finishLabel(finish)}")
            .setWhen(finish).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).setContentIntent(open())
            .setTimeoutAfter((finish - System.currentTimeMillis()).coerceAtLeast(1)).build()
        try { manager.notify(700, notification) } catch (_: SecurityException) { /* Access changed while building. */ }
    }

    fun finished(timer: Countdown) {
        if (!allowed()) return
        manager.createNotificationChannel(NotificationChannel("timer_finished", "Timer finished", NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(true)
        })
        val notification = Notification.Builder(context, "timer_finished").setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("${timer.title} finished").setContentText("Tap to choose your next timer.")
            .setContentIntent(open()).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build()
        try { manager.notify(701, notification) } catch (_: SecurityException) { /* Access changed while building. */ }
    }

    fun clearRunning() = manager.cancel(700)
    fun clearFinished() = manager.cancel(701)
}
