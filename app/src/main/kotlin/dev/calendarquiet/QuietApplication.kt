package dev.calendarquiet

import android.app.Application
import android.content.Context
import dev.calendarquiet.automation.Automation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuietApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val automation by lazy { Automation(this) }
    val timers by lazy { dev.calendarquiet.timer.TimerCoordinator(this) }
    val uiCalendars by lazy { dev.calendarquiet.data.UiCalendars(dev.calendarquiet.data.DeviceCalendars(this)) }
    override fun onCreate() {
        super.onCreate()
        scope.launch { timers.refresh() }
    }
}
val Context.quietApp: QuietApplication get() = applicationContext as QuietApplication
