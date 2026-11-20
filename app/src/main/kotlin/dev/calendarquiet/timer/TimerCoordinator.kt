package dev.calendarquiet.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import dev.calendarquiet.core.Countdown
import dev.calendarquiet.core.MINUTE
import dev.calendarquiet.core.TimerKind
import dev.calendarquiet.core.TimerState
import dev.calendarquiet.core.FocusProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TimerCoordinator(private val context: Context) {
    val store = TimerStore(context)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = TimerNotifications(context)
    private val mutex = Mutex()
    private data class AlarmKey(val type: Int, val at: Long, val exact: Boolean)
    private data class PresentationKey(val state: TimerState, val notificationsEnabled: Boolean, val zone: String, val day: Long)
    private var alarmInitialized = false
    private var installedAlarm: AlarmKey? = null
    private var presented: PresentationKey? = null
    private val mutableState = MutableStateFlow(store.read())
    val state = mutableState.asStateFlow()
    private val mutableCalendarSelection = MutableStateFlow(store.calendarIds())
    val calendarSelection = mutableCalendarSelection.asStateFlow()

    fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    suspend fun refresh(forcePresentation: Boolean = false, forceAlarm: Boolean = false, restoreNotification: Boolean = false) = mutex.withLock {
        refreshLocked(forcePresentation, forceAlarm, restoreNotification)
    }

    suspend fun startDuration(kind: TimerKind, minutes: Int, rememberDuration: Boolean = true, title: String? = null) = mutex.withLock {
        refreshLocked()
        startDurationLocked(kind, minutes, rememberDuration, title)
    }

    private fun startDurationLocked(kind: TimerKind, minutes: Int, rememberDuration: Boolean = true, title: String? = null) {
        check(mutableState.value.active == null) { "A timer is already running." }
        val timer = Countdown.duration(kind, minutes, System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(), title)
        val previous = mutableState.value
        save(previous.copy(active = timer, completed = null,
            focusMinutes = if (rememberDuration && kind == TimerKind.FOCUS) minutes else previous.focusMinutes,
            breakMinutes = if (rememberDuration && kind == TimerKind.BREAK) minutes else previous.breakMinutes,
            longBreakMinutes = if (rememberDuration && kind == TimerKind.LONG_BREAK) minutes else previous.longBreakMinutes))
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

    suspend fun saveSettings(focus: Int, shortBreak: Int, longBreak: Int, every: Int) = mutex.withLock {
        require(focus in 1..180 && shortBreak in 1..180 && longBreak in 1..180 && every in 1..12)
        save(mutableState.value.copy(focusMinutes = focus, breakMinutes = shortBreak,
            longBreakMinutes = longBreak, longBreakEvery = every))
        publish()
    }

    suspend fun saveDailyGoal(goal: Int) = mutex.withLock {
        require(goal in 1..12)
        refreshLocked()
        val progress = mutableState.value.progress!!
        if (progress.dailyGoal != goal) {
            saveProgressOnly(mutableState.value.copy(progress = progress.copy(dailyGoal = goal)))
        }
    }

    suspend fun pauseOrResume(id: String) = mutex.withLock {
        refreshLocked()
        updatePauseLocked(id)
    }

    private fun updatePauseLocked(id: String) {
        val timer = mutableState.value.active?.takeIf { it.id == id && it.canPause } ?: return
        val next = if (timer.isPaused) timer.resume(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount())
            else timer.pause(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount())
        save(mutableState.value.copy(active = next))
        publish()
    }

    suspend fun extend(id: String, minutes: Int) = mutex.withLock {
        refreshLocked()
        val timer = mutableState.value.active?.takeIf { it.id == id } ?: return@withLock
        save(mutableState.value.copy(active = timer.extend(minutes)))
        publish()
    }

    suspend fun clear(id: String? = null) = mutex.withLock {
        // Settle an already elapsed timer before interpreting a late Stop tap.
        refreshLocked()
        if (id != null && mutableState.value.active?.id != id) return@withLock
        clearLocked()
    }

    private fun clearLocked() {
        save(mutableState.value.copy(active = null, completed = null))
        notifications.clearFinished()
        publish()
    }

    // PendingIntents are tied to the exact rendered state. Repeated or old taps cannot
    // pause a replacement timer, stop a newer session, or accidentally start another one.
    suspend fun handleAction(action: String?, revision: Long) = mutex.withLock {
        refreshLocked()
        val current = mutableState.value
        if (revision != current.revision) return@withLock
        when (action) {
            TimerReceiver.PAUSE -> current.active?.takeIf { !it.isPaused }?.let { updatePauseLocked(it.id) }
            TimerReceiver.RESUME -> current.active?.takeIf { it.isPaused }?.let { updatePauseLocked(it.id) }
            TimerReceiver.STOP -> if (current.active != null) clearLocked()
            TimerReceiver.START_FOCUS -> if (current.active == null) startDurationLocked(TimerKind.FOCUS, current.focusMinutes)
            TimerReceiver.START_BREAK -> if (current.active == null) startDurationLocked(current.nextBreakKind, current.minutesFor(current.nextBreakKind))
            TimerReceiver.START_NEXT -> if (current.active == null) startDurationLocked(current.nextSessionKind, current.minutesFor(current.nextSessionKind))
        }
    }

    suspend fun selectCalendars(ids: Set<Long>?) = mutex.withLock {
        store.selectCalendars(ids)
        mutableCalendarSelection.value = ids
    }

    private fun save(state: TimerState) {
        val next = state.copy(revision = mutableState.value.revision + 1)
        store.write(next)
        mutableState.value = next
    }

    private fun saveProgressOnly(state: TimerState) {
        // Metadata must not invalidate existing timer/next-session action tokens.
        store.write(state)
        mutableState.value = state
        presented = presented?.copy(state = state)
    }

    private fun refreshLocked(forcePresentation: Boolean = false, forceAlarm: Boolean = false, restoreNotification: Boolean = false) {
        val previous = mutableState.value
        val now = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        val boot = bootCount()
        val rebased = previous.copy(active = previous.active?.rebaseWallClock(now, elapsedNow, boot),
            progress = previous.progress ?: FocusProgress.importHistory(previous.focusFinishedAt, now, java.time.ZoneId.systemDefault()))
        val next = rebased.completeDue(now, elapsedNow, boot)
        if (next != previous) {
            if (next.copy(progress = previous.progress) == previous) saveProgressOnly(next) else save(next)
        }
        publish(forcePresentation, forceAlarm, restoreNotification)
        val expired = previous.active
        if (expired != null && next.active == null) {
            val finishedAt = expired.finishTime(now, elapsedNow, boot)
            notifications.finished(expired, mutableState.value, late = now - finishedAt > 5 * MINUTE)
        }
    }

    private fun alarmIntent() = PendingIntent.getBroadcast(context, 700,
        Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun publish(forcePresentation: Boolean = false, forceAlarm: Boolean = false, restoreNotification: Boolean = false) {
        val state = mutableState.value
        val timer = state.active
        val boot = bootCount()
        val desiredAlarm = timer?.takeUnless { it.isPaused }?.let {
            val elapsed = it.usesElapsedClock(boot)
            AlarmKey(if (elapsed) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP,
                if (elapsed) it.elapsedDeadline!! else it.endsAt, alarms.canScheduleExactAlarms())
        }
        // The completion alarm changes only with its deadline/clock/access, or after
        // Android explicitly invalidates it. UI refreshes do not churn AlarmManager.
        if (!alarmInitialized || forceAlarm || desiredAlarm != installedAlarm) {
            val pending = alarmIntent()
            var effectiveAlarm = desiredAlarm
            if (desiredAlarm == null) alarms.cancel(pending)
            else try {
                if (desiredAlarm.exact) alarms.setExactAndAllowWhileIdle(desiredAlarm.type, desiredAlarm.at, pending)
                else alarms.setAndAllowWhileIdle(desiredAlarm.type, desiredAlarm.at, pending)
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(desiredAlarm.type, desiredAlarm.at, pending)
                effectiveAlarm = desiredAlarm.copy(exact = false)
            }
            installedAlarm = effectiveAlarm
            alarmInitialized = true
        }
        val zone = java.time.ZoneId.systemDefault()
        val presentation = PresentationKey(state, notifications.enabled(), zone.id,
            java.time.LocalDate.now(zone).toEpochDay())
        if (forcePresentation || presentation != presented ||
            (restoreNotification && timer != null && presentation.notificationsEnabled && !notifications.hasRunning())) {
            if (timer == null) notifications.clearRunning() else notifications.running(timer, boot, state.revision)
            TimerWidget.render(context, state, boot)
            presented = presentation
        }
    }
}
