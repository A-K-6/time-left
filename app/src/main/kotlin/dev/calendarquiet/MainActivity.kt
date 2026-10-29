package dev.calendarquiet

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.calendarquiet.ui.QuietScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var revision by mutableIntStateOf(0)
    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { revision++; refresh() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { revision++; refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = colors) {
                QuietScreen(this, revision, ::requestAccess)
            }
        }
    }
    override fun onResume() { super.onResume(); revision++; refresh() }
    private fun refresh() {
        quietApp.scope.launch { quietApp.automation.reconcile() }
        quietApp.scope.launch { quietApp.timers.refresh() }
    }

    private fun requestAccess(kind: String) {
        val prefs = quietApp.automation.store.prefs
        when (kind) {
            "calendar" -> {
                if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED ||
                    prefs.getBoolean("calendar_requested", false) && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
                    openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                } else {
                    prefs.edit().putBoolean("calendar_requested", true).apply()
                    calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                }
            }
            "reminders" -> {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    (!prefs.getBoolean("reminders_requested", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                    prefs.edit().putBoolean("reminders_requested", true).apply()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
            "focus_sound" -> openSettings(dev.calendarquiet.timer.TimerNotifications(this).settingsIntent(dev.calendarquiet.core.TimerKind.FOCUS))
            "break_sound" -> openSettings(dev.calendarquiet.timer.TimerNotifications(this).settingsIntent(dev.calendarquiet.core.TimerKind.BREAK))
            "calendar_timer_sound" -> openSettings(dev.calendarquiet.timer.TimerNotifications(this).settingsIntent(dev.calendarquiet.core.TimerKind.EVENT))
            "alarm_volume" -> openSettings(Intent(Settings.ACTION_SOUND_SETTINGS))
            "calendar_sync" -> openSettings(Intent(Settings.ACTION_SYNC_SETTINGS)
                .putExtra(Settings.EXTRA_AUTHORITIES, arrayOf(android.provider.CalendarContract.AUTHORITY)))
            "sound" -> openSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            "snooze" -> openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(this, dev.calendarquiet.automation.QuietNotificationListener::class.java).flattenToString()))
            "timing" -> openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            "battery" -> openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
    private fun openSettings(intent: Intent) {
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "Open Android Settings to grant this access.", Toast.LENGTH_LONG).show() }
        }
    }
}
