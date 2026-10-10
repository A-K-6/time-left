package dev.calendarquiet.automation

import android.app.NotificationManager
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
                if (intent.action == NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED) {
                    val store = context.quietApp.automation.store
                    val id = intent.getStringExtra(NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_ID)
                    val status = intent.getIntExtra(NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_STATUS, -1)
                    // 5 is DEACTIVATED (API 35); disabled/removed rules also represent a user decision.
                    if (id == store.prefs.getString("zen_id", null) && status in setOf(2, 3, 5)) {
                        context.quietApp.automation.manualSoundOverride()
                    }
                }
                context.quietApp.automation.reconcile()
            } finally { pending.finish() }
        }
    }
}
