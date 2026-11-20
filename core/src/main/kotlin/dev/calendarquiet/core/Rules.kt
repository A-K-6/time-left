package dev.calendarquiet.core

import java.util.UUID

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE
const val MAX_SNOOZE_MINUTES = 60

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val marker: String,
    val calendarIds: Set<Long> = emptySet(),
    val leadMinutes: Int = 0,
    val reminderText: String = "",
    val reminderLeadMinutes: Int = 10,
    val hiddenPackages: Set<String> = emptySet(),
    val hideMinutes: Int? = null,
    val enabled: Boolean = true,
) {
    val hasActions: Boolean get() = reminderText.isNotBlank() || hiddenPackages.isNotEmpty()

    fun validationError(allowDisabledWithoutActions: Boolean = false): String? = when {
        name.isBlank() -> "Give the rule a name."
        !Regex("#[\\p{L}\\p{N}_-]+").matches(marker) -> "Use a marker like #focus (letters, numbers, _ or -)."
        leadMinutes !in 0..1440 || reminderLeadMinutes !in 0..1440 -> "Offsets must be between 0 and 1440 minutes."
        hideMinutes != null && hideMinutes !in 1..MAX_SNOOZE_MINUTES ->
            "Hide duration must be between 1 and $MAX_SNOOZE_MINUTES minutes."
        !hasActions && (enabled || !allowDisabledWithoutActions) -> "Set a reminder or choose apps to snooze."
        else -> null
    }
}

data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean = false,
    val declined: Boolean = false,
    val busy: Boolean = true,
    val displayColor: Int? = null,
)

data class Session(val rule: Rule, val event: CalendarEvent) {
    val start: Long get() = event.start - rule.leadMinutes * MINUTE
    val end: Long get() = event.end
    val reminderAt: Long get() = event.start - rule.reminderLeadMinutes * MINUTE
    val reminderKey: String get() = "${rule.id}:${event.calendarId}:${event.id}:${event.start}"
    val requestedSnoozeEnd: Long get() = rule.hideMinutes?.let { minOf(end, start + it * MINUTE) } ?: end
}

data class Snapshot(
    val snoozeUntil: Map<String, Long>,
    val reminders: List<Session>,
    val active: List<Session>,
    val nextWake: Long,
)

object RuleEngine {
    private data class SnoozeWindow(val start: Long, val end: Long) {
        val cappedEnd: Long get() = minOf(end, start + MAX_SNOOZE_MINUTES * MINUTE)
    }

    fun matches(rule: Rule, event: CalendarEvent, includeDisabled: Boolean = false): Boolean {
        if ((!rule.enabled && !includeDisabled) || event.allDay || event.declined || event.end <= event.start) return false
        if (rule.calendarIds.isNotEmpty() && event.calendarId !in rule.calendarIds) return false
        // Whole markers only: #focus must not match #focused or #focus-work.
        val expression = Regex("(?<![\\p{L}\\p{N}_#-])${Regex.escape(rule.marker)}(?![\\p{L}\\p{N}_-])", RegexOption.IGNORE_CASE)
        return expression.containsMatchIn(event.title) || expression.containsMatchIn(event.description)
    }

    fun sessions(rules: List<Rule>, events: List<CalendarEvent>): List<Session> =
        rules.flatMap { rule -> events.filter { matches(rule, it) }.map { Session(rule, it) } }
            .sortedBy { it.start }

    fun snapshot(sessions: List<Session>, now: Long, delivered: Set<String> = emptySet()): Snapshot {
        val active = sessions.filter { now >= it.start && now < it.end }
        val snooze = snoozeDeadlines(sessions, now)
        val reminders = sessions.filter {
            it.rule.reminderText.isNotBlank() && it.reminderKey !in delivered &&
                now >= it.reminderAt && now < it.reminderAt + 5 * MINUTE && now < it.end
        }
        val boundaries = sessions.flatMap { session ->
            buildList {
                add(session.start); add(session.end)
                if (session.rule.reminderText.isNotBlank() && session.reminderKey !in delivered) add(session.reminderAt)
            }
        } + snooze.values
        return Snapshot(snooze, reminders, active,
            minOf(boundaries.filter { it > now }.minOrNull() ?: Long.MAX_VALUE, now + 6 * HOUR))
    }

    private fun snoozeDeadlines(sessions: List<Session>, now: Long): Map<String, Long> {
        val latestByPackage = mutableMapOf<String, SnoozeWindow>()
        // Keep earlier intervals in the same continuous window, even after those rules end.
        for (session in sessions.filter { it.start <= now }.sortedBy { it.start }) {
            val end = session.requestedSnoozeEnd
            for (pkg in session.rule.hiddenPackages) {
                val previous = latestByPackage[pkg]
                latestByPackage[pkg] = if (previous != null && session.start <= previous.end) {
                    previous.copy(end = maxOf(previous.end, end))
                } else {
                    SnoozeWindow(session.start, end)
                }
            }
        }
        return latestByPackage.mapValues { it.value.cappedEnd }.filterValues { it > now }
    }
}
