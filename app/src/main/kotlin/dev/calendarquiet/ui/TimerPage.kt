package dev.calendarquiet.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.calendarquiet.core.*
import dev.calendarquiet.data.DeviceCalendar
import dev.calendarquiet.quietApp
import dev.calendarquiet.timer.TimerWidget
import dev.calendarquiet.timer.finishLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

@Composable
internal fun TimerPage(context: Context, revision: Int, modifier: Modifier, requestAccess: (String) -> Unit) {
    val timers = context.quietApp.timers
    val state by timers.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val boot = remember { timers.bootCount() }
    val lifecycle = (context as ComponentActivity).lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var focusMinutes by rememberSaveable { mutableStateOf(state.focusMinutes.toString()) }
    var breakMinutes by rememberSaveable { mutableStateOf(state.breakMinutes.toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var calendarError by remember { mutableStateOf<String?>(null) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var calendars by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
    var selected by remember { mutableStateOf(timers.store.calendarIds()) }
    var pickingCalendars by remember { mutableStateOf(false) }
    var calendarRevision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    val calendarAccess = remember(revision) {
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    val notificationAccess = remember(revision) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    val exactAccess = remember(revision) { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { action() } }
            catch (exception: CancellationException) { throw exception }
            catch (exception: Exception) { error = exception.message ?: "Could not update the timer." }
            finally { busy = false }
        }
    }
    fun startDuration(kind: TimerKind, value: String) {
        val minutes = value.toIntOrNull()
        if (minutes == null || minutes !in 1..180) error = "Choose between 1 and 180 minutes."
        else perform { timers.startDuration(kind, minutes) }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Only the visible app ticks. Android renders the widget and notification chronometers itself.
    LaunchedEffect(foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            elapsed = SystemClock.elapsedRealtime()
            val active = timers.state.value.active
            if (active != null && active.remaining(now, elapsed, boot) <= 0) {
                try { withContext(Dispatchers.IO) { timers.refresh() } }
                catch (exception: CancellationException) { throw exception }
                catch (exception: Exception) { error = exception.message ?: "Could not finish the timer." }
            }
            delay(1000)
        }
    }
    DisposableEffect(context, calendarAccess) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { calendarRevision++ }
        }
        if (calendarAccess) context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(revision, calendarAccess, calendarRevision) {
        events = emptyList()
        calendars = emptyList()
        calendarError = null
        loading = calendarAccess
        if (!calendarAccess) return@LaunchedEffect
        try {
            delay(200)
            val (available, instances) = withContext(Dispatchers.IO) {
                val provider = context.quietApp.automation.calendars
                val from = System.currentTimeMillis()
                provider.calendars() to provider.events(from, from + 7 * 24 * HOUR)
            }
            calendars = available
            events = instances
        } catch (exception: CancellationException) { throw exception }
        catch (_: Exception) { calendarError = "Could not read your calendar. Tap Refresh to try again." }
        finally { loading = false }
    }
    val suggestions = TimerSuggestions.from(events, now, selected)
    val active = state.active
    val canStart = active == null && !busy

    LaunchedEffect(active?.id, state.completed?.id) {
        if (active != null || state.completed != null) listState.animateScrollToItem(0)
    }

    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (active != null) {
                        Text(active.title, style = MaterialTheme.typography.titleLarge)
                        val countdown = formatCountdown(active.remaining(now, elapsed, boot))
                        Text(countdown, style = if (countdown.length > 5) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Medium)
                        Text("Ends ${finishLabel(active.finishTime(now, elapsed, boot))}")
                        if (active.kind == TimerKind.EVENT || active.kind == TimerKind.FREE_TIME) {
                            Text("Using the deadline you chose when starting.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { perform { timers.clear() } }, enabled = !busy) { Text("Dismiss timer") }
                    } else {
                        Text(state.completed?.let { "${it.title} finished" } ?: "What’s your time for?",
                            style = MaterialTheme.typography.headlineSmall)
                        Text(if (state.completed?.kind == TimerKind.FOCUS) "Take a break, then choose your next focus."
                            else "Choose a focus session, an event, or your free time.")
                    }
                    Text("Focus sessions today: ${state.completedToday(now)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text("Pomodoro", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(focusMinutes, { focusMinutes = it.take(3) }, Modifier.weight(1f), label = { Text("Focus · min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(breakMinutes, { breakMinutes = it.take(3) }, Modifier.weight(1f), label = { Text("Break · min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { startDuration(TimerKind.FOCUS, focusMinutes) }, Modifier.weight(1f), enabled = canStart) { Text("Start focus") }
                FilledTonalButton(onClick = { startDuration(TimerKind.BREAK, breakMinutes) }, Modifier.weight(1f), enabled = canStart) { Text("Start break") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your calendar", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { calendarRevision++ }, enabled = calendarAccess && !loading) { Text("Refresh") }
            }
        }
        if (!calendarAccess) item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("See event and free-time suggestions from calendars synced to this phone.")
                    FilledTonalButton(onClick = { requestAccess("calendar") }) { Text("Connect calendars") }
                }
            }
        } else {
            item { TextButton(onClick = { pickingCalendars = !pickingCalendars }) {
                Text(if (selected == null) "All visible calendars · Change" else "${selected!!.size} calendars selected · Change")
            } }
            if (pickingCalendars) {
                item { TextButton(onClick = { perform { timers.selectCalendars(null); withContext(Dispatchers.Main) { selected = null } } }) {
                    Text("Use all visible calendars")
                } }
                items(calendars, key = { "calendar-${it.id}" }) { calendar ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selected == null || calendar.id in selected!!, onCheckedChange = { checked ->
                            val next = (selected ?: calendars.map { it.id }.toSet()).toMutableSet().apply {
                                if (checked) add(calendar.id) else remove(calendar.id)
                            }.toSet()
                            perform { timers.selectCalendars(next); withContext(Dispatchers.Main) { selected = next } }
                        }, enabled = !busy)
                        Text(calendar.name, Modifier.weight(1f))
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            calendarError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            items(suggestions.current, key = { "event-${it.calendarId}-${it.id}-${it.start}" }) { event ->
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(event.title.ifBlank { "Current event" }, style = MaterialTheme.typography.titleMedium)
                        Text("${formatCountdown(event.end - now)} left · ends ${finishLabel(event.end)}")
                        Button(onClick = { perform { timers.startUntil(TimerKind.EVENT, event.title.ifBlank { "Event" }, event.end) } },
                            enabled = canStart) { Text("Count down this event") }
                    }
                }
            }
            val freeUntil = suggestions.freeUntil
            if (freeUntil != null && !loading && calendarError == null) item {
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Free until ${finishLabel(freeUntil)}", style = MaterialTheme.typography.titleMedium)
                        Text("${formatCountdown(freeUntil - now)} before ${suggestions.next?.title?.ifBlank { "your next event" }}")
                        Button(onClick = { perform { timers.startUntil(TimerKind.FREE_TIME, "Free time", freeUntil) } }, enabled = canStart) {
                            Text("Count down free time")
                        }
                        val fit = ((freeUntil - now) / MINUTE).toInt()
                        if (fit in 1 until (focusMinutes.toIntOrNull() ?: 25)) {
                            TextButton(onClick = { perform { timers.startDuration(TimerKind.FOCUS, fit, rememberDuration = false) } }, enabled = canStart) { Text("Focus for $fit minutes instead") }
                        }
                    }
                }
            }
            if (!loading && calendarError == null && suggestions.current.isEmpty() && suggestions.next == null) item {
                Text(if (selected?.isEmpty() == true) "Choose at least one calendar for suggestions."
                    else "No timed busy events found in the selected calendars for the next 7 days.")
            }
        }
        item {
            OutlinedButton(onClick = {
                val current = ZonedDateTime.now()
                TimePickerDialog(context, { _, hour, minute ->
                    var until = ZonedDateTime.now().withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                    if (!until.isAfter(ZonedDateTime.now())) until = until.plusDays(1)
                    perform { timers.startUntil(TimerKind.FREE_TIME, "Free time", until.toInstant().toEpochMilli()) }
                }, current.hour, current.minute, DateFormat.is24HourFormat(context)).show()
            }, enabled = canStart) { Text("Choose a free-time end…") }
        }
        if (!notificationAccess || !exactAccess) item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!notificationAccess) {
                        Text("Allow notifications to see your countdown outside the app and hear when it finishes.")
                        TextButton(onClick = { requestAccess("reminders") }) { Text("Allow notifications") }
                    }
                    if (!exactAccess) {
                        Text("Allow precise timing for on-time finish alerts. Without it, Android may delay the alert and the widget’s finished state.")
                        TextButton(onClick = { requestAccess("timing") }) { Text("Enable precise timing") }
                    }
                }
            }
        }
        item {
            val widgets = AppWidgetManager.getInstance(context)
            if (widgets.isRequestPinAppWidgetSupported) {
                OutlinedButton(onClick = { widgets.requestPinAppWidget(ComponentName(context, TimerWidget::class.java), null, null) }) {
                    Text("Add countdown widget")
                }
            } else Text("Add the Time Left widget from your home screen’s widget picker.", style = MaterialTheme.typography.bodySmall)
        }
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Timer") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}
