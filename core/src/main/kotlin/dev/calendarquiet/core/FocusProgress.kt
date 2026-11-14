package dev.calendarquiet.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Daily counts survive pruning of the timer's small recent-timestamp list. */
data class FocusProgress(
    val trackedFrom: LocalDate,
    val days: Map<LocalDate, Int> = emptyMap(),
    val dailyGoal: Int = 4,
) {
    init { require(dailyGoal in 1..12); require(days.values.all { it > 0 }) }

    fun record(finishedAt: Long, zone: ZoneId): FocusProgress {
        val day = Instant.ofEpochMilli(finishedAt).atZone(zone).toLocalDate()
        val count = ((days[day] ?: 0).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return copy(days = days + (day to count))
    }

    fun summary(today: LocalDate): FocusSummary {
        var run = 0
        var best = 0
        var previous: LocalDate? = null
        days.keys.sorted().forEach { day ->
            run = if (previous?.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        var current = 0
        var cursor = if (days.containsKey(today)) today else today.minusDays(1)
        while (days.containsKey(cursor)) { current++; cursor = cursor.minusDays(1) }
        // Earned XP/milestones remain stable when travel moves the local date back.
        return FocusSummary(days.values.sumOf { it.toLong() }, days.size,
            days[today] ?: 0, current, best)
    }

    companion object {
        fun importHistory(timestamps: List<Long>, now: Long, zone: ZoneId): FocusProgress = FocusProgress(
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
            timestamps.filter { it <= now }.groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.eachCount())
    }
}

data class FocusSummary(val totalFocuses: Long, val activeDays: Int, val todayFocuses: Int,
    val currentStreak: Int, val bestStreak: Int) {
    val xp: Long get() = totalFocuses * 10
    val level: Long get() = xp / 100 + 1
    val levelXp: Int get() = (xp % 100).toInt()
}
