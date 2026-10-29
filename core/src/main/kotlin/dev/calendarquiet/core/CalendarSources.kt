package dev.calendarquiet.core

data class CalendarSource(
    val id: Long,
    val name: String,
    val account: String,
    val accountType: String,
    val visible: Boolean,
    val syncEnabled: Boolean,
) {
    val includedByDefault: Boolean get() = visible && syncEnabled
    val isGoogle: Boolean get() = accountType == "com.google"
    val sourceLabel: String get() = when (accountType) {
        "com.google" -> "Google"
        "LOCAL" -> "On this device"
        "" -> "Other account"
        else -> accountType
    }
    val description: String get() = listOf(name, sourceLabel, account).filter { it.isNotBlank() }.joinToString(" · ")
}

/** Null follows device visibility; an explicit selection belongs to this app, including hidden sources. */
fun selectCalendarSources(calendars: List<CalendarSource>, selectedIds: Set<Long>?): List<CalendarSource> =
    calendars.filter { if (selectedIds == null) it.includedByDefault else it.id in selectedIds }
