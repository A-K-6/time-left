package dev.calendarquiet.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalendarDatesTest {
    private fun event(start: String, end: String, allDay: Boolean = false) = CalendarEvent(
        1, 7, "Event", "", Instant.parse(start).toEpochMilli(), Instant.parse(end).toEpochMilli(), allDay)

    @Test fun timedEventsUseThePhoneZone() {
        val event = event("2026-10-12T22:00:00Z", "2026-10-12T23:00:00Z")
        val zone = ZoneId.of("Asia/Tehran")
        assertFalse(event.occursOn(LocalDate.parse("2026-10-12"), zone))
        assertTrue(event.occursOn(LocalDate.parse("2026-10-13"), zone))
    }

    @Test fun overnightEventsAppearOnBothDaysButNotOnTheirExclusiveEndDate() {
        val event = event("2026-10-12T23:00:00Z", "2026-10-14T00:00:00Z")
        val zone = ZoneId.of("UTC")
        assertTrue(event.occursOn(LocalDate.parse("2026-10-12"), zone))
        assertTrue(event.occursOn(LocalDate.parse("2026-10-13"), zone))
        assertFalse(event.occursOn(LocalDate.parse("2026-10-14"), zone))
    }

    @Test fun allDayDatesDoNotShiftWithTimeZones() {
        val event = event("2026-10-13T00:00:00Z", "2026-10-14T00:00:00Z", allDay = true)
        listOf("America/Los_Angeles", "Pacific/Kiritimati").forEach { name ->
            val zone = ZoneId.of(name)
            assertFalse(event.occursOn(LocalDate.parse("2026-10-12"), zone))
            assertTrue(event.occursOn(LocalDate.parse("2026-10-13"), zone))
            assertFalse(event.occursOn(LocalDate.parse("2026-10-14"), zone))
        }
    }

    @Test fun daylightSavingDayUsesLocalMidnightBoundaries() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.parse("2026-03-08")
        assertTrue(event("2026-03-09T03:30:00Z", "2026-03-09T04:00:00Z").occursOn(date, zone))
        assertFalse(event("2026-03-09T04:00:00Z", "2026-03-09T05:00:00Z").occursOn(date, zone))
    }
}
