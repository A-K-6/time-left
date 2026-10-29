package dev.calendarquiet.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayTimelineTest {
    private val date = LocalDate.of(2026, 10, 29)
    private val zone = ZoneId.of("UTC")
    private val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun event(id: Long, from: Int, until: Int) = CalendarEvent(id, 1, "Event $id", "",
        midnight + from * MINUTE, midnight + until * MINUTE)

    @Test fun positionsFollowTimeAndSeparateOverlaps() {
        val result = dayTimeline(listOf(event(1, 9 * 60, 10 * 60), event(2, 9 * 60 + 30, 11 * 60)), date, zone)
        assertEquals(1440f, result.minutes)
        assertEquals(540f, result.events[0].startMinute)
        assertEquals(600f, result.events[0].endMinute)
        assertEquals(listOf(0, 1), result.events.map { it.column })
        assertTrue(result.events.all { it.columnCount == 2 })
    }

    @Test fun touchingEventsShareAColumnAndNewClustersHaveFullWidth() {
        val result = dayTimeline(listOf(event(1, 540, 600), event(2, 600, 660), event(3, 800, 900)), date, zone)
        assertTrue(result.events.all { it.column == 0 && it.columnCount == 1 })
    }

    @Test fun overlapChainUsesSharedWidthAndReusesVacatedColumns() {
        val result = dayTimeline(listOf(event(1, 540, 600), event(2, 570, 630), event(3, 600, 660)), date, zone)
        assertEquals(listOf(0, 1, 0), result.events.map { it.column })
        assertTrue(result.events.all { it.columnCount == 2 })
    }

    @Test fun threeSimultaneousEventsRemainIndividuallyAccessible() {
        val result = dayTimeline(listOf(event(1, 540, 600), event(2, 540, 600), event(3, 540, 600)), date, zone)
        assertEquals(setOf(0, 1, 2), result.events.map { it.column }.toSet())
        assertTrue(result.events.all { it.columnCount == 3 })
    }

    @Test fun shortEventCardsDoNotVisuallyCoverEachOther() {
        val result = dayTimeline(listOf(event(1, 540, 541), event(2, 545, 546)), date, zone)
        assertEquals(560f, result.events[0].visualEndMinute)
        assertEquals(2, result.events[1].columnCount)
        assertNotEquals(result.events[0].column, result.events[1].column)
    }

    @Test fun overnightEventsAreClippedAndMidnightEndsAreExclusive() {
        val result = dayTimeline(listOf(event(1, -60, 60), event(2, 1380, 1500), event(3, -60, 0), event(4, 1440, 1500)), date, zone)
        assertEquals(listOf(1L, 2L), result.events.map { it.event.id })
        assertEquals(0f, result.events[0].startMinute)
        assertEquals(1440f, result.events[1].endMinute)
    }

    @Test fun allDayAndInvalidEventsDoNotOccupyHourlyGrid() {
        val result = dayTimeline(listOf(event(1, 0, 1440).copy(allDay = true), event(2, 600, 600), event(3, 700, 600)), date, zone)
        assertTrue(result.events.isEmpty())
    }

    @Test fun freeAndDeclinedEventsRemainVisible() {
        val result = dayTimeline(listOf(event(1, 540, 600).copy(busy = false), event(2, 700, 800).copy(declined = true)), date, zone)
        assertEquals(2, result.events.size)
    }

    @Test fun localTimePlacementUsesDeviceZone() {
        val result = dayTimeline(listOf(CalendarEvent(1, 1, "Persian event", "",
            Instant.parse("2026-10-29T06:00:00Z").toEpochMilli(), Instant.parse("2026-10-29T07:00:00Z").toEpochMilli())),
            date, ZoneId.of("Asia/Tehran"))
        assertEquals(570f, result.events.single().startMinute)
    }

    @Test fun springForwardDayHas23RealHours() {
        val result = dayTimeline(emptyList(), LocalDate.of(2026, 3, 8), ZoneId.of("America/New_York"))
        assertEquals(1380f, result.minutes)
    }

    @Test fun repeatedFallBackHourKeepsSeparateRealPositions() {
        val events = listOf("2026-11-01T05:00:00Z", "2026-11-01T06:00:00Z").mapIndexed { i, time ->
            val start = Instant.parse(time).toEpochMilli()
            CalendarEvent(i.toLong(), 1, "Event", "", start, start + 30 * MINUTE)
        }
        val result = dayTimeline(events, LocalDate.of(2026, 11, 1), ZoneId.of("America/New_York"))
        assertEquals(1500f, result.minutes)
        assertEquals(listOf(60f, 120f), result.events.map { it.startMinute })
        assertTrue(result.events.all { it.columnCount == 1 })
    }
}
