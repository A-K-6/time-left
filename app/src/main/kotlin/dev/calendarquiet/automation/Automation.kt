package dev.calendarquiet.automation

import android.content.Context
import dev.calendarquiet.core.RuleEngine
import dev.calendarquiet.core.Session
import dev.calendarquiet.core.SoundMode
import dev.calendarquiet.data.DeviceCalendars
import dev.calendarquiet.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AutomationStatus(
    val active: List<Session> = emptyList(),
    val upcoming: List<Session> = emptyList(),
    val lastRefresh: Long = 0,
    val nextWake: Long = 0,
    val message: String = "Automation is paused.",
    val loading: Boolean = false,
)

class Automation(private val context: Context) {
    val store = Store(context)
    val calendars = DeviceCalendars(context)
    private val sounds = SoundController(context, store)
    private val scheduler = Scheduler(context)
    private val reminders = ReminderNotifications(context)
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow(AutomationStatus())
    val status = mutableStatus.asStateFlow()

    suspend fun reconcile() = mutex.withLock {
        mutableStatus.value = mutableStatus.value.copy(loading = true)
        val now = System.currentTimeMillis()
        try {
            if (!store.enabled || !calendars.hasPermission()) {
                store.setSnoozeWindows(emptyMap())
                scheduler.cancel()
                sounds.apply(SoundMode.UNCHANGED, 0, now)
                mutableStatus.value = AutomationStatus(lastRefresh = now,
                    message = if (store.enabled) "Allow calendar access to start automation." else "Automation is paused.")
                return@withLock
            }
            val sessions = RuleEngine.sessions(store.rules(), calendars.events(now))
            val snapshot = RuleEngine.snapshot(sessions, now, store.delivered(now).keys)
            store.setSnoozeWindows(snapshot.snoozeUntil)
            var message = if (snapshot.active.isEmpty()) "Ready for your next matching event." else "${snapshot.active.size} calendar rule(s) active."
            try { sounds.apply(snapshot.soundMode, snapshot.soundUntil, now) }
            catch (error: Exception) { message = error.message ?: "Sound mode could not be updated." }
            snapshot.reminders.forEach { session ->
                if (reminders.post(session)) store.markDelivered(session.reminderKey, session.end, now)
            }
            // Next alarm is always installed, even if one optional sound action fails.
            scheduler.schedule(snapshot.nextWake)
            QuietNotificationListener.snoozeExisting()
            mutableStatus.value = AutomationStatus(snapshot.active, sessions.filter { it.end > now }.take(30), now, snapshot.nextWake, message)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A transient calendar/provider failure must not leave an old sound mode stuck.
            store.setSnoozeWindows(emptyMap())
            runCatching { sounds.apply(SoundMode.UNCHANGED, 0, now) }
            if (store.enabled && calendars.hasPermission()) scheduler.schedule(now + 5 * 60_000)
            mutableStatus.value = AutomationStatus(lastRefresh = now, message = error.message ?: "Could not refresh calendar rules.")
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        mutex.withLock { store.enabled = enabled }
        reconcile()
    }
    suspend fun updateRules(transform: (List<dev.calendarquiet.core.Rule>) -> List<dev.calendarquiet.core.Rule>) {
        mutex.withLock { store.saveRules(transform(store.rules())) }
        reconcile()
    }
    suspend fun observeRingerChange() = mutex.withLock { runCatching { sounds.observeRingerChange() } }
    suspend fun manualSoundOverride() = mutex.withLock {
        sounds.manualOverride(store.prefs.getLong("sound_until", 0))
    }
}
