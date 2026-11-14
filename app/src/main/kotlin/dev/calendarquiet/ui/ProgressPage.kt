package dev.calendarquiet.ui

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.calendarquiet.core.FocusProgress
import dev.calendarquiet.quietApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressPage(context: Context, revision: Int, modifier: Modifier, onOpenTimer: () -> Unit) {
    val timers = context.quietApp.timers
    val state by timers.state.collectAsState()
    val lifecycle = context.uiLifecycle()
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(revision) {
        lifecycle.withVisibleUi {
            while (true) {
                today = LocalDate.now()
                val midnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                delay((midnight - System.currentTimeMillis()).coerceAtLeast(1000))
            }
        }
    }
    val progress = remember(state.progress, state.focusFinishedAt, today) {
        state.progress ?: FocusProgress.importHistory(state.focusFinishedAt, System.currentTimeMillis(), ZoneId.systemDefault())
    }
    val summary = remember(progress, today) { progress.summary(today) }
    var selectedIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = LocalDate.parse(selectedIso).coerceIn(today.minusDays(364), today)
    var goalDialog by remember { mutableStateOf(false) }
    var rulesDialog by remember { mutableStateOf(false) }
    var dateDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    fun chooseDay() { dateDialog = true }
    val milestones = remember(summary) { listOf(
        ProgressMilestone("First light", "Complete your first focus", summary.totalFocuses, 1),
        ProgressMilestone("Finding a rhythm", "Complete 10 focuses", summary.totalFocuses, 10),
        ProgressMilestone("Three in a row", "Focus on 3 consecutive days", summary.bestStreak.toLong(), 3),
        ProgressMilestone("A week of focus", "Focus on 7 consecutive days", summary.bestStreak.toLong(), 7),
        ProgressMilestone("Making room", "Complete 50 focuses", summary.totalFocuses, 50),
        ProgressMilestone("One hundred moments", "Complete 100 focuses", summary.totalFocuses, 100)) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("A LITTLE, OFTEN", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Your rhythm.", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
            Text("Small sessions add up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Level ${summary.level}", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                        Text("${summary.xp} XP", fontWeight = FontWeight.Medium)
                    }
                    LinearProgressIndicator(progress = { summary.levelXp / 100f }, modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f))
                    Text("${100 - summary.levelXp} XP to the next level", style = MaterialTheme.typography.bodySmall)
                    if (summary.totalFocuses == 0L) Text("Your first completed focus lights up today.")
                    if (summary.totalFocuses == 0L || state.active != null) FilledTonalButton(onClick = onOpenTimer) { Text(if (state.active == null) "Open timer" else "Back to timer") }
                }
            }
        }
        item {
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Your focus year", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = ::chooseDay) { Text("Choose day") }
                    }
                    Text("Completed focuses · swipe across the year", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FocusHeatmap(progress, today, selected, { selectedIso = it.toString() }, ::chooseDay)
                    HorizontalDivider()
                    val count = progress.days[selected] ?: 0
                    Text(if (selected == today) "Today" else selected.format(formatter), style = MaterialTheme.typography.titleMedium)
                    Text(if (count == 0 && selected < progress.trackedFrom) "No saved history for this day"
                        else "$count completed focus${if (count == 1) "" else "es"} · ${count * 10L} XP")
                    Text("Full tracking since ${progress.trackedFrom.format(formatter)}. Earlier saved sessions are included; older history may be incomplete.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedCard(onClick = { goalDialog = true }) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Today's target", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text("${summary.todayFocuses} / ${progress.dailyGoal}", fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(progress = { (summary.todayFocuses.toFloat() / progress.dailyGoal).coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth())
                    Text(if (summary.todayFocuses >= progress.dailyGoal) "Target reached. Nice work."
                        else "${progress.dailyGoal - summary.todayFocuses} more completed focus${if (progress.dailyGoal - summary.todayFocuses == 1) "" else "es"} · tap to change target",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressStat("Day streak", summary.currentStreak.toString(), Modifier.weight(1f))
                ProgressStat("Best streak", summary.bestStreak.toString(), Modifier.weight(1f))
                ProgressStat("Focuses", summary.totalFocuses.toString(), Modifier.weight(1f))
            }
            if (summary.currentStreak > 0 && summary.todayFocuses == 0) Text("Complete a focus today to continue your streak.",
                Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text("Milestones", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text("Earned from the history saved on this device.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(milestones, key = { it.title }) { milestone ->
            val earned = milestone.count >= milestone.target
            OutlinedCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = if (earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(if (earned) "✓" else "○", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(milestone.title, style = MaterialTheme.typography.titleMedium)
                        Text(milestone.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (earned) "Earned" else "${milestone.count.coerceAtMost(milestone.target)} / ${milestone.target}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { TextButton(onClick = { rulesDialog = true }) { Text("How progress works") } }
    }
    if (dateDialog) {
        val window = LocalWindowInfo.current.containerSize
        val density = LocalDensity.current
        val compactPicker = with(density) { window.width.toDp() < 360.dp || window.height.toDp() < 600.dp } || density.fontScale > 1.4f
        val pickerColors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selected.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            initialDisplayMode = if (compactPicker) DisplayMode.Input else DisplayMode.Picker,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                    return day in today.minusDays(364)..today
                }
                override fun isSelectableYear(year: Int): Boolean = year in today.minusDays(364).year..today.year
            })
        DatePickerDialog(onDismissRequest = { dateDialog = false }, colors = pickerColors,
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { selectedIso = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString() }
                dateDialog = false
            }) { Text("Use date") } },
            dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Cancel") } }) {
            DatePicker(state = picker, colors = pickerColors, showModeToggle = !compactPicker,
                modifier = Modifier.verticalScroll(rememberScrollState()))
        }
    }
    if (goalDialog) {
        var goal by remember { mutableIntStateOf(progress.dailyGoal) }
        AlertDialog(onDismissRequest = { if (!saving) goalDialog = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Daily focus target") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$goal completed focus${if (goal == 1) "" else "es"} per day", style = MaterialTheme.typography.titleMedium)
                Slider(value = goal.toFloat(), onValueChange = { goal = it.toInt() }, valueRange = 1f..12f, steps = 10, enabled = !saving)
                Text("Your target changes the heatmap shading. Earned XP and streaks stay the same.")
            } }, confirmButton = { TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { timers.saveDailyGoal(goal) }; goalDialog = false }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Could not save your target." }
                    finally { saving = false }
                }
            }) { Text("Save target") } }, dismissButton = { TextButton(enabled = !saving, onClick = { goalDialog = false }) { Text("Cancel") } })
    }
    if (rulesDialog) AlertDialog(onDismissRequest = { rulesDialog = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("A little progress, every day") },
        text = { Text("Each completed Focus timer earns 10 XP, whatever its length. Every 100 XP reaches the next level. Breaks, calendar countdowns and stopped sessions don't earn XP.\n\nA streak counts consecutive days with at least one completed focus. You have today to continue yesterday's streak.\n\nDays use the local date at completion. Your history and rewards are kept on this device.", Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { rulesDialog = false }) { Text("Got it") } })
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Progress") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

private data class HeatmapCell(val day: LocalDate, val week: Int, val row: Int, val count: Int, val intensity: Int)

private data class ProgressMilestone(val title: String, val detail: String, val count: Long, val target: Long)

@Composable
private fun ProgressStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FocusHeatmap(progress: FocusProgress, today: LocalDate, selected: LocalDate,
    onSelect: (LocalDate) -> Unit, onChooseDate: () -> Unit) {
    val start = remember(today) { today.minusDays(364).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val weeks = (ChronoUnit.DAYS.between(start, today) / 7 + 1).toInt()
    val scroll = rememberScrollState()
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(scroll.maxValue) { if (!positioned && scroll.maxValue > 0) { scroll.scrollTo(scroll.maxValue); positioned = true } }
    val density = LocalDensity.current
    val pitchDp = maxOf(16f, density.fontScale * 12f).dp
    val topDp = maxOf(22f, density.fontScale * 11f + 8f).dp
    val pitch = with(density) { pitchDp.toPx() }
    val cell = with(density) { (pitchDp - 4.dp).toPx() }
    val top = with(density) { topDp.toPx() }
    val cells = remember(progress, today, start, weeks) { (0 until weeks * 7).map { index ->
        val day = start.plusDays(index.toLong())
        val count = progress.days[day] ?: 0
        HeatmapCell(day, index / 7, index % 7, count,
            if (count == 0) 0 else ((count.toFloat() / progress.dailyGoal * 4).toInt()).coerceIn(1, 4))
    } }
    val months = remember(start, weeks, Locale.getDefault()) { (0 until weeks).mapNotNull { week ->
        val monday = start.plusWeeks(week.toLong())
        if (week == 0 || monday.month != monday.minusWeeks(1).month)
            week to monday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) else null
    } }
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val border = MaterialTheme.colorScheme.outlineVariant
    val palette = listOf(empty, primary.copy(alpha = .22f), primary.copy(alpha = .44f), primary.copy(alpha = .7f), primary)
    val paint = remember(density, muted) { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted.toArgb(); textSize = with(density) { 11.sp.toPx() } } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Column(Modifier.width(30.dp).padding(top = topDp)) {
                repeat(7) { row -> Text(if (row in listOf(0, 2, 4)) listOf("M", "T", "W", "T", "F", "S", "S")[row] else "",
                    Modifier.height(pitchDp), fontSize = 11.sp, lineHeight = 11.sp, color = muted) }
            }
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Canvas(Modifier.width(pitchDp * weeks).height(topDp + pitchDp * 7 + 4.dp)
                    .semantics {
                        contentDescription = "Focus heatmap for the past year. Choose day to inspect a date."
                        customActions = listOf(CustomAccessibilityAction("Choose day") { onChooseDate(); true })
                    }.pointerInput(start, today, pitch, top) {
                        detectTapGestures { offset ->
                            val week = (offset.x / pitch).toInt(); val row = ((offset.y - top) / pitch).toInt()
                            if (offset.y >= top && week in 0 until weeks && row in 0..6) {
                                val day = start.plusDays((week * 7 + row).toLong())
                                if (day in today.minusDays(364)..today) onSelect(day)
                            }
                        }
                    }) {
                    months.forEach { (week, label) -> drawContext.canvas.nativeCanvas.drawText(label, week * pitch, paint.textSize, paint) }
                    cells.forEach { entry ->
                        val day = entry.day
                        if (day <= today && day >= today.minusDays(364)) {
                            val at = Offset(entry.week * pitch, top + entry.row * pitch)
                            drawRoundRect(palette[entry.intensity], at, Size(cell, cell), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                            if (entry.count == 0 && day < progress.trackedFrom) drawLine(border, at + Offset(2.dp.toPx(), cell - 2.dp.toPx()),
                                at + Offset(cell - 2.dp.toPx(), 2.dp.toPx()), 1.dp.toPx())
                            if (day == selected) drawRoundRect(primary, at - Offset(1.dp.toPx(), 1.dp.toPx()),
                                Size(cell + 2.dp.toPx(), cell + 2.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = MaterialTheme.typography.bodySmall, color = muted)
            palette.forEach { color -> Canvas(Modifier.padding(start = 4.dp).size(10.dp)) { drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())) } }
            Text("  Target", style = MaterialTheme.typography.bodySmall, color = muted)
        }
        Text("Slashed cells have incomplete older history.", style = MaterialTheme.typography.bodySmall, color = muted)
    }
}
