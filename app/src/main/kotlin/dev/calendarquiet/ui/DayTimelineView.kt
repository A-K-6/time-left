package dev.calendarquiet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.calendarquiet.core.CalendarEvent
import dev.calendarquiet.core.HOUR
import dev.calendarquiet.core.MINUTE
import dev.calendarquiet.core.dayTimeline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun DayTimelineView(events: List<CalendarEvent>, date: LocalDate, zone: ZoneId, now: Long,
    dataReady: Boolean, modifier: Modifier = Modifier, onEvent: (CalendarEvent) -> Unit) {
    val timeline = remember(events, date, zone) { dayTimeline(events, date, zone) }
    val allDay = events.filter { it.allDay }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var positionedDay by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val clock = remember { DateTimeFormatter.ofPattern("HH:mm") }

    LaunchedEffect(date, dataReady, zone) {
        if (dataReady && positionedDay != "$date/$zone") {
            withFrameNanos { }
            val minute = if (now in timeline.start until timeline.end) (now - timeline.start).toFloat() / MINUTE
                else timeline.events.firstOrNull()?.startMinute ?: 8 * 60f
            scroll.scrollTo(with(density) { ((minute - 60).coerceAtLeast(0f) * 1.6f).dp.roundToPx() })
            positionedDay = "$date/$zone"
        }
    }
    Column(modifier) {
        if (allDay.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("All day", Modifier.width(58.dp).padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allDay, key = { "${it.calendarId}:${it.id}:${it.start}" }) { event ->
                        SuggestionChip(onClick = { onEvent(event) }, label = {
                            Text(event.title.ifBlank { "Untitled event" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp))
                        })
                    }
                }
            }
        }
        if (dataReady && events.isEmpty()) Text("No events on this day.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height((timeline.minutes * 1.6f + 48f).dp)) {
                val gutter = 52.dp
                val areaWidth = (maxWidth - gutter - 8.dp).coerceAtLeast(1.dp)
                val ticks = (0..((timeline.end - timeline.start) / HOUR).toInt()).map { timeline.start + it * HOUR }
                Canvas(Modifier.matchParentSize()) {
                    val left = if (rtl) 8.dp.toPx() else gutter.toPx()
                    val right = if (rtl) size.width - gutter.toPx() else size.width - 8.dp.toPx()
                    ticks.forEach { at ->
                        val y = (12f + (at - timeline.start).toFloat() / MINUTE * 1.6f).dp.toPx()
                        drawLine(colors.outlineVariant, Offset(left, y), Offset(right, y), 1.dp.toPx())
                    }
                }
                ticks.forEach { at ->
                    Text(Instant.ofEpochMilli(at).atZone(zone).format(clock),
                        Modifier.offset(y = (4f + (at - timeline.start).toFloat() / MINUTE * 1.6f).dp)
                            .width(gutter - 6.dp), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                timeline.events.forEach { block ->
                    val event = block.event
                    val width = ((areaWidth - 3.dp * (block.columnCount - 1)) / block.columnCount).coerceAtLeast(1.dp)
                    val height = ((block.visualEndMinute - block.startMinute) * 1.6f).dp
                    val blockTop = with(density) { (12f + block.startMinute * 1.6f).dp.roundToPx() }
                    val labelHeight = if (height >= 72.dp) 56.dp else if (height >= 46.dp) 40.dp else 24.dp
                    val maxLabelOffset = with(density) { (height - labelHeight).coerceAtLeast(0.dp).roundToPx() }
                    val accent = event.displayColor?.let { Color(it or 0xFF000000.toInt()) } ?: colors.primary
                    val background = lerp(colors.surface, accent, if (dark) .28f else .18f)
                    val startText = Instant.ofEpochMilli(event.start).atZone(zone).format(clock)
                    val endText = Instant.ofEpochMilli(event.end).atZone(zone).format(clock)
                    val description = "${event.title.ifBlank { "Untitled event" }}, $startText to $endText" +
                        if (event.declined) ", declined" else ""
                    Surface(onClick = { onEvent(event) }, color = background, contentColor = colors.onSurface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.offset(x = gutter + (width + 3.dp) * block.column,
                            y = (12f + block.startMinute * 1.6f).dp).width(width).height(height - 1.dp)
                            .semantics(mergeDescendants = true) { contentDescription = description }) {
                        Row {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
                            Column(Modifier.offset {
                                IntOffset(0, (scroll.value - blockTop).coerceIn(0, maxLabelOffset))
                            }.padding(horizontal = 5.dp, vertical = 3.dp)) {
                                Text(event.title.ifBlank { "Untitled event" }, style = MaterialTheme.typography.labelMedium,
                                    maxLines = if (height >= 72.dp) 2 else 1, overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (event.declined) TextDecoration.LineThrough else null)
                                if (height >= 46.dp) Text("$startText – $endText", style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    if (now in timeline.start until timeline.end) {
                        val left = if (rtl) 8.dp.toPx() else gutter.toPx()
                        val right = if (rtl) size.width - gutter.toPx() else size.width - 8.dp.toPx()
                        val y = (12f + (now - timeline.start).toFloat() / MINUTE * 1.6f).dp.toPx()
                        drawLine(colors.error, Offset(left, y), Offset(right, y), 2.dp.toPx())
                        drawCircle(colors.error, 3.dp.toPx(), Offset(if (rtl) right else left, y))
                    }
                }
            }
        }
    }
}
