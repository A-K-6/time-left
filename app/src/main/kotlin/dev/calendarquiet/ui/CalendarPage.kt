package dev.calendarquiet.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.calendarquiet.automation.AutomationStatus
import dev.calendarquiet.core.CalendarEvent
import dev.calendarquiet.core.Rule
import dev.calendarquiet.core.RuleEngine
import dev.calendarquiet.core.Session
import dev.calendarquiet.core.SoundMode
import dev.calendarquiet.core.occursOn
import dev.calendarquiet.core.TimerKind
import dev.calendarquiet.data.DeviceCalendar
import dev.calendarquiet.quietApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
internal fun CalendarPage(
    context: Context, revision: Int, rules: List<Rule>, enabled: Boolean,
    status: AutomationStatus, hasAccess: Boolean, soundAccess: Boolean, snoozeAccess: Boolean,
    reminderAccess: Boolean, calendars: List<DeviceCalendar>,
    modifier: Modifier, onRequestAccess: () -> Unit, onEditRule: (Rule) -> Unit, onOpenTimer: () -> Unit,
) {
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var view by rememberSaveable { mutableStateOf("day") }
    var selectedEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var timerError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val timerState by context.quietApp.timers.state.collectAsState()
    val date = LocalDate.parse(selectedDay)
    val month = YearMonth.from(date)
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(context, hasAccess) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh++ }
        }
        if (hasAccess) context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(30_000) }
    }
    LaunchedEffect(month, zone, revision, hasAccess, refresh) {
        events = emptyList()
        error = null
        loading = hasAccess
        if (!hasAccess) return@LaunchedEffect
        try {
            delay(200)
            // Include UTC all-day instances even when the device is across a date boundary.
            events = withContext(Dispatchers.IO) {
                context.quietApp.automation.calendars.events(
                    month.atDay(1).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    month.plusMonths(1).atDay(1).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            error = exception.message ?: "Could not load calendar events."
        } finally { loading = false }
    }
    val eventsByDay = remember(events, month, zone) {
        (1..month.lengthOfMonth()).associate { day ->
            val value = month.atDay(day)
            value to events.filter { it.occursOn(value, zone) }.sortedWith(
                compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.start })
        }
    }

    val dayEvents = eventsByDay[date].orEmpty()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = view == "day", onClick = { view = "day" }, label = { Text("Day") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = view == "month", onClick = { view = "month" }, label = { Text("Month") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { selectedDay = LocalDate.now().toString() }) { Text("Today") }
            IconButton(onClick = { refresh++ }, enabled = hasAccess && !loading) {
                Icon(painterResource(android.R.drawable.ic_menu_rotate), "Refresh calendar", Modifier.size(24.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selectedDay = (if (view == "day") date.minusDays(1) else month.minusMonths(1).atDay(1)).toString() }) {
                Icon(painterResource(android.R.drawable.ic_media_previous), if (view == "day") "Previous day" else "Previous month", Modifier.size(24.dp))
            }
            Text(if (view == "day") date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale))
                else month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { selectedDay = (if (view == "day") date.plusDays(1) else month.plusMonths(1).atDay(1)).toString() }) {
                Icon(painterResource(android.R.drawable.ic_media_next), if (view == "day") "Next day" else "Next month", Modifier.size(24.dp))
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        if (!hasAccess) {
            Column(Modifier.padding(16.dp)) {
                Text("Allow calendar access to see your events.")
                Button(onClick = onRequestAccess) { Text("Allow calendar access") }
            }
        } else if (view == "day") {
            DayTimelineView(dayEvents, date, zone, now, !loading && error == null, Modifier.weight(1f)) { selectedEvent = it }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { MonthGrid(month, date, eventsByDay, locale) { selectedDay = it.toString(); view = "day" } }
                if (!enabled) item { Text("Quiet automation is paused", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (enabled) item { Text(status.message, style = MaterialTheme.typography.bodySmall) }
                item {
                    HorizontalDivider()
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)),
                        Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                }
                if (!loading && error == null && dayEvents.isEmpty()) item { Text("No events on this day") }
                items(dayEvents, key = { "${it.calendarId}:${it.id}:${it.start}" }) { event ->
                    EventAgenda(event, calendars.firstOrNull { it.id == event.calendarId }?.name,
                        rules, enabled, status, now, zone, soundAccess, snoozeAccess, reminderAccess, onEditRule)
                    HorizontalDivider(Modifier.padding(top = 12.dp))
                }
            }
        }
    }
    selectedEvent?.let { event ->
        val canCountDown = !event.allDay && !event.declined && event.start <= now && event.end > now
        AlertDialog(onDismissRequest = { selectedEvent = null }, title = { Text("Event details") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EventAgenda(event, calendars.firstOrNull { it.id == event.calendarId }?.name,
                        rules, enabled, status, now, zone, soundAccess, snoozeAccess, reminderAccess, onEditRule)
                    if (!event.busy) Text("Marked free", style = MaterialTheme.typography.bodySmall)
                    if (canCountDown && timerState.active != null) Text("A timer is already running.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                if (canCountDown && timerState.active == null) TextButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { context.quietApp.timers.startUntil(TimerKind.EVENT,
                                event.title.ifBlank { "Event" }, event.end) }
                            selectedEvent = null
                            onOpenTimer()
                        } catch (exception: CancellationException) { throw exception }
                        catch (exception: Exception) { timerError = exception.message ?: "Could not start timer." }
                    }
                }) { Text("Count down event") }
                else TextButton(onClick = { selectedEvent = null }) { Text("Done") }
            }, dismissButton = {
                if (canCountDown && timerState.active == null) TextButton(onClick = { selectedEvent = null }) { Text("Close") }
            })
    }
    timerError?.let { message -> AlertDialog(onDismissRequest = { timerError = null }, title = { Text("Timer") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { timerError = null }) { Text("OK") } }) }
}

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate,
    events: Map<LocalDate, List<CalendarEvent>>, locale: Locale, onSelect: (LocalDate) -> Unit) {
    val firstWeekday = WeekFields.of(locale).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - firstWeekday.value + 7) % 7
    Column {
        Row {
            repeat(7) { index ->
                Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) {
                    Text(firstWeekday.plus(index.toLong()).getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
            Row {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - offset + 1
                    if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(52.dp))
                    else {
                        val date = month.atDay(day)
                        val count = events[date].orEmpty().size
                        val chosen = date == selected
                        Surface(onClick = { onSelect(date) }, modifier = Modifier.weight(1f).height(52.dp)
                            .semantics { contentDescription = "$date, $count events" },
                            color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(day.toString(), fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                                Box(Modifier.height(6.dp).width(6.dp).background(
                                    if (count > 0) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.foundation.shape.CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventAgenda(event: CalendarEvent, calendarName: String?, rules: List<Rule>, enabled: Boolean,
    status: AutomationStatus, now: Long, zone: ZoneId, soundAccess: Boolean, snoozeAccess: Boolean,
    reminderAccess: Boolean, onEditRule: (Rule) -> Unit) {
    val matching = rules.filter { RuleEngine.matches(it, event, includeDisabled = true) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(event.title.ifBlank { "Untitled event" }, style = MaterialTheme.typography.titleMedium)
        val formatter = DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withZone(zone)
        Text(if (event.allDay) "All day" else "${formatter.format(Instant.ofEpochMilli(event.start))} - ${formatter.format(Instant.ofEpochMilli(event.end))}",
            style = MaterialTheme.typography.bodyMedium)
        calendarName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (matching.isEmpty()) {
            Text(when { event.declined -> "Declined - quiet rules do not run"
                event.allDay -> "All-day event - quiet rules do not run"
                else -> "No quiet rule" }, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        matching.forEach { rule ->
            val session = Session(rule, event)
            val label = when {
                !rule.enabled -> "Disabled"
                !enabled -> "Paused"
                now >= session.end -> "Ended"
                status.active.any { it.reminderKey == session.reminderKey } && now >= session.start -> "Rule active now"
                now >= session.start -> "Awaiting automation"
                else -> "Scheduled"
            }
            TextButton(onClick = { onEditRule(rule) }) { Text("${rule.name} - $label") }
            val actions = buildList {
                when (rule.soundMode) {
                    SoundMode.SILENT -> add("Silent")
                    SoundMode.VIBRATE -> add("Vibrate")
                    SoundMode.UNCHANGED -> Unit
                }
                if (rule.hiddenPackages.isNotEmpty()) add("Snooze ${rule.hiddenPackages.size} app(s)")
                if (rule.reminderText.isNotBlank()) add("Reminder ${rule.reminderLeadMinutes} min before")
            }
            Text(actions.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
            val missing = buildList {
                if (rule.soundMode != SoundMode.UNCHANGED && !soundAccess) add("sound")
                if (rule.hiddenPackages.isNotEmpty() && !snoozeAccess) add("notification snoozing")
                if (rule.reminderText.isNotBlank() && !reminderAccess) add("reminders")
            }
            if (missing.isNotEmpty()) Text("Access needed: ${missing.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (rule.soundMode != SoundMode.UNCHANGED || rule.hiddenPackages.isNotEmpty()) {
                Text("Quiet window: ${formatter.format(Instant.ofEpochMilli(session.start))} - ${formatter.format(Instant.ofEpochMilli(session.end))}",
                    style = MaterialTheme.typography.bodySmall)
                if (rule.hiddenPackages.isNotEmpty() && session.hideEnd < session.end) {
                    Text("App snoozing ends ${formatter.format(Instant.ofEpochMilli(session.hideEnd))}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
