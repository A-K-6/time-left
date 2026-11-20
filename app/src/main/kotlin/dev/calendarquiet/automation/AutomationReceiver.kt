package dev.calendarquiet.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.calendarquiet.quietApp
import kotlinx.coroutines.launch

class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.quietApp.scope.launch {
            try {
                context.quietApp.automation.reconcile()
            } finally { pending.finish() }
        }
    }
}
