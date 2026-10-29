package dev.calendarquiet.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.calendarquiet.quietApp
import kotlinx.coroutines.launch

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.quietApp.scope.launch {
            try { context.quietApp.timers.refresh() }
            finally { pending.finish() }
        }
    }
    companion object { const val REFRESH = "dev.calendarquiet.TIMER_REFRESH" }
}
