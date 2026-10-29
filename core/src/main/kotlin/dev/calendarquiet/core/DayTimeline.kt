package dev.calendarquiet.core

import java.time.LocalDate
import java.time.ZoneId

data class DayEventPlacement(
    val event: CalendarEvent,
    val startMinute: Float,
    val endMinute: Float,
    val visualEndMinute: Float,
    val column: Int,
    val columnCount: Int,
)

data class DayTimeline(val start: Long, val end: Long, val events: List<DayEventPlacement>) {
    val minutes: Float get() = (end - start).toFloat() / MINUTE
}

fun dayTimeline(events: List<CalendarEvent>, date: LocalDate, zone: ZoneId, minVisualMinutes: Float = 20f): DayTimeline {
    require(minVisualMinutes > 0)
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val blocks = events.filter { !it.allDay && it.end > it.start && it.start < end && it.end > start }
        .map { event ->
            val top = (maxOf(start, event.start) - start).toFloat() / MINUTE
            val bottom = (minOf(end, event.end) - start).toFloat() / MINUTE
            DayEventPlacement(event, top, bottom, maxOf(bottom, top + minVisualMinutes), 0, 1)
        }.sortedWith(compareBy<DayEventPlacement> { it.startMinute }.thenByDescending { it.endMinute }.thenBy { it.event.id })

    val placed = mutableListOf<DayEventPlacement>()
    var cluster = mutableListOf<DayEventPlacement>()
    var clusterEnd = -1f
    fun finishCluster() {
        val columnEnds = mutableListOf<Float>()
        val assigned = cluster.map { block ->
            val available = columnEnds.indexOfFirst { it <= block.startMinute }
            val column = if (available < 0) columnEnds.size.also { columnEnds.add(block.visualEndMinute) }
                else available.also { columnEnds[it] = block.visualEndMinute }
            block.copy(column = column)
        }
        placed += assigned.map { it.copy(columnCount = columnEnds.size) }
        cluster = mutableListOf()
    }
    for (block in blocks) {
        if (cluster.isNotEmpty() && block.startMinute >= clusterEnd) finishCluster()
        if (cluster.isEmpty()) clusterEnd = block.visualEndMinute else clusterEnd = maxOf(clusterEnd, block.visualEndMinute)
        cluster += block
    }
    if (cluster.isNotEmpty()) finishCluster()
    return DayTimeline(start, end, placed)
}
