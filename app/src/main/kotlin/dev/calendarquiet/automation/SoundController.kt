package dev.calendarquiet.automation

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import dev.calendarquiet.MainActivity
import dev.calendarquiet.core.SoundMode
import dev.calendarquiet.data.Store

class SoundController(private val context: Context, private val store: Store) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val prefs = store.prefs
    private val conditionUri = Uri.parse("condition://${context.packageName}/calendar-quiet")

    fun apply(mode: SoundMode, until: Long, now: Long) {
        prefs.edit().putLong("sound_until", until).commit()
        if (mode == SoundMode.UNCHANGED) {
            setSilent(false)
            releaseVibrate(restore = true)
            prefs.edit().remove("override_until").commit()
            return
        }
        if (prefs.getLong("override_until", 0) > now) {
            prefs.edit().putLong("override_until", until).commit()
            return
        }
        if (prefs.contains("applied_ringer") && audio.ringerMode != prefs.getInt("applied_ringer", -1)) {
            manualOverride(until)
            return
        }
        if (!notifications.isNotificationPolicyAccessGranted) {
            throw IllegalStateException("Allow Do Not Disturb access to automate sound mode.")
        }
        when (mode) {
            SoundMode.SILENT -> {
                releaseVibrate(restore = true)
                setSilent(true)
            }
            SoundMode.VIBRATE -> {
                setSilent(false)
                // Never turn off a separate DND rule just to enable vibration.
                if (notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    throw IllegalStateException("Another Do Not Disturb mode is active; vibration may stay blocked.")
                }
                if (prefs.contains("applied_ringer")) {
                    if (audio.ringerMode != prefs.getInt("applied_ringer", -1)) {
                        manualOverride(until)
                        return
                    }
                } else {
                    prefs.edit().putInt("previous_ringer", audio.ringerMode)
                        .putInt("applied_ringer", AudioManager.RINGER_MODE_VIBRATE).commit()
                }
                audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
            SoundMode.UNCHANGED -> Unit
        }
    }

    fun observeRingerChange() {
        if (prefs.contains("applied_ringer") && audio.ringerMode != prefs.getInt("applied_ringer", -1)) {
            manualOverride(prefs.getLong("sound_until", 0))
        }
    }

    fun manualOverride(until: Long) {
        prefs.edit().putLong("override_until", until).commit()
        releaseVibrate(restore = false)
        setSilent(false)
    }

    private fun releaseVibrate(restore: Boolean) {
        if (!prefs.contains("applied_ringer")) return
        val applied = prefs.getInt("applied_ringer", -1)
        val previous = prefs.getInt("previous_ringer", AudioManager.RINGER_MODE_NORMAL)
        // A user change must never be overwritten by the end-of-event restore.
        val shouldRestore = restore && audio.ringerMode == applied
        if (shouldRestore) audio.ringerMode = previous
        prefs.edit().remove("applied_ringer").remove("previous_ringer").commit()
    }

    private fun setSilent(active: Boolean) {
        if (active == prefs.getBoolean("silent_active", false)) return
        if (!notifications.isNotificationPolicyAccessGranted) {
            if (active) throw IllegalStateException("Allow Do Not Disturb access to use Silent.")
            if (prefs.getBoolean("silent_active", false)) throw IllegalStateException("Restore Do Not Disturb access to release the app's Silent rule.")
            return
        }
        var id = prefs.getString("zen_id", null)
        if (id != null && notifications.getAutomaticZenRule(id) == null) {
            prefs.edit().remove("zen_id").putBoolean("silent_active", false).commit()
            if (active) throw IllegalStateException("The Calendar Quiet sound rule was removed. Pause and resume automation to recreate it.")
            return
        }
        if (id == null && active) {
            val policy = ZenPolicy.Builder().disallowAllSounds().allowAlarms(true).allowMedia(true)
                .allowSystem(true).showAllVisualEffects().build()
            val rule = AutomaticZenRule("Calendar Quiet", null, ComponentName(context, MainActivity::class.java),
                conditionUri, policy, NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
            id = notifications.addAutomaticZenRule(rule)
                ?: throw IllegalStateException("Android could not create the sound rule.")
            prefs.edit().putString("zen_id", id).commit()
        }
        if (id != null) {
            val rule = notifications.getAutomaticZenRule(id)
            if (active && rule?.isEnabled != true) throw IllegalStateException("Enable the Calendar Quiet rule in Do Not Disturb settings.")
            notifications.setAutomaticZenRuleState(id,
                Condition(conditionUri, "Calendar event", if (active) Condition.STATE_TRUE else Condition.STATE_FALSE))
        }
        prefs.edit().putBoolean("silent_active", active).commit()
    }
}
