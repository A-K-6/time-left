package dev.calendarquiet.timer

import android.content.Context
import dev.calendarquiet.core.Countdown
import dev.calendarquiet.core.TimerKind
import dev.calendarquiet.core.TimerState
import dev.calendarquiet.core.FocusProgress
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

class TimerStore(context: Context) {
    private val prefs = context.getSharedPreferences("timers", Context.MODE_PRIVATE)

    fun read(): TimerState = runCatching {
        val json = JSONObject(prefs.getString("state", "{}") ?: "{}")
        val history = json.optJSONArray("focusFinishedAt") ?: JSONArray()
        TimerState(json.optJSONObject("active")?.toCountdown(), json.optJSONObject("completed")?.toCountdown(),
            List(history.length()) { history.getLong(it) },
            json.optInt("focusMinutes", 25).coerceIn(1, 180), json.optInt("breakMinutes", 5).coerceIn(1, 180),
            json.optInt("longBreakMinutes", 15).coerceIn(1, 180),
            json.optInt("longBreakEvery", 4).coerceIn(1, 12),
            json.optInt("focusesInCycle", 0).coerceIn(0, 12), json.optLong("revision", 0),
            json.optJSONObject("progress")?.let { readProgress(it) })
    }.getOrDefault(TimerState())

    fun write(state: TimerState) {
        val json = JSONObject().put("focusMinutes", state.focusMinutes).put("breakMinutes", state.breakMinutes)
            .put("focusFinishedAt", JSONArray(state.focusFinishedAt))
            .put("longBreakMinutes", state.longBreakMinutes).put("longBreakEvery", state.longBreakEvery)
            .put("focusesInCycle", state.focusesInCycle).put("revision", state.revision)
        state.progress?.let { progress ->
            val days = JSONObject()
            progress.days.toSortedMap().forEach { (day, count) -> days.put(day.toString(), count) }
            json.put("progress", JSONObject().put("trackedFrom", progress.trackedFrom.toString())
                .put("dailyGoal", progress.dailyGoal).put("days", days))
        }
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

    private fun readProgress(json: JSONObject): FocusProgress? {
        val raw = json.optJSONObject("days") ?: return null
        val days = buildMap {
            raw.keys().forEach { key ->
                val day = runCatching { LocalDate.parse(key) }.getOrNull()
                val count = raw.optInt(key, 0)
                if (day != null && count > 0) put(day, count)
            }
        }
        val since = runCatching { LocalDate.parse(json.getString("trackedFrom")) }.getOrNull()
            ?: days.keys.minOrNull() ?: LocalDate.now()
        return FocusProgress(since, days, json.optInt("dailyGoal", 4).coerceIn(1, 12))
    }

    private fun Countdown.toJson() = JSONObject().put("id", id).put("kind", kind.name).put("title", title)
        .put("startedAt", startedAt).put("endsAt", endsAt).put("elapsedDeadline", elapsedDeadline).put("bootCount", bootCount).put("pausedRemaining", pausedRemaining)

    private fun JSONObject.toCountdown() = Countdown(getString("id"), TimerKind.valueOf(getString("kind")),
        getString("title"), getLong("startedAt"), getLong("endsAt"),
        if (isNull("elapsedDeadline")) null else getLong("elapsedDeadline"), optInt("bootCount", -1), if (isNull("pausedRemaining")) null else getLong("pausedRemaining"))
}
