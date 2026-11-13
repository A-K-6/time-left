package dev.calendarquiet.data

import dev.calendarquiet.core.CalendarEvent
import dev.calendarquiet.core.CalendarSource
import dev.calendarquiet.core.CalendarSnapshots
import dev.calendarquiet.core.CalendarWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** UI-only snapshots. Automation always reads the provider directly. */
class UiCalendars(private val provider: DeviceCalendars) {
    private val snapshots = CalendarSnapshots()
    private val reads = Mutex()

    fun invalidate() = snapshots.invalidate()
    fun peekSources(): List<CalendarSource>? = if (provider.hasPermission()) snapshots.sources() else null
    fun peekEvents(window: CalendarWindow): List<CalendarEvent>? = if (provider.hasPermission()) snapshots.events(window) else null

    suspend fun sources(): List<CalendarSource> = reads.withLock { sourcesLocked() }

    private suspend fun sourcesLocked(): List<CalendarSource> {
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!provider.hasPermission()) { invalidate(); return emptyList() }
            snapshots.sources()?.let { return it }
            val token = snapshots.generation()
            val value = withContext(Dispatchers.IO) { provider.calendars() }
            if (snapshots.storeSources(token, value)) return value
        }
    }

    suspend fun events(window: CalendarWindow): List<CalendarEvent> = reads.withLock { eventsLocked(window) }

    private suspend fun eventsLocked(window: CalendarWindow): List<CalendarEvent> {
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!provider.hasPermission()) { invalidate(); return emptyList() }
            snapshots.events(window)?.let { return it }
            val token = snapshots.generation()
            val sources = sourcesLocked()
            val value = withContext(Dispatchers.IO) { provider.events(window.from, window.until, window.selectedIds, sources) }
            if (snapshots.storeEvents(token, window, value)) return value
        }
    }
}
