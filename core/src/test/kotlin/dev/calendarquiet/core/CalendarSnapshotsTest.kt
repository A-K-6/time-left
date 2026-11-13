package dev.calendarquiet.core

import org.junit.Assert.*
import org.junit.Test

class CalendarSnapshotsTest {
    private fun window(ids: Set<Long>? = null, from: Long = 0) = CalendarWindow(from, from + 100, ids)

    @Test fun invalidationRejectsAProviderReadThatStartedBeforeTheEdit() {
        val cache = CalendarSnapshots()
        val beforeEdit = cache.generation()
        cache.invalidate()
        assertFalse(cache.storeEvents(beforeEdit, window(), emptyList()))
        assertFalse(cache.storeSources(beforeEdit, emptyList()))
        assertNull(cache.events(window()))
        assertNull(cache.sources())
        assertTrue(cache.storeEvents(cache.generation(), window(), emptyList()))
        assertEquals(emptyList<CalendarEvent>(), cache.events(window()))
    }

    @Test fun explicitEmptyAndDifferentSelectionsNeverReuseAnotherCalendar() {
        val cache = CalendarSnapshots()
        cache.storeEvents(cache.generation(), window(), emptyList())
        assertNull(cache.events(window(emptySet())))
        assertNull(cache.events(window(setOf(2))))
        cache.storeEvents(cache.generation(), window(setOf(2)), emptyList())
        assertNotNull(cache.events(window(setOf(2))))
        assertNull(cache.events(window(setOf(1))))
    }

    @Test fun monthNavigationHasBoundedRetentionAndKeepsRecentlyUsedWindows() {
        val cache = CalendarSnapshots(2)
        cache.storeEvents(cache.generation(), window(from = 0), emptyList())
        cache.storeEvents(cache.generation(), window(from = 100), emptyList())
        cache.events(window(from = 0))
        cache.storeEvents(cache.generation(), window(from = 200), emptyList())
        assertNull(cache.events(window(from = 100)))
        assertNotNull(cache.events(window(from = 0)))
        assertNotNull(cache.events(window(from = 200)))
    }

    @Test fun dayAndTimeWindowChangesRequireFreshData() {
        val cache = CalendarSnapshots()
        cache.storeEvents(cache.generation(), window(), emptyList())
        assertNull(cache.events(window(from = 1)))
        assertNull(cache.events(CalendarWindow(0, 200, null)))
        cache.invalidate()
        assertNull(cache.events(window()))
    }
}
