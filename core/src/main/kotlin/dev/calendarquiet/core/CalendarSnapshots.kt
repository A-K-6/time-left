package dev.calendarquiet.core

data class CalendarWindow(val from: Long, val until: Long, val selectedIds: Set<Long>?)

/** Small foreground cache. A provider change invalidates even an in-flight read. */
class CalendarSnapshots(private val maxWindows: Int = 4) {
    init { require(maxWindows > 0) }
    private var generation = 0L
    private var sources: List<CalendarSource>? = null
    private val windows = LinkedHashMap<CalendarWindow, List<CalendarEvent>>(maxWindows, .75f, true)

    @Synchronized fun generation(): Long = generation
    @Synchronized fun sources(): List<CalendarSource>? = sources
    @Synchronized fun events(window: CalendarWindow): List<CalendarEvent>? = windows[window]
    @Synchronized fun invalidate() { generation++; sources = null; windows.clear() }
    @Synchronized fun storeSources(token: Long, value: List<CalendarSource>): Boolean {
        if (token != generation) return false
        sources = value.toList()
        return true
    }
    @Synchronized fun storeEvents(token: Long, window: CalendarWindow, value: List<CalendarEvent>): Boolean {
        if (token != generation) return false
        windows[window.copy(selectedIds = window.selectedIds?.toSet())] = value.toList()
        while (windows.size > maxWindows) windows.remove(windows.keys.first())
        return true
    }
}
