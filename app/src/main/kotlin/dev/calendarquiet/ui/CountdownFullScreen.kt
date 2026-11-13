package dev.calendarquiet.ui

import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.widget.Chronometer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.calendarquiet.core.Countdown
import dev.calendarquiet.core.HOUR
import dev.calendarquiet.core.countdownChronometerBase
import dev.calendarquiet.core.formatCountdown
import dev.calendarquiet.timer.finishLabel

@Composable
internal fun CountdownFullScreen(timer: Countdown, boot: Int, foreground: Boolean, busy: Boolean, environmentRevision: Int,
    onClose: () -> Unit, onPauseResume: () -> Unit, onExtend: () -> Unit, onStop: () -> Unit, onExpired: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        var keepAwake by rememberSaveable { mutableStateOf(false) }
        var confirmStop by remember { mutableStateOf(false) }
        DisposableEffect(view) {
            val window = (view.parent as DialogWindowProvider).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        }
        DisposableEffect(view, keepAwake, foreground, timer.isPaused) {
            view.keepScreenOn = keepAwake && foreground && !timer.isPaused
            onDispose { view.keepScreenOn = false }
        }
        BackHandler { onClose() }
        val endLabel = remember(timer, environmentRevision) {
            finishLabel(timer.finishTime(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot))
        }
        val accent = Color(0xFFD2E8AF)
        val muted = Color(0xFFA8B7AA)
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color(0xFFF1F5ED)) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
                val landscape = maxWidth > maxHeight
                val scale = LocalDensity.current.fontScale
                val stackControls = timer.canPause && !landscape && maxWidth < 330.dp && scale > 1.15f
                val columns = if (timer.durationMillis >= HOUR) 4.8f else 3.1f
                val clockSp = (minOf(maxWidth.value / columns, maxHeight.value * if (landscape) .32f else .23f) / scale).coerceIn(32f, 128f)
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClose) { Text("Back to timers", color = muted, style = MaterialTheme.typography.labelMedium) }
                        Spacer(Modifier.weight(1f))
                        Text(if (timer.isPaused) "PAUSED" else "TIME REMAINING", color = accent,
                            style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
                        if (landscape) {
                            Spacer(Modifier.width(24.dp))
                            Text("Screen on", color = muted, style = MaterialTheme.typography.labelSmall)
                            Switch(keepAwake, { keepAwake = it })
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(timer.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    NativeCountdown(timer, boot, foreground, environmentRevision, clockSp,
                        Modifier.fillMaxWidth().height((clockSp * scale * 1.35f).dp), onExpired)
                    Text(if (timer.isPaused) "Remaining time saved" else "Ends $endLabel",
                        color = muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    if (stackControls) {
                        Button(onClick = onPauseResume, enabled = !busy, shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF17291A)),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(if (timer.isPaused) "Resume" else "Pause", fontWeight = FontWeight.Medium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onExtend, enabled = !busy && timer.durationMillis + 60_000 <= 180 * 60_000) { Text("+1 min", color = accent) }
                            TextButton(onClick = { confirmStop = true }, enabled = !busy) { Text("End", color = muted) }
                        }
                    } else Row(Modifier.widthIn(max = 440.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (timer.canPause) {
                            Button(onClick = onPauseResume, enabled = !busy, shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF17291A)),
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                                Text(if (timer.isPaused) "Resume" else "Pause", fontWeight = FontWeight.Medium)
                            }
                            OutlinedButton(onClick = onExtend, enabled = !busy && timer.durationMillis + 60_000 <= 180 * 60_000,
                                shape = RoundedCornerShape(16.dp), modifier = Modifier.heightIn(min = 52.dp)) { Text("+1 min", color = accent) }
                        }
                        TextButton(onClick = { confirmStop = true }, enabled = !busy) { Text("End", color = muted) }
                    }
                    if (!landscape) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Keep screen on", color = muted, style = MaterialTheme.typography.bodySmall)
                        Switch(keepAwake, { keepAwake = it }, colors = SwitchDefaults.colors(
                            checkedTrackColor = accent, checkedThumbColor = Color(0xFF17291A), uncheckedTrackColor = Color(0xFF27372C)))
                    }
                }
            }
        }
        if (confirmStop) AlertDialog(onDismissRequest = { confirmStop = false }, title = { Text("End this timer?") },
            text = { Text("This session will not count as completed.") },
            confirmButton = { TextButton(onClick = { confirmStop = false; onStop() }) { Text("End timer") } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going") } })
    }
}

private data class ClockBinding(val timer: Countdown, val boot: Int, val foreground: Boolean, val environmentRevision: Int)

/** Android owns the one-second text ticks; no Compose loop, service or wake lock. */
@Composable
private fun NativeCountdown(timer: Countdown, boot: Int, foreground: Boolean, environmentRevision: Int, sizeSp: Float, modifier: Modifier,
    onExpired: () -> Unit) {
    val expired by rememberUpdatedState(onExpired)
    AndroidView(factory = { context ->
        Chronometer(context).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color(0xFFF1F5ED).toArgb())
            fontFeatureSettings = "tnum"
            setSingleLine(true)
        }
    }, modifier = modifier, onRelease = { it.stop(); it.onChronometerTickListener = null }, update = { clock ->
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        val binding = ClockBinding(timer, boot, foreground, environmentRevision)
        if (clock.tag != binding) {
            clock.tag = binding
            clock.onChronometerTickListener = null
            clock.stop()
            val remaining = timer.remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot).coerceAtLeast(0)
            clock.isCountDown = true
            clock.base = countdownChronometerBase(remaining, SystemClock.elapsedRealtime(), android.os.Build.VERSION.SDK_INT >= 36)
            if (timer.isPaused || remaining == 0L) clock.text = formatCountdown(remaining)
            var delivered = false
            clock.setOnChronometerTickListener {
                if (timer.remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot) <= 0) {
                    clock.stop()
                    clock.text = formatCountdown(0)
                    if (!delivered) { delivered = true; expired() }
                }
            }
            if (foreground && !timer.isPaused && remaining > 0) clock.start()
        }
    })
}
