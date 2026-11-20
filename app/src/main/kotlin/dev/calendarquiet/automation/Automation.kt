package dev.calendarquiet.automation

import android.content.Context
import dev.calendarquiet.core.RuleEngine
import dev.calendarquiet.core.Session
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
    val legacySoundCleanupNeeded: Boolean = false,
)

class Automation(private val context: Context) {
    val store = Store(context)
    val calendars = DeviceCalendars(context)
    private val legacySound = LegacySoundCleanup(context, store)
    private val scheduler = Scheduler(context)
    private val reminders = ReminderNotifications(context)
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow(AutomationStatus())
    val status = mutableStatus.asStateFlow()
    private var pausedSettled = false
    // Derived from current calendar data. Never reuse a prior process's windows.
    @Volatile private var snoozeWindows = emptyMap<String, Long>()

    internal val canRunRules: Boolean get() = store.enabled && calendars.hasPermission()

    internal fun snoozeDeadline(packageName: String): Long? =
        if (canRunRules) snoozeWindows[packageName] else null

    suspend fun reconcile(fromUi: Boolean = false) = mutex.withLock {
        // Pausing is still settled on process start, explicit changes and system
        // broadcasts. Reopening an already settled paused UI has no effects to redo.
        if (fromUi && pausedSettled && !store.enabled) return@withLock
        pausedSettled = false
        snoozeWindows = emptyMap()
        mutableStatus.value = mutableStatus.value.copy(loading = true)
        val now = System.currentTimeMillis()
        val cleanupNeeded = !legacySound.clear()
        try {
            if (!canRunRules) {
                scheduler.cancel()
                mutableStatus.value = AutomationStatus(lastRefresh = now,
                    message = if (store.enabled) "Allow calendar access to start automation." else "Automation is paused.",
                    legacySoundCleanupNeeded = cleanupNeeded)
                pausedSettled = !store.enabled && !cleanupNeeded
                return@withLock
            }
            val sessions = RuleEngine.sessions(store.rules(), calendars.events(now))
            val snapshot = RuleEngine.snapshot(sessions, now, store.delivered(now).keys)
            val message = if (snapshot.active.isEmpty()) "Ready for your next matching event." else "${snapshot.active.size} calendar rule(s) active."
            snapshot.reminders.forEach { session ->
                if (reminders.post(session)) store.markDelivered(session.reminderKey, session.end, now)
            }
            scheduler.schedule(snapshot.nextWake)
            snoozeWindows = snapshot.snoozeUntil
            QuietNotificationListener.snoozeExisting()
            mutableStatus.value = AutomationStatus(snapshot.active, sessions.filter { it.end > now }.take(30), now,
                snapshot.nextWake, message, legacySoundCleanupNeeded = cleanupNeeded)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            snoozeWindows = emptyMap()
            if (canRunRules) scheduler.schedule(now + 5 * 60_000)
            mutableStatus.value = AutomationStatus(lastRefresh = now, message = error.message ?: "Could not refresh calendar rules.",
                legacySoundCleanupNeeded = cleanupNeeded)
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        mutex.withLock {
            snoozeWindows = emptyMap()
            store.enabled = enabled
        }
        reconcile()
    }
    suspend fun updateRules(transform: (List<dev.calendarquiet.core.Rule>) -> List<dev.calendarquiet.core.Rule>) {
        mutex.withLock {
            snoozeWindows = emptyMap()
            store.saveRules(transform(store.rules()))
        }
        reconcile()
    }
}
