package dev.calendarquiet.automation

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import dev.calendarquiet.data.Store

/** Release sound settings left behind by older versions of Time Left. */
internal class LegacySoundCleanup(context: Context, store: Store) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val prefs = store.prefs
    private val condition = Uri.parse("condition://${context.packageName}/calendar-quiet")
    private var finished = false

    fun clear(): Boolean {
        if (finished) return true
        // Attempt both restorations. Either one can be blocked independently.
        val silentCleared = runCatching { clearSilentRule() }.getOrDefault(false)
        val ringerRestored = runCatching { restoreRinger() }.getOrDefault(false)
        finished = silentCleared && ringerRestored
        return finished
    }

    private fun clearSilentRule(): Boolean {
        if (!notifications.isNotificationPolicyAccessGranted) {
            return !prefs.contains("zen_id") && !prefs.getBoolean("silent_active", false)
        }
        val ownRules = notifications.automaticZenRules.filterValues { it.conditionId == condition }
        for (id in ownRules.keys) {
            if (!notifications.removeAutomaticZenRule(id) && notifications.getAutomaticZenRule(id) != null) return false
        }
        return prefs.edit().remove("zen_id").remove("silent_active").commit()
    }

    private fun restoreRinger(): Boolean {
        val applied = prefs.getInt("applied_ringer", -1)
        if (applied == -1) return true
        val previous = prefs.getInt("previous_ringer", -1)
        if (audio.ringerMode == applied && previous in AudioManager.RINGER_MODE_SILENT..AudioManager.RINGER_MODE_NORMAL) {
            // A different DND mode or a user change takes precedence over preview state.
            if (notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return false
            audio.ringerMode = previous
            if (audio.ringerMode != previous) return false
        }
        return prefs.edit().remove("applied_ringer").remove("previous_ringer")
            .remove("override_until").remove("sound_until").commit()
    }
}
