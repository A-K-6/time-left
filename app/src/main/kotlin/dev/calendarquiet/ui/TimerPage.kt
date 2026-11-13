package dev.calendarquiet.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import dev.calendarquiet.R
import kotlin.math.cos
import kotlin.math.sin
import dev.calendarquiet.timer.FocusWidget
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.calendarquiet.core.*
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
internal fun TimerPage(context: Context, revision: Int, calendarRevision: Int, calendars: List<CalendarSource>,
    modifier: Modifier, requestAccess: (String) -> Unit, onChooseCalendars: () -> Unit, onRefreshCalendars: () -> Unit) {
    val timers = context.quietApp.timers
    val state by timers.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val boot = remember { timers.bootCount() }
    val lifecycle = (context as ComponentActivity).lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val focusMinutes = state.focusMinutes.toString()
    val breakMinutes = state.breakMinutes.toString()
    var focusTitle by rememberSaveable { mutableStateOf("") }
    var showPomodoroSettings by rememberSaveable { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var chosenKind by rememberSaveable { mutableStateOf(TimerKind.FOCUS) }
    var showLabel by rememberSaveable { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var calendarError by remember { mutableStateOf<String?>(null) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    val selected by timers.calendarSelection.collectAsState()
    var showSoundSettings by rememberSaveable { mutableStateOf(false) }
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
        else perform { timers.startDuration(kind, minutes, title = if (kind == TimerKind.FOCUS) focusTitle else null) }
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
    LaunchedEffect(revision, calendarAccess, calendarRevision, selected) {
        events = emptyList()
        calendarError = null
        loading = calendarAccess
        if (!calendarAccess) return@LaunchedEffect
        try {
            delay(200)
            events = withContext(Dispatchers.IO) {
                val provider = context.quietApp.automation.calendars
                val from = System.currentTimeMillis()
                provider.events(from, from + 7 * 24 * HOUR, selected)
            }
        } catch (exception: CancellationException) { throw exception }
        catch (_: Exception) { calendarError = "Could not read your calendar. Tap Refresh to try again." }
        finally { loading = false }
    }
    val suggestions = TimerSuggestions.from(events, now, selected)
    val active = state.active
    val canStart = active == null && !busy

    LaunchedEffect(state.completed?.id) {
        if (state.completed != null) chosenKind = state.nextSessionKind
    }
    LaunchedEffect(active?.id, state.completed?.id) {
        if (active == null) fullScreen = false
        if (active != null || state.completed != null) listState.animateScrollToItem(0)
    }

    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (active != null) "IN THE MOMENT" else if (state.completed != null) "A MOMENT WELL SPENT" else "MAKE ROOM",
                        style = MaterialTheme.typography.labelSmall, letterSpacing = 1.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(active?.title ?: if (state.completed?.kind == TimerKind.FOCUS) "Well done." else "Make time.",
                        style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                IconButton(onClick = { showSoundSettings = true }) {
                    Icon(painterResource(R.drawable.ic_sound_outline), "Sounds & vibration", Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showPomodoroSettings = true }) {
                    Icon(painterResource(R.drawable.ic_settings_outline), "Customize Pomodoro", Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (active == null || active.canPause) item {
            val selectedKind = active?.kind ?: chosenKind
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(TimerKind.FOCUS to "Focus", TimerKind.BREAK to "Short break", TimerKind.LONG_BREAK to "Long break").forEach { (kind, label) ->
                        val selectedPhase = selectedKind == kind
                        TextButton(onClick = { chosenKind = kind }, enabled = active == null && !busy,
                            modifier = Modifier.weight(1f).semantics { this.selected = selectedPhase }, shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = ButtonDefaults.textButtonColors(containerColor = if (selectedPhase) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = if (selectedPhase) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContainerColor = if (selectedPhase) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                disabledContentColor = if (selectedPhase) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
        item {
            if (active != null) {
                FocusDial(active.remaining(now, elapsed, boot).coerceAtLeast(0), active.durationMillis,
                    if (active.isPaused) "PAUSED" else if (active.kind == TimerKind.FOCUS) "STAY WITH IT" else "TAKE YOUR TIME",
                    if (active.isPaused) "Your time is saved" else "Until ${finishLabel(active.finishTime(now, elapsed, boot))}",
                    active.kind != TimerKind.FOCUS)
            } else {
                FocusDial(state.minutesFor(chosenKind) * MINUTE, state.minutesFor(chosenKind) * MINUTE,
                    "READY WHEN YOU ARE", "${when (chosenKind) { TimerKind.BREAK -> "Short break"; TimerKind.LONG_BREAK -> "Long break"; else -> "Focus" }} · ${state.minutesFor(chosenKind)} min", chosenKind != TimerKind.FOCUS)
            }
        }
        item {
            if (active != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (active.canPause) {
                        Button(onClick = { perform { timers.pauseOrResume(active.id) } }, enabled = !busy,
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                            Text(if (active.isPaused) "Resume" else "Pause", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(onClick = { perform { timers.extend(active.id, 1) } },
                            enabled = !busy && active.durationMillis + MINUTE <= 180 * MINUTE,
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.heightIn(min = 54.dp), contentPadding = PaddingValues(horizontal = 16.dp)) { Text("+1 min") }
                        IconButton(onClick = { fullScreen = true }, modifier = Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.ic_expand_outline), "Full screen", Modifier.size(20.dp))
                        }
                    } else {
                        OutlinedButton(onClick = { confirmStop = active.id }, modifier = Modifier.fillMaxWidth()) { Text("Stop countdown") }
                    }
                }
                if (active.canPause) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { confirmStop = active.id }, enabled = !busy) {
                        Text("End session", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val label = when (chosenKind) { TimerKind.BREAK -> "Start short break"; TimerKind.LONG_BREAK -> "Start long break"; else -> "Start focus" }
                Button(onClick = { startDuration(chosenKind, state.minutesFor(chosenKind).toString()) }, enabled = canStart,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(painterResource(R.drawable.ic_play_outline), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
                if (chosenKind == TimerKind.FOCUS) {
                    if (showLabel || focusTitle.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(focusTitle, { focusTitle = it.take(120) }, Modifier.fillMaxWidth(),
                            label = { Text("Name this focus · optional") }, shape = RoundedCornerShape(14.dp), singleLine = true, enabled = canStart)
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { showLabel = true }) { Text("+ Give this focus a name", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (state.focusesInCycle >= state.longBreakEvery) "Long break ready" else "${state.focusesInCycle} of ${state.longBreakEvery} focuses", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(state.longBreakEvery) { index ->
                            Box(Modifier.size(6.dp).background(if (index < state.focusesInCycle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                        }
                    }
                }
                Text("${state.longBreakMinutes} min long break", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (active == null) item {
            Text("A DIFFERENT PACE", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(15, 25, 50).forEach { minutes ->
                    FilledTonalButton(onClick = { perform { timers.startDuration(TimerKind.FOCUS, minutes, rememberDuration = false, title = focusTitle) } },
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp), enabled = canStart, shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Text("$minutes", style = MaterialTheme.typography.titleMedium)
                        Text(" min", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Around your day", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onRefreshCalendars, enabled = calendarAccess && !loading) { Text("Refresh") }
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
            item { TextButton(onClick = onChooseCalendars) {
                Text(if (selected == null) "All visible calendars · Change" else "${selected!!.size} calendars selected · Change")
            } }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            calendarError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            items(suggestions.current, key = { "event-${it.calendarId}-${it.id}-${it.start}" }) { event ->
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(event.title.ifBlank { "Current event" }, style = MaterialTheme.typography.titleMedium)
                        calendars.firstOrNull { it.id == event.calendarId }?.let { Text(it.description, style = MaterialTheme.typography.bodySmall) }
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { widgets.requestPinAppWidget(ComponentName(context, TimerWidget::class.java), null, null) }) {
                        Text("Timer controls widget")
                    }
                    OutlinedButton(onClick = { widgets.requestPinAppWidget(ComponentName(context, FocusWidget::class.java), null, null) }) {
                        Text("Quick focus widget")
                    }
                }
            } else Text("Add the Time Left widget from your home screen’s widget picker.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (showPomodoroSettings) PomodoroSettingsDialog(state, onDismiss = { showPomodoroSettings = false }) { focus, short, long, every ->
        perform { timers.saveSettings(focus, short, long, every) }
        showPomodoroSettings = false
    }
    confirmStop?.let { id ->
        AlertDialog(onDismissRequest = { confirmStop = null }, title = { Text("Stop this timer?") },
            text = { Text("This session will not count as completed. Your finished sessions are kept.") },
            confirmButton = { TextButton(onClick = { perform { timers.clear(id) }; confirmStop = null }) { Text("Stop timer") } },
            dismissButton = { TextButton(onClick = { confirmStop = null }) { Text("Keep going") } })
    }
    if (fullScreen && active?.canPause == true) {
        Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
                    val view = LocalView.current
                    var keepAwake by rememberSaveable { mutableStateOf(false) }
                    DisposableEffect(view, keepAwake, active.isPaused) {
                        view.keepScreenOn = keepAwake && !active.isPaused
                        onDispose { view.keepScreenOn = false }
                    }
                    TimerDisplay(active, now, elapsed, boot)
                    Button(onClick = { perform { timers.pauseOrResume(active.id) } }, enabled = !busy) {
                        Text(if (active.isPaused) "Resume" else "Pause")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Keep screen on", Modifier.padding(end = 16.dp))
                        Switch(checked = keepAwake, onCheckedChange = { keepAwake = it })
                    }
                    TextButton(onClick = { fullScreen = false }) { Text("Back to timers") }
                }
            }
        }
    }
    if (showSoundSettings) TimerSoundDialog(context, revision, requestAccess) { showSoundSettings = false }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Timer") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

@Composable
private fun TimerDisplay(timer: Countdown, now: Long, elapsed: Long, boot: Int) {
    Text(timer.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    FocusDial(timer.remaining(now, elapsed, boot).coerceAtLeast(0), timer.durationMillis,
        if (timer.isPaused) "PAUSED" else if (timer.kind == TimerKind.FOCUS) "STAY WITH IT" else "TAKE YOUR TIME",
        if (timer.isPaused) "Your time is saved" else "Until ${finishLabel(timer.finishTime(now, elapsed, boot))}", timer.kind != TimerKind.FOCUS)
}

@Composable
private fun FocusDial(left: Long, total: Long, label: String, caption: String, rest: Boolean) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = if (rest) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.sizeIn(maxWidth = 224.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 14.dp.toPx()
                repeat(48) { i ->
                    val angle = (i * 7.5 - 90) * Math.PI / 180
                    val outer = radius + 11.dp.toPx()
                    val inner = outer - (if (i % 4 == 0) 4.dp.toPx() else 2.dp.toPx())
                    drawLine(track, Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                        Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer), 1.dp.toPx())
                }
                drawCircle(track, radius, center, style = Stroke(2.dp.toPx()))
                val fraction = (left.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
                drawArc(accent, -90f, 360f * fraction, false,
                    topLeft = Offset(center.x - radius, center.y - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                val angle = (fraction * 360 - 90) * Math.PI / 180
                drawCircle(accent, 5.dp.toPx(), Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val clock = formatCountdown(left)
                Text(clock, color = ink, style = TextStyle(fontSize = if (clock.length > 5) 44.sp else 64.sp,
                    fontWeight = FontWeight.Light, letterSpacing = (-2.5).sp, fontFeatureSettings = "tnum"))
                Text(label, color = muted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun PomodoroSettingsDialog(state: TimerState, onDismiss: () -> Unit, onSave: (Int, Int, Int, Int) -> Unit) {
    var focus by rememberSaveable { mutableStateOf(state.focusMinutes.toString()) }
    var short by rememberSaveable { mutableStateOf(state.breakMinutes.toString()) }
    var long by rememberSaveable { mutableStateOf(state.longBreakMinutes.toString()) }
    var every by rememberSaveable { mutableStateOf(state.longBreakEvery.toString()) }
    val f = focus.toIntOrNull(); val s = short.toIntOrNull(); val l = long.toIntOrNull(); val e = every.toIntOrNull()
    val valid = f != null && f in 1..180 && s != null && s in 1..180 && l != null && l in 1..180 && e != null && e in 1..12
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your Pomodoro rhythm") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Saved for the app and both widgets. A running session keeps its current length.")
            listOf(Triple("Focus · minutes", focus, { v: String -> focus = v.take(3) }),
                Triple("Short break · minutes", short, { v: String -> short = v.take(3) }),
                Triple("Long break · minutes", long, { v: String -> long = v.take(3) }),
                Triple("Long break every · focuses", every, { v: String -> every = v.take(2) })).forEach { (label, value, change) ->
                OutlinedTextField(value, change, label = { Text(label) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("Sessions: 1–180 minutes. Long break: every 1–12 focuses. Nothing starts automatically.")
        }
    }, confirmButton = { TextButton(onClick = { if (valid) onSave(f!!, s!!, l!!, e!!) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
