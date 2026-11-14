package dev.calendarquiet.core

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.LocalDate

class FocusProgressTest {
    private val zone = ZoneId.of("UTC")
    private val now = 1_800_000_000_000L

    @Test fun dailyHistorySurvivesRecentTimestampPruning() {
        val oldDay = now - 40 * 24 * HOUR
        val timer = Countdown.duration(TimerKind.FOCUS, 25, now - 25 * MINUTE, 0, 4)
        val completed = TimerState(active = timer, focusFinishedAt = listOf(oldDay))
            .completeDue(now, 25 * MINUTE, 4, zone)
        assertEquals(1, completed.completedToday(oldDay, zone))
    }

    @Test fun completionAwardsOnceAndBreaksDoNotEarnProgress() {
        val timer = Countdown.duration(TimerKind.FOCUS, 1, now - MINUTE, 0, 4)
        val state = TimerState(active = timer).completeDue(now, MINUTE, 4, zone)
        assertEquals(10L, state.progress!!.summary(LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone)).xp)
        assertEquals(state, state.completeDue(now + MINUTE, 2 * MINUTE, 4, zone))
        val rest = Countdown.duration(TimerKind.BREAK, 1, now, MINUTE, 4)
        assertEquals(state.progress, state.copy(active = rest).completeDue(now + MINUTE, 2 * MINUTE, 4, zone).progress)
    }

    @Test fun pausedAndUnfinishedSessionsEarnNothing() {
        val timer = Countdown.duration(TimerKind.FOCUS, 1, now, 0, 4)
        assertNull(TimerState(active = timer).completeDue(now + 30_000, 30_000, 4, zone).progress)
        assertNull(TimerState(active = timer.pause(now + 30_000, 30_000, 4)).completeDue(now + HOUR, HOUR, 4, zone).progress)
    }

    @Test fun streakRemainsAvailableThroughTodayAndBestSurvivesAGap() {
        val today = LocalDate.of(2026, 11, 14)
        val p = FocusProgress(today.minusDays(10), mapOf(today.minusDays(3) to 1,
            today.minusDays(2) to 3, today.minusDays(1) to 1))
        assertEquals(3, p.summary(today).currentStreak)
        assertEquals(0, p.summary(today.plusDays(1)).currentStreak)
        assertEquals(3, p.summary(today.plusDays(1)).bestStreak)
        assertEquals(5L, p.summary(today).totalFocuses)
    }

    @Test fun changingGoalNeverRewritesXpOrStreaks() {
        val today = LocalDate.of(2026, 11, 14)
        val p = FocusProgress(today, mapOf(today to 10))
        val a = p.summary(today)
        assertEquals(a, p.copy(dailyGoal = 1).summary(today))
        assertEquals(2L, a.level)
        assertEquals(0, a.levelXp)
    }

    @Test fun migrationUsesOnlyKnownCompletionsAndKeepsHistoricalDays() {
        val imported = FocusProgress.importHistory(listOf(now, now - 40 * 24 * HOUR, now + HOUR), now, zone)
        val today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        assertEquals(2L, imported.summary(today).totalFocuses)
        assertEquals(today, imported.trackedFrom)
    }

    @Test fun completionDateIsAnchoredAndTravelDoesNotRemoveEarnedXp() {
        val midnightUtc = java.time.Instant.parse("2026-11-14T00:30:00Z").toEpochMilli()
        val p = FocusProgress(LocalDate.of(2026, 11, 13)).record(midnightUtc, ZoneId.of("America/Los_Angeles"))
        assertEquals(1, p.days[LocalDate.of(2026, 11, 13)])
        assertEquals(10L, p.summary(LocalDate.of(2026, 11, 12)).xp)
        assertEquals(0, p.summary(LocalDate.of(2026, 11, 12)).currentStreak)
        assertEquals(0, p.summary(LocalDate.of(2026, 11, 12)).todayFocuses)
        assertEquals(10L, p.summary(LocalDate.of(2026, 11, 13)).xp)
    }

    @Test fun oversizedHistoricalBucketCannotBreakTimerCompletion() {
        val day = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        val timer = Countdown.duration(TimerKind.FOCUS, 1, now - MINUTE, 0, 4)
        val state = TimerState(active = timer, progress = FocusProgress(day, mapOf(day to Int.MAX_VALUE)))
            .completeDue(now, MINUTE, 4, zone)
        assertNull(state.active)
        assertEquals(Int.MAX_VALUE, state.progress!!.days[day])
    }
}
