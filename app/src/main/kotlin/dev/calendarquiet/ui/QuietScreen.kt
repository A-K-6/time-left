package dev.calendarquiet.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import dev.calendarquiet.automation.AutomationStatus
import dev.calendarquiet.automation.QuietNotificationListener
import dev.calendarquiet.core.Rule
import dev.calendarquiet.core.SoundMode
import dev.calendarquiet.core.CalendarSource
import dev.calendarquiet.quietApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class InstalledApp(val packageName: String, val label: String)
private data class Access(val calendar: Boolean, val sound: Boolean, val reminders: Boolean, val snooze: Boolean, val exact: Boolean)

@Composable
fun QuietScreen(context: Context, revision: Int, requestAccess: (String) -> Unit, openTimerRequest: Int = 0) {
    val automation = context.quietApp.automation
    val status by automation.status.collectAsState()
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(openTimerRequest) { if (openTimerRequest > 0) page = 0 }
    var rules by remember { mutableStateOf(automation.store.rules()) }
    var enabled by remember { mutableStateOf(automation.store.enabled) }
    var editing by remember { mutableStateOf<Rule?>(null) }
    var calendars by remember { mutableStateOf<List<CalendarSource>>(emptyList()) }
    var calendarRevision by remember { mutableIntStateOf(0) }
    var choosingCalendars by rememberSaveable { mutableStateOf(false) }
    var calendarLoading by remember { mutableStateOf(false) }
    var calendarError by remember { mutableStateOf<String?>(null) }
    var calendarSaving by remember { mutableStateOf(false) }
    val selectedCalendars by context.quietApp.timers.calendarSelection.collectAsState()
    var installed by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val access = remember(revision, status) { readAccess(context) }

    LaunchedEffect(revision) {
        rules = automation.store.rules()
        enabled = automation.store.enabled
        withContext(Dispatchers.IO) {
            val apps = readApps(context)
            withContext(Dispatchers.Main) { installed = apps }
        }
    }
    DisposableEffect(context, access.calendar) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { calendarRevision++ }
        }
        if (access.calendar) context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(revision, calendarRevision, access.calendar) {
        calendarError = null
        calendars = emptyList()
        calendarLoading = access.calendar
        if (!access.calendar) return@LaunchedEffect
        try {
            delay(200)
            calendars = withContext(Dispatchers.IO) { automation.calendars.calendars() }
        } catch (exception: CancellationException) { throw exception }
        catch (_: Exception) { calendarError = "Could not read calendar sources. Tap Refresh to try again." }
        finally { calendarLoading = false }
    }

    fun chooseCalendars() { calendarRevision++; choosingCalendars = true }

    fun editRules(transform: (List<Rule>) -> List<Rule>, close: Boolean = false) {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { automation.updateRules(transform) }
                rules = automation.store.rules()
                if (close) editing = null
            } catch (exception: Exception) { error = exception.message ?: "Could not save the rule." }
            finally { busy = false }
        }
    }

    BackHandler(editing != null) { editing = null }
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (editing != null) "Edit rule" else "time left", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                        if (editing != null) Text("Choose when and how it runs", style = MaterialTheme.typography.bodySmall)
                    }
                    if (editing != null) TextButton(onClick = { editing = null }) { Text("Back") }
                    else {
                        val timerState by context.quietApp.timers.state.collectAsState()
                        Text("${timerState.completedToday(System.currentTimeMillis())} focus today", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        bottomBar = {
            if (editing == null) NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                listOf("Timer", "Calendar", "Rules", "Schedule", "Setup").forEachIndexed { index, name ->
                    NavigationBarItem(selected = page == index, onClick = { page = index },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                        icon = { Icon(painterResource(listOf(dev.calendarquiet.R.drawable.ic_timer, dev.calendarquiet.R.drawable.ic_calendar_outline,
                            dev.calendarquiet.R.drawable.ic_rules_outline, dev.calendarquiet.R.drawable.ic_clock_outline,
                            dev.calendarquiet.R.drawable.ic_settings_outline)[index]), contentDescription = name,
                            modifier = Modifier.size(22.dp)) }, label = { Text(name, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) })
                }
            }
        }
    ) { padding ->
        val current = editing
        if (current != null) {
            key(current.id) {
                RuleEditor(current, calendars.filter { it.includedByDefault }, installed, busy, Modifier.padding(padding),
                    onSave = { edited -> editRules({ old -> old.filterNot { it.id == edited.id } + edited }, close = true) },
                    onDelete = { editRules({ old -> old.filterNot { it.id == current.id } }, close = true) },
                    isExisting = rules.any { it.id == current.id })
            }
        } else when (page) {
            0 -> TimerPage(context, revision, calendarRevision, calendars, Modifier.padding(padding), requestAccess,
                onChooseCalendars = ::chooseCalendars, onRefreshCalendars = { calendarRevision++ })
            2 -> RulesPage(rules, enabled, status, access, busy, Modifier.padding(padding),
                onEnabled = { value ->
                    if (value && !access.calendar) requestAccess("calendar")
                    else {
                        busy = true
                        scope.launch {
                            try { withContext(Dispatchers.IO) { automation.setEnabled(value) }; enabled = automation.store.enabled }
                            catch (exception: Exception) { error = exception.message }
                            finally { busy = false }
                        }
                    }
                },
                onEdit = { editing = it },
                onToggle = { rule, value -> editRules({ old -> old.map { if (it.id == rule.id) it.copy(enabled = value) else it } }) },
                onAdd = { editing = Rule(name = "", marker = "#focus", soundMode = SoundMode.VIBRATE, leadMinutes = 5) },
                onSetup = { page = 4 })
            3 -> SchedulePage(status, enabled, access.exact, Modifier.padding(padding)) {
                scope.launch { withContext(Dispatchers.IO) { automation.reconcile() } }
            }
            1 -> CalendarPage(context, revision, calendarRevision, rules, enabled, status, access.calendar,
                access.sound, access.snooze, access.reminders, calendars, Modifier.padding(padding), onRequestAccess = { requestAccess("calendar") },
                onEditRule = { editing = it }, onOpenTimer = { page = 0 },
                onChooseCalendars = ::chooseCalendars, onRefreshCalendars = { calendarRevision++ })
            else -> SetupPage(access, calendars, calendarLoading, calendarError, Modifier.padding(padding), requestAccess, ::chooseCalendars)
        }
    }
    if (choosingCalendars) CalendarSourcesDialog(calendars, selectedCalendars, calendarLoading, calendarError, calendarSaving,
        onSelect = { ids ->
            calendarSaving = true
            scope.launch {
                try { withContext(Dispatchers.IO) { context.quietApp.timers.selectCalendars(ids) } }
                catch (exception: CancellationException) { throw exception }
                catch (_: Exception) { error = "Could not save calendar selection." }
                finally { calendarSaving = false }
            }
        }, onReload = { calendarRevision++ }, onSyncSettings = { requestAccess("calendar_sync") },
        onDismiss = { choosingCalendars = false })
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Could not finish") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

@Composable
private fun RulesPage(rules: List<Rule>, enabled: Boolean, status: AutomationStatus, access: Access, busy: Boolean,
    modifier: Modifier, onEnabled: (Boolean) -> Unit, onEdit: (Rule) -> Unit,
    onToggle: (Rule, Boolean) -> Unit, onAdd: () -> Unit, onSetup: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (enabled) "Automation on" else "Automation paused", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Switch(checked = enabled, onCheckedChange = onEnabled, enabled = !busy)
                    }
                    Text(status.message)
                    Text("Turning on applies rules to events currently underway.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!access.calendar || rules.any { it.soundMode != SoundMode.UNCHANGED } && !access.sound ||
            rules.any { it.hiddenPackages.isNotEmpty() } && !access.snooze ||
            rules.any { it.reminderText.isNotBlank() } && !access.reminders) {
            item { OutlinedCard(onClick = onSetup) {
                Column(Modifier.padding(16.dp)) { Text("Finish setup", fontWeight = FontWeight.SemiBold); Text("Enable access for the actions you want to use.") }
            } }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Your rules", style = MaterialTheme.typography.titleLarge)
                FilledTonalButton(onClick = onAdd, enabled = !busy) { Text("+ Add rule") }
            }
        }
        if (rules.isEmpty()) item { Text("Add a rule, then put its marker in a calendar event's title or description.") }
        items(rules, key = { it.id }) { rule ->
            OutlinedCard(onClick = { onEdit(rule) }) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(rule.marker, color = MaterialTheme.colorScheme.primary)
                        }
                        Switch(rule.enabled, { onToggle(rule, it) }, enabled = !busy)
                    }
                    Text(ruleSummary(rule), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text("Example: “Deep work #focus” activates the Focus rule. Markers also work in event descriptions.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text("Pausing stops new actions. Notifications already snoozed return when their original timer expires.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SchedulePage(status: AutomationStatus, enabled: Boolean, exact: Boolean, modifier: Modifier, refresh: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Next seven days", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = refresh, enabled = !status.loading) { Text("Refresh") }
            }
            Text(if (!enabled) "Enable automation to load matching events." else if (exact) "Precise timing access is enabled." else "Timing may be delayed. Enable Alarms & reminders in Setup for precise changes.")
            if (status.lastRefresh > 0) Text("Last checked ${formatTime(status.lastRefresh)}", style = MaterialTheme.typography.bodySmall)
            if (status.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        if (enabled && !status.loading && status.upcoming.isEmpty()) item {
            OutlinedCard { Column(Modifier.padding(20.dp)) {
                Text("No matching events", style = MaterialTheme.typography.titleMedium)
                Text("Check your markers and selected calendars. Google Calendar sync must be enabled on this phone.")
            } }
        }
        items(status.upcoming, key = { "${it.reminderKey}:${it.rule.id}" }) { session ->
            OutlinedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(session.event.title, style = MaterialTheme.typography.titleMedium)
                Text("${formatTime(session.event.start)} – ${formatTime(session.end)}")
                Text("${session.rule.name} · ${ruleSummary(session.rule)}", style = MaterialTheme.typography.bodySmall)
                if (status.active.any { it.reminderKey == session.reminderKey }) Text("Active now", color = MaterialTheme.colorScheme.primary)
            } }
        }
    }
}

@Composable
private fun SetupPage(access: Access, calendars: List<CalendarSource>, calendarLoading: Boolean, calendarError: String?,
    modifier: Modifier, requestAccess: (String) -> Unit, onChooseCalendars: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Enable what you need", style = MaterialTheme.typography.titleLarge)
        Text("Events come from calendars synced to this phone. Your rules and settings stay on this device.")
        AccessCard("Calendar access", "Required to read event titles, descriptions and times.", access.calendar) { requestAccess("calendar") }
        AccessCard("Sound-mode access", "Allows Silent and Vibrate rules. Silent blocks call and notification sounds and vibration; alarms and media are left alone.", access.sound) { requestAccess("sound") }
        AccessCard("Custom reminders", "Allows this app to show your reminder notifications.", access.reminders) { requestAccess("reminders") }
        AccessCard("Notification snoozing", "Allows access to other apps' notifications. Only your selected apps are snoozed; notification text is not saved.", access.snooze) { requestAccess("snooze") }
        AccessCard("Alarms & reminders", "Allows precise event-boundary timing. Without it, Android may delay changes.", access.exact) { requestAccess("timing") }
        if (access.calendar) {
            OutlinedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Calendar sources", style = MaterialTheme.typography.titleMedium)
                if (calendarLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                calendarError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!calendarLoading && calendarError == null && calendars.isEmpty()) Text("No calendars found. Check Calendar sync for your account.")
                calendars.forEach { Text(it.description, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = onChooseCalendars) { Text("Choose calendars") }
                TextButton(onClick = { requestAccess("calendar_sync") }) { Text("Account sync settings") }
            } }
        }
        OutlinedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Samsung and Xiaomi", style = MaterialTheme.typography.titleMedium)
            Text("If actions are delayed, check background settings. On Samsung, remove Calendar Quiet from Sleeping and Deep sleeping apps. On Xiaomi, check background autostart and the app's battery restrictions. Menu names vary by version.")
            TextButton(onClick = { requestAccess("battery") }) { Text("Open app settings") }
        } }
        Text("Snoozing happens after a notification arrives: an initial sound or pop-up may still occur. Calls, alarms, media controls and ongoing notifications are excluded. Existing snoozes cannot be ended early by this app, even if an event is cancelled or shortened.", style = MaterialTheme.typography.bodySmall)
        Text("Other Do Not Disturb rules remain in control. Vibrate does not override them. A manual sound-mode change pauses sound automation until the current group of overlapping events ends.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccessCard(title: String, detail: String, granted: Boolean, action: () -> Unit) {
    OutlinedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(if (granted) "Enabled" else "Not enabled", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = action) { Text(if (granted) "Manage" else "Enable") }
    } }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RuleEditor(original: Rule, calendars: List<CalendarSource>, apps: List<InstalledApp>, busy: Boolean,
    modifier: Modifier, onSave: (Rule) -> Unit, onDelete: () -> Unit, isExisting: Boolean) {
    var name by rememberSaveable { mutableStateOf(original.name) }
    var marker by rememberSaveable { mutableStateOf(original.marker) }
    var mode by rememberSaveable { mutableStateOf(original.soundMode.name) }
    var lead by rememberSaveable { mutableStateOf(original.leadMinutes.toString()) }
    var reminder by rememberSaveable { mutableStateOf(original.reminderText) }
    var reminderLead by rememberSaveable { mutableStateOf(original.reminderLeadMinutes.toString()) }
    var selectedCalendars by rememberSaveable { mutableStateOf(original.calendarIds.toList()) }
    var packages by rememberSaveable { mutableStateOf(original.hiddenPackages.toList()) }
    var fixedDuration by rememberSaveable { mutableStateOf(original.hideMinutes != null) }
    var hideDuration by rememberSaveable { mutableStateOf((original.hideMinutes ?: 30).toString()) }
    var picker by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Rule name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(marker, { marker = it }, label = { Text("Event marker") }, supportingText = { Text("For example #focus, in the event title or description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { picker = "calendars" }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedCalendars.isEmpty()) "All visible synced calendars" else "${selectedCalendars.size} calendar(s) selected")
        }
        HorizontalDivider()
        Text("Sound mode", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundMode.entries.forEach { value -> FilterChip(mode == value.name, { mode = value.name }, label = { Text(modeName(value)) }) }
        }
        NumberField("Activate before event (minutes)", lead, { lead = it })
        Text("Sound restores at the event's end unless you changed it manually. Silent takes priority when events overlap.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Custom reminder", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(reminder, { reminder = it }, label = { Text("Reminder text (optional)") }, supportingText = { Text("Use {event} for the event title. Leave empty for no reminder.") }, modifier = Modifier.fillMaxWidth())
        NumberField("Remind before event (minutes)", reminderLead, { reminderLead = it })
        HorizontalDivider()
        Text("Hide other apps' notifications", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { picker = "apps" }, modifier = Modifier.fillMaxWidth()) {
            Text(if (packages.isEmpty()) "Choose apps to snooze" else "${packages.size} app(s) selected")
        }
        if (packages.isNotEmpty()) {
            Text(packages.joinToString { pkg -> apps.find { it.packageName == pkg }?.label ?: pkg }, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(fixedDuration, { fixedDuration = it })
                Text("Hide for a fixed duration", Modifier.weight(1f))
            }
            if (fixedDuration) NumberField("Duration from activation (minutes)", hideDuration, { hideDuration = it })
            else Text("Notifications return at the event's scheduled end.", style = MaterialTheme.typography.bodySmall)
            Text("Starts at the activation offset above. Fixed duration is capped at the event's end. Calls, alarms and ongoing notifications are excluded.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            val leadNumber = lead.toIntOrNull()
            val reminderNumber = reminderLead.toIntOrNull()
            val durationNumber = hideDuration.toIntOrNull()
            if (leadNumber == null || reminderNumber == null || fixedDuration && packages.isNotEmpty() && durationNumber == null) {
                error = "Enter whole numbers for timing values."
            } else {
                val edited = original.copy(name = name.trim(), marker = marker.trim(), soundMode = SoundMode.valueOf(mode),
                    leadMinutes = leadNumber, reminderText = reminder.trim(), reminderLeadMinutes = reminderNumber,
                    calendarIds = selectedCalendars.toSet(), hiddenPackages = packages.toSet(),
                    hideMinutes = if (fixedDuration && packages.isNotEmpty()) durationNumber else null)
                error = edited.validationError()
                if (error == null) onSave(edited)
            }
        }) { Text(if (busy) "Saving…" else "Save rule") }
        if (isExisting) TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Delete rule", color = MaterialTheme.colorScheme.error) }
    }
    when (picker) {
        "calendars" -> SelectionDialog("Choose calendars", "Leave all unchecked to match every visible synced calendar.",
            calendars.map { Choice(it.id.toString(), "${it.name} · ${it.account}") }, selectedCalendars.map { it.toString() }.toSet(),
            onChange = { selectedCalendars = it.map(String::toLong) }, onDone = { picker = null })
        "apps" -> SelectionDialog("Choose apps to snooze", "Only ordinary notifications from selected apps will be hidden.",
            apps.map { Choice(it.packageName, it.label) }, packages.toSet(), onChange = { packages = it.toList() }, onDone = { picker = null })
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this rule?") },
        text = { Text("Future actions stop. Existing notification snoozes keep their original timer.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep rule") } })
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}

private data class Choice(val id: String, val label: String)
@Composable
private fun SelectionDialog(title: String, detail: String, choices: List<Choice>, selected: Set<String>,
    onChange: (Set<String>) -> Unit, onDone: () -> Unit) {
    var search by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDone, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detail, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(search, { search = it }, label = { Text("Search") }, singleLine = true)
            Row {
                TextButton(onClick = { onChange(emptySet()) }) { Text("Clear selection") }
                Text("${selected.size} selected", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(choices.filter { it.label.contains(search, ignoreCase = true) }, key = { it.id }) { choice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(choice.id in selected, { checked -> onChange(if (checked) selected + choice.id else selected - choice.id) })
                        Text(choice.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (choices.isEmpty()) item { Text("No items found. Check access and calendar sync in Setup.") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDone) { Text("Done") } })
}

private fun readAccess(context: Context): Access {
    val notifications = context.getSystemService(NotificationManager::class.java)
    return Access(context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
        notifications.isNotificationPolicyAccessGranted,
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && notifications.areNotificationsEnabled(),
        notifications.isNotificationListenerAccessGranted(ComponentName(context, QuietNotificationListener::class.java)),
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
}

private fun readApps(context: Context): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        .filter { it.activityInfo.packageName != context.packageName }
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
        .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
}
private fun modeName(mode: SoundMode) = when (mode) {
    SoundMode.UNCHANGED -> "Keep current"
    SoundMode.VIBRATE -> "Vibrate"
    SoundMode.SILENT -> "Silent"
}
private fun ruleSummary(rule: Rule) = buildList {
    if (rule.soundMode != SoundMode.UNCHANGED) add("${modeName(rule.soundMode)} ${rule.leadMinutes} min before")
    if (rule.reminderText.isNotBlank()) add("Reminder ${rule.reminderLeadMinutes} min before")
    if (rule.hiddenPackages.isNotEmpty()) add("Snooze ${rule.hiddenPackages.size} app(s)")
}.joinToString(" · ")
private fun formatTime(millis: Long): String = DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
