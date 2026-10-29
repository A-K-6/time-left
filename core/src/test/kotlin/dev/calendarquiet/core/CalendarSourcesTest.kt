package dev.calendarquiet.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSourcesTest {
    private val local = CalendarSource(1, "Personal", "phone", "LOCAL", true, true)
    private val google = CalendarSource(2, "Personal", "person@example.invalid", "com.google", false, true)
    private val disabled = CalendarSource(3, "Old", "person@example.invalid", "com.google", true, false)
    private val other = CalendarSource(4, "Work", "work@example.invalid", "exchange", true, true)
    private val sources = listOf(local, google, disabled, other)

    @Test fun defaultFollowsDeviceVisibilityAndSync() {
        assertEquals(listOf(local, other), selectCalendarSources(sources, null))
    }

    @Test fun choosingHiddenGoogleDoesNotIncludeLocalWithSameName() {
        assertEquals(listOf(google), selectCalendarSources(sources, setOf(google.id)))
    }

    @Test fun explicitSelectionSurvivesAnotherCalendarAppsVisibilityChange() {
        assertEquals(listOf(google.copy(visible = true)), selectCalendarSources(
            sources.map { if (it.id == google.id) it.copy(visible = true) else it }, setOf(google.id)))
        assertEquals(listOf(google), selectCalendarSources(sources, setOf(google.id)))
    }

    @Test fun deselectingAllNeverFallsBackToLocalCalendars() {
        assertEquals(emptyList<CalendarSource>(), selectCalendarSources(sources, emptySet()))
    }

    @Test fun removedSelectionNeverFallsBackToAnotherAccount() {
        assertEquals(emptyList<CalendarSource>(), selectCalendarSources(sources, setOf(999)))
        assertEquals(listOf(google), selectCalendarSources(sources, setOf(999, google.id)))
    }

    @Test fun explicitDisabledCalendarCanShowItsStoredEvents() {
        assertEquals(listOf(disabled), selectCalendarSources(sources, setOf(disabled.id)))
    }
}
