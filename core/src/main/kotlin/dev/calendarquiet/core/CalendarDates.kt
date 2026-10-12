package dev.calendarquiet.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

fun CalendarEvent.occursOn(date: LocalDate, zone: ZoneId): Boolean {
    // Calendar Provider stores all-day dates at UTC midnight, independent of the phone's zone.
    val eventZone = if (allDay) ZoneOffset.UTC else zone
    val dayStart = date.atStartOfDay(eventZone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(eventZone).toInstant().toEpochMilli()
    return start < dayEnd && end > dayStart
}
