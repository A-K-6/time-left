package dev.calendarquiet.core

import java.util.UUID

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE

enum class SoundMode { UNCHANGED, VIBRATE, SILENT }

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val marker: String,
    val calendarIds: Set<Long> = emptySet(),
    val soundMode: SoundMode = SoundMode.UNCHANGED,
    val leadMinutes: Int = 0,
    val reminderText: String = "",
    val reminderLeadMinutes: Int = 10,
    val hiddenPackages: Set<String> = emptySet(),
    val hideMinutes: Int? = null,
    val enabled: Boolean = true,
) {
    fun validationError(): String? = when {
        name.isBlank() -> "Give the rule a name."
        !Regex("#[\\p{L}\\p{N}_-]+").matches(marker) -> "Use a marker like #focus (letters, numbers, _ or -)."
        leadMinutes !in 0..1440 || reminderLeadMinutes !in 0..1440 -> "Offsets must be between 0 and 1440 minutes."
        hideMinutes != null && hideMinutes !in 1..1440 -> "Hide duration must be between 1 and 1440 minutes."
        soundMode == SoundMode.UNCHANGED && reminderText.isBlank() && hiddenPackages.isEmpty() -> "Choose a sound mode, a reminder, or apps to snooze."
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
    val hideEnd: Long get() = rule.hideMinutes?.let { minOf(end, start + it * MINUTE) } ?: end
}

data class Snapshot(
    val soundMode: SoundMode,
    val soundUntil: Long,
    val snoozeUntil: Map<String, Long>,
    val reminders: List<Session>,
    val active: List<Session>,
    val nextWake: Long,
)

object RuleEngine {
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
        val mode = active.maxByOrNull { it.rule.soundMode.ordinal }?.rule?.soundMode ?: SoundMode.UNCHANGED
        val snooze = mutableMapOf<String, Long>()
        active.filter { now < it.hideEnd }.forEach { session ->
            session.rule.hiddenPackages.forEach { pkg -> snooze[pkg] = maxOf(snooze[pkg] ?: 0, session.hideEnd) }
        }
        val reminders = sessions.filter {
            it.rule.reminderText.isNotBlank() && it.reminderKey !in delivered &&
                now >= it.reminderAt && now < it.reminderAt + 5 * MINUTE && now < it.end
        }
        val boundaries = sessions.flatMap { session ->
            buildList {
                add(session.start); add(session.end)
                if (session.rule.hiddenPackages.isNotEmpty()) add(session.hideEnd)
                if (session.rule.reminderText.isNotBlank() && session.reminderKey !in delivered) add(session.reminderAt)
            }
        }
        return Snapshot(mode, soundClusterEnd(sessions, now), snooze, reminders, active,
            minOf(boundaries.filter { it > now }.minOrNull() ?: Long.MAX_VALUE, now + 6 * HOUR))
    }

    // A manual override lasts through the connected set of overlapping sound windows.
    fun soundClusterEnd(sessions: List<Session>, now: Long): Long {
        val sound = sessions.filter { it.rule.soundMode != SoundMode.UNCHANGED }.sortedBy { it.start }
        var end = sound.filter { now >= it.start && now < it.end }.maxOfOrNull { it.end } ?: return 0
        for (session in sound) if (session.start <= end && session.end > now) end = maxOf(end, session.end)
        return end
    }
}
