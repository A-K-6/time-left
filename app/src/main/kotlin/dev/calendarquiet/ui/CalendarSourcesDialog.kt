package dev.calendarquiet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.calendarquiet.core.CalendarSource
import dev.calendarquiet.core.selectCalendarSources

@Composable
internal fun CalendarSourcesDialog(
    calendars: List<CalendarSource>, selected: Set<Long>?, loading: Boolean, error: String?, saving: Boolean,
    onSelect: (Set<Long>?) -> Unit, onReload: () -> Unit, onSyncSettings: () -> Unit, onDismiss: () -> Unit,
) {
    val chosen = selectCalendarSources(calendars, selected).map { it.id }.toSet()
    val missing = selected.orEmpty() - calendars.map { it.id }.toSet()
    val google = calendars.filter { it.isGoogle && it.syncEnabled }.map { it.id }.toSet()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Calendar sources") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Used by Calendar and Timer suggestions. Quiet rules keep their own selections.", style = MaterialTheme.typography.bodySmall)
            Text("Account names identify Google, phone and other calendars.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    if (google.isNotEmpty()) TextButton(onClick = { onSelect(google) }, enabled = !saving && !loading && error == null) {
                        Text("Use Google calendars")
                    }
                    TextButton(onClick = { onSelect(null) }, enabled = !saving && !loading && error == null) {
                        Text("Use all visible calendars")
                    }
                    Text(if (selected == null) "Following phone visibility and sync settings" else "${chosen.size} calendars selected",
                        style = MaterialTheme.typography.labelMedium)
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!loading && error == null && missing.isNotEmpty()) Text(
                        "${missing.size} saved calendar(s) are no longer available. Choose sources again.", color = MaterialTheme.colorScheme.error)
                    if (!loading && error == null && calendars.isEmpty()) Text("No calendars found on this phone.")
                }
                items(calendars, key = { it.id }) { calendar ->
                    Row(Modifier.fillMaxWidth().toggleable(calendar.id in chosen, enabled = !saving && !loading && error == null,
                        role = Role.Checkbox, onValueChange = { checked ->
                            onSelect(if (checked) chosen + calendar.id else chosen - calendar.id)
                        }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(calendar.id in chosen, onCheckedChange = null)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(calendar.name.ifBlank { "Untitled calendar" }, style = MaterialTheme.typography.bodyMedium)
                            Text(listOf(calendar.sourceLabel, calendar.account).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall)
                            if (!calendar.visible) Text("Hidden in phone calendar display", style = MaterialTheme.typography.labelSmall)
                            if (!calendar.syncEnabled) Text("Sync disabled · stored events may be outdated", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text("Refresh reloads events stored on this phone. To fetch from Google, check your account’s Calendar sync settings.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    if (!loading && error == null && calendars.none { it.isGoogle }) Text(
                        "Google calendars missing? Enable Calendar sync for your Google account in Android Settings.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onSyncSettings) { Text("Account sync settings") }
                    TextButton(onClick = onReload, enabled = !loading) { Text("Refresh") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
