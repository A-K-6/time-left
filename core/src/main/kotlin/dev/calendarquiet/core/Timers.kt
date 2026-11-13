package dev.calendarquiet.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

enum class TimerKind { FOCUS, BREAK, EVENT, FREE_TIME, LONG_BREAK }

data class Countdown(
    val id: String = UUID.randomUUID().toString(),
    val kind: TimerKind,
    val title: String,
    val startedAt: Long,
    val endsAt: Long,
    val elapsedDeadline: Long? = null,
    val bootCount: Int = -1,
    val pausedRemaining: Long? = null,
) {
    init {
        require(endsAt > startedAt) { "The end time must be in the future." }
        require(pausedRemaining == null || (canPause && pausedRemaining > 0 && pausedRemaining <= endsAt - startedAt))
    }

    val isPaused: Boolean get() = pausedRemaining != null
    val canPause: Boolean get() = kind == TimerKind.FOCUS || kind == TimerKind.BREAK || kind == TimerKind.LONG_BREAK
    val durationMillis: Long get() = endsAt - startedAt

    fun pause(now: Long, elapsedNow: Long, boot: Int): Countdown {
        require(canPause) { "Calendar deadlines cannot be paused." }
        if (isPaused) return this
        val left = remaining(now, elapsedNow, boot)
        require(left > 0) { "This timer has finished." }
        return copy(pausedRemaining = left)
    }

    fun resume(now: Long, elapsedNow: Long, boot: Int): Countdown {
        val left = pausedRemaining ?: return this
        return copy(startedAt = now + left - durationMillis, endsAt = now + left,
            elapsedDeadline = elapsedNow + left, bootCount = boot, pausedRemaining = null)
    }

    fun extend(minutes: Int): Countdown {
        require(canPause) { "Calendar deadlines cannot be extended." }
        require(minutes in 1..180 && durationMillis + minutes * MINUTE <= 180 * MINUTE) {
            "A session can be at most 180 minutes."
        }
        val extra = minutes * MINUTE
        return copy(endsAt = endsAt + extra, elapsedDeadline = elapsedDeadline?.plus(extra),
            pausedRemaining = pausedRemaining?.plus(extra))
    }

    fun usesElapsedClock(currentBoot: Int): Boolean =
        elapsedDeadline != null && currentBoot >= 0 && bootCount == currentBoot

    // Duration timers survive wall-clock corrections. After reboot, use the saved wall deadline.
    fun remaining(now: Long, elapsedNow: Long, currentBoot: Int): Long =
        pausedRemaining ?: if (usesElapsedClock(currentBoot)) elapsedDeadline!! - elapsedNow else endsAt - now

    fun finishTime(now: Long, elapsedNow: Long, currentBoot: Int): Long =
        now + remaining(now, elapsedNow, currentBoot)

    fun rebaseWallClock(now: Long, elapsedNow: Long, currentBoot: Int): Countdown {
        if (isPaused || !usesElapsedClock(currentBoot)) return this
        val shift = finishTime(now, elapsedNow, currentBoot) - endsAt
        if (shift in -999..999) return this
        return copy(startedAt = startedAt + shift, endsAt = endsAt + shift)
    }

    companion object {
        fun duration(kind: TimerKind, minutes: Int, now: Long, elapsedNow: Long, boot: Int, title: String? = null): Countdown {
            require(kind == TimerKind.FOCUS || kind == TimerKind.BREAK || kind == TimerKind.LONG_BREAK)
            require(minutes in 1..180) { "Choose between 1 and 180 minutes." }
            return Countdown(kind = kind, title = title?.trim()?.take(120)?.takeIf { it.isNotEmpty() } ?: when (kind) {
                TimerKind.FOCUS -> "Focus"
                TimerKind.LONG_BREAK -> "Long break"
                else -> "Short break"
            },
                startedAt = now, endsAt = now + minutes * MINUTE,
                elapsedDeadline = elapsedNow + minutes * MINUTE, bootCount = boot)
        }
    }
}

data class TimerState(
    val active: Countdown? = null,
    val completed: Countdown? = null,
    val focusFinishedAt: List<Long> = emptyList(),
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val longBreakEvery: Int = 4,
    val focusesInCycle: Int = 0,
    val revision: Long = 0,
) {
    val nextBreakKind: TimerKind get() = if (focusesInCycle >= longBreakEvery) TimerKind.LONG_BREAK else TimerKind.BREAK
    val nextSessionKind: TimerKind get() = if (completed?.kind == TimerKind.FOCUS) nextBreakKind else TimerKind.FOCUS
    fun minutesFor(kind: TimerKind): Int = when (kind) {
        TimerKind.FOCUS -> focusMinutes
        TimerKind.LONG_BREAK -> longBreakMinutes
        else -> breakMinutes
    }

    fun completeDue(now: Long, elapsedNow: Long, boot: Int): TimerState {
        val timer = active ?: return this
        if (timer.isPaused || timer.remaining(now, elapsedNow, boot) > 0) return this
        val finishedAt = timer.finishTime(now, elapsedNow, boot)
        return copy(active = null, completed = timer,
            focusesInCycle = when (timer.kind) {
                TimerKind.FOCUS -> if (focusesInCycle >= longBreakEvery) 1 else focusesInCycle + 1
                TimerKind.LONG_BREAK -> 0
                TimerKind.BREAK -> if (focusesInCycle >= longBreakEvery) 0 else focusesInCycle
                else -> focusesInCycle
            },
            focusFinishedAt = (focusFinishedAt + if (timer.kind == TimerKind.FOCUS) listOf(finishedAt) else emptyList())
                .filter { it >= now - 30 * 24 * HOUR }.takeLast(1000))
    }

    fun completedToday(now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return focusFinishedAt.count { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == date }
    }
}

data class TimerSuggestions(val current: List<CalendarEvent>, val next: CalendarEvent?) {
    val freeUntil: Long? get() = if (current.isEmpty()) next?.start else null

    companion object {
        // null selects all visible calendars; an empty set deliberately selects none.
        fun from(events: List<CalendarEvent>, now: Long, calendarIds: Set<Long>? = null): TimerSuggestions {
            val busy = events.filter { !it.allDay && !it.declined && it.busy && it.end > it.start &&
                it.end > now && (calendarIds == null || it.calendarId in calendarIds) }
            return TimerSuggestions(
                busy.filter { it.start <= now }.sortedBy { it.end },
                busy.filter { it.start > now }.minByOrNull { it.start })
        }
    }
}

fun formatCountdown(remainingMillis: Long): String {
    val seconds = (remainingMillis.coerceAtLeast(0) + 999) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
