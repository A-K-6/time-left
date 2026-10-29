package dev.calendarquiet.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import dev.calendarquiet.core.Countdown
import dev.calendarquiet.core.TimerKind
import dev.calendarquiet.core.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TimerCoordinator(private val context: Context) {
    val store = TimerStore(context)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = TimerNotifications(context)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(store.read())
    val state = mutableState.asStateFlow()

    fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    suspend fun refresh() = mutex.withLock { refreshLocked() }

    suspend fun startDuration(kind: TimerKind, minutes: Int, rememberDuration: Boolean = true) = mutex.withLock {
        refreshLocked()
        check(mutableState.value.active == null) { "A timer is already running." }
        val timer = Countdown.duration(kind, minutes, System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount())
        val next = mutableState.value.copy(active = timer, completed = null,
            focusMinutes = if (rememberDuration && kind == TimerKind.FOCUS) minutes else mutableState.value.focusMinutes,
            breakMinutes = if (rememberDuration && kind == TimerKind.BREAK) minutes else mutableState.value.breakMinutes)
        save(next)
        notifications.clearFinished()
        publish()
        notifications.started(timer)
    }

    suspend fun startUntil(kind: TimerKind, title: String, endsAt: Long) = mutex.withLock {
        require(kind == TimerKind.EVENT || kind == TimerKind.FREE_TIME)
        refreshLocked()
        check(mutableState.value.active == null) { "A timer is already running." }
        val timer = Countdown(kind = kind, title = title.ifBlank { "Timer" }, startedAt = System.currentTimeMillis(), endsAt = endsAt)
        save(mutableState.value.copy(active = timer, completed = null))
        notifications.clearFinished()
        publish()
    }

    // A small escape hatch; pause/resume and more involved stop behavior are deliberately deferred.
    suspend fun clear() = mutex.withLock {
        save(mutableState.value.copy(active = null, completed = null))
        notifications.clearFinished()
        publish()
    }

    suspend fun selectCalendars(ids: Set<Long>?) = mutex.withLock { store.selectCalendars(ids) }

    private fun save(state: TimerState) { store.write(state); mutableState.value = state }

    private fun refreshLocked() {
        val previous = mutableState.value
        val now = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        val boot = bootCount()
        val rebased = previous.copy(active = previous.active?.rebaseWallClock(now, elapsedNow, boot))
        val next = rebased.completeDue(now, elapsedNow, boot)
        if (next != previous) save(next)
        publish()
        val expired = previous.active
        if (expired != null && next.active == null) {
            // Do not produce a stale sound hours after a powered-off phone is restarted.
            val finishedAt = expired.finishTime(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount())
            if (System.currentTimeMillis() - finishedAt <= 5 * 60_000) notifications.finished(expired)
        }
    }

    private fun alarmIntent() = PendingIntent.getBroadcast(context, 700,
        Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun publish() {
        val timer = mutableState.value.active
        val pending = alarmIntent()
        if (timer == null) {
            alarms.cancel(pending)
            notifications.clearRunning()
        } else {
            val boot = bootCount()
            val elapsed = timer.usesElapsedClock(boot)
            val type = if (elapsed) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP
            val at = if (elapsed) timer.elapsedDeadline!! else timer.endsAt
            try {
                if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(type, at, pending)
                else alarms.setAndAllowWhileIdle(type, at, pending)
            } catch (_: SecurityException) { alarms.setAndAllowWhileIdle(type, at, pending) }
            notifications.running(timer, boot)
        }
        TimerWidget.render(context, mutableState.value, bootCount())
    }
}
