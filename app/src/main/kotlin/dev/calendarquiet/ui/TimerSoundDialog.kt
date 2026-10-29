package dev.calendarquiet.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.calendarquiet.core.TimerKind
import dev.calendarquiet.timer.TimerNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun TimerSoundDialog(context: Context, revision: Int, requestAccess: (String) -> Unit, onDismiss: () -> Unit) {
    val sounds = remember(context) { TimerNotifications(context) }
    val scope = rememberCoroutineScope()
    var onStart by remember { mutableStateOf(sounds.playOnStart) }
    var onFinish by remember { mutableStateOf(sounds.playOnFinish) }
    var summaries by remember { mutableStateOf<Map<TimerKind, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var volumeMuted by remember { mutableStateOf(false) }
    val kinds = listOf(TimerKind.FOCUS, TimerKind.BREAK, TimerKind.EVENT)
    LaunchedEffect(revision) {
        summaries = withContext(Dispatchers.IO) {
            sounds.ensureSoundChannels()
            kinds.associateWith { sounds.soundSummary(it) }
        }
        volumeMuted = context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Timer sounds") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("When focus or break starts", Modifier.weight(1f))
                Switch(onStart, { enabled -> scope.launch {
                    try { withContext(Dispatchers.IO) { sounds.setPlayOnStart(enabled) }; onStart = enabled }
                    catch (exception: CancellationException) { throw exception }
                    catch (_: Exception) { error = "Could not save sound settings." }
                } }, modifier = Modifier.semantics { contentDescription = "Play sound when focus or break starts" })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("When a timer finishes", Modifier.weight(1f))
                Switch(onFinish, { enabled -> scope.launch {
                    try { withContext(Dispatchers.IO) { sounds.setPlayOnFinish(enabled) }; onFinish = enabled }
                    catch (exception: CancellationException) { throw exception }
                    catch (_: Exception) { error = "Could not save sound settings." }
                } }, modifier = Modifier.semantics { contentDescription = "Play sound when a timer finishes" })
            }
            HorizontalDivider()
            kinds.forEach { kind ->
                Text(TimerNotifications.channelName(kind), style = MaterialTheme.typography.titleSmall)
                Text(summaries[kind] ?: "Loading…", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { requestAccess(when (kind) {
                        TimerKind.FOCUS -> "focus_sound"
                        TimerKind.BREAK -> "break_sound"
                        else -> "calendar_timer_sound"
                    }) }, modifier = Modifier.semantics { contentDescription = "Choose ${TimerNotifications.channelName(kind)}" }) { Text("Choose sound") }
                    TextButton(onClick = { if (!sounds.test(kind)) requestAccess("reminders") },
                        modifier = Modifier.semantics { contentDescription = "Test ${TimerNotifications.channelName(kind)}" }) { Text("Test") }
                }
            }
            Text(if (volumeMuted) "Alarm volume is muted." else "Cues use your phone’s alarm volume. Do Not Disturb settings still apply.",
                style = MaterialTheme.typography.bodySmall,
                color = if (volumeMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { requestAccess("alarm_volume") }) { Text("Adjust alarm volume") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
