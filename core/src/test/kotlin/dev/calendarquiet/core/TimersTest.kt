package dev.calendarquiet.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimersTest {
    private val now = Instant.parse("2026-10-29T12:00:00Z").toEpochMilli()
    private fun event(id: Long, start: Long = now, end: Long = now + HOUR) =
        CalendarEvent(id, 7, "Event $id", "", start, end)

    @Test fun focusUsesElapsedTimeDespiteManualClockChange() {
        val timer = Countdown.duration(TimerKind.FOCUS, 25, now, 1000, 4)
        assertEquals(24 * MINUTE, timer.remaining(now + HOUR, 1000 + MINUTE, 4))
        assertEquals(now + HOUR + 24 * MINUTE, timer.finishTime(now + HOUR, 1000 + MINUTE, 4))
    }

    @Test fun rebootFallsBackToSavedDeadline() {
        val timer = Countdown.duration(TimerKind.FOCUS, 25, now, 1000, 4)
        assertEquals(20 * MINUTE, timer.remaining(now + 5 * MINUTE, 10, 5))
        assertEquals(20 * MINUTE, timer.remaining(now + 5 * MINUTE, 10, -1))
    }

    @Test fun eventCountdownAlwaysFollowsTheWallDeadline() {
        val timer = Countdown(kind = TimerKind.EVENT, title = "Meeting", startedAt = now, endsAt = now + HOUR)
        assertEquals(15 * MINUTE, timer.remaining(now + 45 * MINUTE, 1, 4))
    }

    @Test fun clockChangeCanBeSavedBeforeARebootWithoutChangingRemainingDuration() {
        val timer = Countdown.duration(TimerKind.FOCUS, 25, now, 1000, 4)
        listOf(now + HOUR, now - HOUR).forEach { shiftedNow ->
            val saved = timer.rebaseWallClock(shiftedNow, 1000 + MINUTE, 4)
            assertEquals(24 * MINUTE, saved.remaining(shiftedNow, 0, 5))
            assertEquals(25 * MINUTE, saved.endsAt - saved.startedAt)
        }
    }

    @Test fun completionCountsFocusExactlyOnceAndNeverStartsABreak() {
        val timer = Countdown.duration(TimerKind.FOCUS, 25, now, 1000, 4)
        val state = TimerState(active = timer).completeDue(now + 25 * MINUTE, 1000 + 25 * MINUTE, 4)
        assertNull(state.active)
        assertEquals(timer, state.completed)
        assertEquals(listOf(now + 25 * MINUTE), state.focusFinishedAt)
        assertEquals(state, state.completeDue(now + HOUR, 1000 + HOUR, 4))
    }

    @Test fun completionNotDueAndBreaksDoNotIncrementFocusCount() {
        val timer = Countdown.duration(TimerKind.BREAK, 5, now, 0, 4)
        val initial = TimerState(active = timer)
        assertEquals(initial, initial.completeDue(now + MINUTE, MINUTE, 4))
        assertTrue(initial.completeDue(now + 5 * MINUTE, 5 * MINUTE, 4).focusFinishedAt.isEmpty())
    }

    @Test fun delayedCompletionIsAttributedToItsFinishDayInTheDeviceZone() {
        val midnight = Instant.parse("2026-10-29T20:30:00Z").toEpochMilli()
        val timer = Countdown.duration(TimerKind.FOCUS, 1, midnight - 2 * MINUTE, 0, 4)
        val state = TimerState(active = timer).completeDue(midnight + MINUTE, 3 * MINUTE, 4)
        assertEquals(0, state.completedToday(midnight + MINUTE, ZoneId.of("Asia/Tehran")))
        assertEquals(1, state.completedToday(midnight - MINUTE, ZoneId.of("Asia/Tehran")))
    }

    @Test fun suggestionsExcludeFreeDeclinedAllDayAndExpiredEvents() {
        val next = event(6, now + HOUR, now + 2 * HOUR)
        val suggestion = TimerSuggestions.from(listOf(event(1).copy(allDay = true), event(2).copy(declined = true),
            event(3).copy(busy = false), event(4, now - HOUR, now), event(5, now + HOUR, now), next), now)
        assertTrue(suggestion.current.isEmpty())
        assertEquals(next, suggestion.next)
        assertEquals(now + HOUR, suggestion.freeUntil)
    }

    @Test fun overlappingEventsNeverOfferFreeTimeAndExactEndIsFree() {
        val first = event(1, now - HOUR, now + MINUTE)
        val second = event(2, now, now + HOUR)
        val next = event(3, now + 2 * HOUR, now + 3 * HOUR)
        val suggestion = TimerSuggestions.from(listOf(second, first, next), now)
        assertEquals(listOf(first, second), suggestion.current)
        assertNull(suggestion.freeUntil)
        assertEquals(next.start, TimerSuggestions.from(listOf(first, second, next), second.end).freeUntil)
    }

    @Test fun calendarSelectionDistinguishesAllNoneAndSelected() {
        val events = listOf(event(1), event(2).copy(calendarId = 8))
        assertEquals(2, TimerSuggestions.from(events, now).current.size)
        assertTrue(TimerSuggestions.from(events, now, emptySet()).current.isEmpty())
        assertEquals(listOf(events[1]), TimerSuggestions.from(events, now, setOf(8)).current)
    }

    @Test fun countdownRoundsUpAndClampsAtZero() {
        assertEquals("00:01", formatCountdown(1))
        assertEquals("00:00", formatCountdown(-MINUTE))
        assertEquals("1:01:01", formatCountdown(HOUR + MINUTE + 1000))
    }

    @Test(expected = IllegalArgumentException::class) fun durationRejectsZero() {
        Countdown.duration(TimerKind.FOCUS, 0, now, 0, 4)
    }
}
