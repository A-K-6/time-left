package dev.calendarquiet

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import dev.calendarquiet.automation.Automation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuietApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val automation by lazy { Automation(this) }
    val timers by lazy { dev.calendarquiet.timer.TimerCoordinator(this) }
    override fun onCreate() {
        super.onCreate()
        scope.launch { timers.refresh() }
        // This receiver exists only while Android has a reason to keep our process alive.
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                scope.launch { automation.observeRingerChange() }
            }
        }, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION), Context.RECEIVER_EXPORTED)
    }
}
val Context.quietApp: QuietApplication get() = applicationContext as QuietApplication
