package dev.calendarquiet.timer

import android.content.Context
import dev.calendarquiet.core.Countdown
import dev.calendarquiet.core.TimerKind
import dev.calendarquiet.core.TimerState
import org.json.JSONArray
import org.json.JSONObject

class TimerStore(context: Context) {
    private val prefs = context.getSharedPreferences("timers", Context.MODE_PRIVATE)

    fun read(): TimerState = runCatching {
        val json = JSONObject(prefs.getString("state", "{}") ?: "{}")
        val history = json.optJSONArray("focusFinishedAt") ?: JSONArray()
        TimerState(json.optJSONObject("active")?.toCountdown(), json.optJSONObject("completed")?.toCountdown(),
            List(history.length()) { history.getLong(it) },
            json.optInt("focusMinutes", 25).coerceIn(1, 180), json.optInt("breakMinutes", 5).coerceIn(1, 180))
    }.getOrDefault(TimerState())

    fun write(state: TimerState) {
        val json = JSONObject().put("focusMinutes", state.focusMinutes).put("breakMinutes", state.breakMinutes)
            .put("focusFinishedAt", JSONArray(state.focusFinishedAt))
        state.active?.let { json.put("active", it.toJson()) }
        state.completed?.let { json.put("completed", it.toJson()) }
        check(prefs.edit().putString("state", json.toString()).commit()) { "Could not save your timer." }
    }

    fun calendarIds(): Set<Long>? = prefs.getStringSet("calendar_ids", null)?.mapNotNull { it.toLongOrNull() }?.toSet()
    fun selectCalendars(ids: Set<Long>?) {
        check(prefs.edit().putStringSet("calendar_ids", ids?.map { it.toString() }?.toSet()).commit()) {
            "Could not save calendar selection."
        }
    }

    private fun Countdown.toJson() = JSONObject().put("id", id).put("kind", kind.name).put("title", title)
        .put("startedAt", startedAt).put("endsAt", endsAt).put("elapsedDeadline", elapsedDeadline).put("bootCount", bootCount)

    private fun JSONObject.toCountdown() = Countdown(getString("id"), TimerKind.valueOf(getString("kind")),
        getString("title"), getLong("startedAt"), getLong("endsAt"),
        if (isNull("elapsedDeadline")) null else getLong("elapsedDeadline"), optInt("bootCount", -1))
}
