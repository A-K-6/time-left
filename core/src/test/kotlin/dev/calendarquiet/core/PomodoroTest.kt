package dev.calendarquiet.core

import org.junit.Assert.*
import org.junit.Test

class PomodoroTest {
    private val now = 1_800_000_000_000L
    private fun focus() = Countdown.duration(TimerKind.FOCUS, 25, now, 1000, 4)

    @Test fun pauseFreezesTimeAcrossClockChangesAndReboot() {
        val paused = focus().pause(now + MINUTE, 1000 + MINUTE, 4)
        assertTrue(paused.isPaused)
        assertEquals(24 * MINUTE, paused.remaining(now + HOUR, 1000 + HOUR, 4))
        assertEquals(24 * MINUTE, paused.remaining(now - HOUR, 10, 5))
        assertEquals(paused, paused.rebaseWallClock(now + HOUR, 1000 + HOUR, 4))
    }

    @Test fun resumePreservesIdentityAndOriginalDurationWithoutCountingPause() {
        val original = focus()
        val paused = original.pause(now + MINUTE, 1000 + MINUTE, 4)
        val resumed = paused.resume(now + HOUR, 10, 5)
        assertEquals(original.id, resumed.id)
        assertFalse(resumed.isPaused)
        assertEquals(25 * MINUTE, resumed.durationMillis)
        assertEquals(23 * MINUTE, resumed.remaining(now + HOUR + MINUTE, 10 + MINUTE, 5))
        assertEquals(now + HOUR + 24 * MINUTE, resumed.endsAt)
    }

    @Test fun pauseAndResumeAreIdempotent() {
        val original = focus()
        assertEquals(original, original.resume(now, 1000, 4))
        val paused = original.pause(now + MINUTE, 1000 + MINUTE, 4)
        assertEquals(paused, paused.pause(now + HOUR, 1000 + HOUR, 4))
    }

    @Test fun pausedSessionNeverCompletesOrCountsFocus() {
        val paused = focus().pause(now + MINUTE, 1000 + MINUTE, 4)
        val state = TimerState(active = paused)
        assertEquals(state, state.completeDue(now + 3 * HOUR, 1000 + 3 * HOUR, 4))
        assertEquals(0, state.completedToday(now))
    }

    @Test fun resumedSessionCompletesExactlyOnceAtItsNewDeadline() {
        val resumed = focus().pause(now + MINUTE, 1000 + MINUTE, 4).resume(now + HOUR, 10, 5)
        val state = TimerState(active = resumed)
        val completed = state.completeDue(resumed.endsAt, 10 + 24 * MINUTE, 5)
        assertNull(completed.active)
        assertEquals(listOf(resumed.endsAt), completed.focusFinishedAt)
        assertEquals(completed, completed.completeDue(now + 2 * HOUR, 10 + HOUR, 5))
    }

    @Test fun extensionKeepsMonotonicTimeAndWorksWhilePaused() {
        val extended = focus().extend(1)
        assertEquals(26 * MINUTE, extended.durationMillis)
        assertEquals(25 * MINUTE, extended.remaining(now + HOUR, 1000 + MINUTE, 4))
        val paused = focus().pause(now + MINUTE, 1000 + MINUTE, 4).extend(1)
        assertEquals(25 * MINUTE, paused.pausedRemaining)
        assertEquals(26 * MINUTE, paused.durationMillis)
        assertEquals(25 * MINUTE, paused.resume(now + HOUR, 5, 6).remaining(now + HOUR, 5, 6))
    }

    @Test(expected = IllegalArgumentException::class) fun cannotPauseCalendarDeadline() {
        Countdown(kind = TimerKind.EVENT, title = "Event", startedAt = now, endsAt = now + HOUR).pause(now, 1000, 4)
    }

    @Test(expected = IllegalArgumentException::class) fun cannotExtendFreeTimeIntoNextEvent() {
        Countdown(kind = TimerKind.FREE_TIME, title = "Gap", startedAt = now, endsAt = now + HOUR).extend(1)
    }

    @Test(expected = IllegalArgumentException::class) fun cannotPauseAlreadyExpiredTimer() {
        focus().pause(now + HOUR, 1000 + HOUR, 4)
    }

    @Test(expected = IllegalArgumentException::class) fun extensionRespectsMaximumSessionLength() {
        Countdown.duration(TimerKind.FOCUS, 180, now, 1000, 4).extend(1)
    }

    @Test fun fourCompletedFocusesOfferLongBreakWithoutStartingIt() {
        var state = TimerState()
        repeat(4) { index ->
            val timer = focus()
            state = state.copy(active = timer).completeDue(timer.endsAt, 1000 + 25 * MINUTE, 4)
            assertEquals(index + 1, state.focusesInCycle)
            assertEquals(if (index == 3) TimerKind.LONG_BREAK else TimerKind.BREAK, state.nextSessionKind)
            assertNull(state.active)
            if (index < 3) {
                val rest = Countdown.duration(TimerKind.BREAK, 5, now, 1000, 4)
                state = state.copy(active = rest).completeDue(rest.endsAt, 1000 + 5 * MINUTE, 4)
                assertEquals(TimerKind.FOCUS, state.nextSessionKind)
            }
        }
        assertEquals(4, state.focusFinishedAt.size)
        assertEquals(15, state.minutesFor(state.nextSessionKind))
        val rest = Countdown.duration(TimerKind.LONG_BREAK, 15, now, 1000, 4)
        state = state.copy(active = rest).completeDue(rest.endsAt, 1000 + 15 * MINUTE, 4)
        assertEquals(0, state.focusesInCycle)
        assertEquals(4, state.focusFinishedAt.size)
        assertEquals(TimerKind.FOCUS, state.nextSessionKind)
    }

    @Test fun skippedLongBreakStartsNextCycleAtOneAndCustomIntervalWorks() {
        val timer = focus()
        val state = TimerState(active = timer, longBreakEvery = 2, focusesInCycle = 2)
            .completeDue(timer.endsAt, 1000 + 25 * MINUTE, 4)
        assertEquals(1, state.focusesInCycle)
        assertEquals(TimerKind.BREAK, state.nextSessionKind)
        val everyFocus = TimerState(active = timer, longBreakEvery = 1).completeDue(timer.endsAt, 1000 + 25 * MINUTE, 4)
        assertEquals(TimerKind.LONG_BREAK, everyFocus.nextSessionKind)
    }

    @Test fun calendarCompletionDoesNotAdvancePomodoroCycle() {
        val timer = Countdown(kind = TimerKind.EVENT, title = "Event", startedAt = now, endsAt = now + HOUR)
        val state = TimerState(active = timer, focusesInCycle = 2).completeDue(timer.endsAt, 1000 + HOUR, 4)
        assertEquals(2, state.focusesInCycle)
        assertTrue(state.focusFinishedAt.isEmpty())
    }

    @Test fun customTitleIsBoundedAndWhitespaceFallsBackToPhaseName() {
        assertEquals("My task", Countdown.duration(TimerKind.FOCUS, 25, now, 0, 4, " My task ").title)
        assertEquals("Long break", Countdown.duration(TimerKind.LONG_BREAK, 15, now, 0, 4, " ").title)
        assertEquals(120, Countdown.duration(TimerKind.FOCUS, 25, now, 0, 4, "a".repeat(200)).title.length)
    }
}
