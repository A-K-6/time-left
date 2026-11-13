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
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import dev.calendarquiet.core.TimerKind
import dev.calendarquiet.MainActivity
import dev.calendarquiet.R
import dev.calendarquiet.core.TimerState
import dev.calendarquiet.core.formatCountdown
import dev.calendarquiet.core.Countdown
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun finishLabel(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

class TimerNotifications(private val context: Context) {
    private val prefs = context.getSharedPreferences("timer_sounds", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private fun open() = PendingIntent.getActivity(context, 700, Intent(context, MainActivity::class.java).setAction(MainActivity.OPEN_TIMER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun allowed(): Boolean = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED && manager.areNotificationsEnabled()

    fun running(timer: Countdown, boot: Int, revision: Long) {
        if (!allowed()) return
        manager.createNotificationChannel(NotificationChannel("timer_running", "Running timer", NotificationManager.IMPORTANCE_LOW))
        val finish = timer.finishTime(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot)
        val detail = if (timer.isPaused) "Paused · ${formatCountdown(timer.pausedRemaining!!)} left" else "Ends ${finishLabel(finish)}"
        val public = Notification.Builder(context, "timer_running").setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(if (timer.isPaused) "Timer paused" else "Timer running")
        val builder = Notification.Builder(context, "timer_running").setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(timer.title).setContentText(detail)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open())
        if (!timer.isPaused) {
            public.setWhen(finish).setUsesChronometer(true).setChronometerCountDown(true)
            builder.setWhen(finish).setUsesChronometer(true).setChronometerCountDown(true)
                .setTimeoutAfter((finish - System.currentTimeMillis()).coerceAtLeast(1))
        } else builder.setShowWhen(false)
        if (timer.canPause) builder.addAction(Notification.Action.Builder(null,
            if (timer.isPaused) "Resume" else "Pause", TimerReceiver.action(context,
                if (timer.isPaused) TimerReceiver.RESUME else TimerReceiver.PAUSE, revision)).build())
        builder.addAction(Notification.Action.Builder(null, "Stop",
            TimerReceiver.action(context, TimerReceiver.STOP, revision)).build())
        builder.setPublicVersion(public.build())
        try { manager.notify(700, builder.build()) } catch (_: SecurityException) { /* Access changed while building. */ }
    }

    val playOnStart: Boolean get() = prefs.getBoolean("on_start", true)
    val playOnFinish: Boolean get() = prefs.getBoolean("on_finish", true)

    fun setPlayOnStart(value: Boolean) { check(prefs.edit().putBoolean("on_start", value).commit()) }
    fun setPlayOnFinish(value: Boolean) { check(prefs.edit().putBoolean("on_finish", value).commit()) }

    fun ensureSoundChannels() {
        val legacy = manager.getNotificationChannel("timer_finished")
        listOf(TimerKind.FOCUS, TimerKind.BREAK, TimerKind.EVENT).forEach { kind ->
            if (manager.getNotificationChannel(channelId(kind)) == null) {
                // Sound/audio attributes cannot be changed on an existing channel. Preserve explicit legacy choices.
                val importance = legacy?.takeIf { it.hasUserSetImportance() || it.importance == NotificationManager.IMPORTANCE_NONE }
                    ?.importance ?: NotificationManager.IMPORTANCE_HIGH
                val sound = if (legacy != null && (legacy.hasUserSetSound() || legacy.sound == null)) legacy.sound
                    else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                manager.createNotificationChannel(NotificationChannel(channelId(kind), channelName(kind), importance).apply {
                    description = "Customizable timer cues. Uses your phone's alarm volume."
                    setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    enableVibration(legacy?.shouldVibrate() ?: true)
                    setShowBadge(false)
                })
            }
        }
    }

    fun settingsIntent(kind: TimerKind): Intent {
        ensureSoundChannels()
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId(kind))
    }

    fun soundSummary(kind: TimerKind): String {
        val channel = manager.getNotificationChannel(channelId(kind)) ?: return "Default alarm sound"
        if (!allowed() || channel.importance == NotificationManager.IMPORTANCE_NONE) return "Notifications disabled"
        if (channel.sound == null || channel.importance < NotificationManager.IMPORTANCE_DEFAULT) return "Silent"
        return runCatching { RingtoneManager.getRingtone(context, channel.sound)?.getTitle(context) }
            .getOrNull() ?: "Custom sound"
    }

    fun started(timer: Countdown) {
        if (playOnStart && timer.canPause) {
            postAlert(timer.kind, "${timer.title} started", "Ends ${finishLabel(timer.endsAt)}", 702, 8_000)
        }
    }

    fun finished(timer: Countdown, state: TimerState) {
        if (playOnFinish) {
            val next = state.nextSessionKind
            val label = when (next) {
                TimerKind.LONG_BREAK -> "Start long break"
                TimerKind.BREAK -> "Start short break"
                else -> "Start focus"
            }
            postAlert(timer.kind, "${timer.title} finished", "$label · ${state.minutesFor(next)} min", 701, 60_000,
                label, TimerReceiver.action(context, TimerReceiver.START_NEXT, state.revision))
        }
    }

    fun test(kind: TimerKind): Boolean = postAlert(kind, "${channelName(kind)} test", "Your timer cue plays once.", 703, 10_000)

    private fun postAlert(kind: TimerKind, title: String, text: String, id: Int, timeout: Long, actionLabel: String? = null, action: PendingIntent? = null): Boolean {
        if (!allowed()) return false
        ensureSoundChannels()
        val builder = Notification.Builder(context, channelId(kind)).setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title).setContentText(text).setContentIntent(open()).setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setTimeoutAfter(timeout)
        if (action != null) builder.addAction(Notification.Action.Builder(null, actionLabel, action).build())
        return try { manager.notify(id, builder.build()); true } catch (_: SecurityException) { false }
    }

    fun clearRunning() = manager.cancel(700)
    fun clearFinished() { manager.cancel(701); manager.cancel(702); manager.cancel(703) }

    companion object {
        fun channelId(kind: TimerKind): String = when (kind) {
            TimerKind.FOCUS -> "timer_focus_sound_v1"
            TimerKind.BREAK, TimerKind.LONG_BREAK -> "timer_break_sound_v1"
            else -> "timer_calendar_sound_v1"
        }
        fun channelName(kind: TimerKind): String = when (kind) {
            TimerKind.FOCUS -> "Focus sound"
            TimerKind.BREAK, TimerKind.LONG_BREAK -> "Break sound"
            else -> "Calendar timer sound"
        }
    }
}
