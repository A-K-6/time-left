package dev.calendarquiet.data

import android.content.Context
import dev.calendarquiet.core.Rule
import org.json.JSONArray
import org.json.JSONObject

/** Small local configuration; notification contents are never stored. */
class Store(context: Context) {
    val prefs = context.getSharedPreferences("calendar_quiet", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).commit() }

    fun rules(): List<Rule> {
        val raw = prefs.getString("rules", null) ?: return listOf(Rule(
            id = "initial-focus", name = "Focus", marker = "#focus",
            leadMinutes = 5, reminderText = "Time to focus: {event}", reminderLeadMinutes = 10,
        ))
        return parseArray(raw).map { json ->
            val reminder = json.optString("reminderText")
            val packages = json.optJSONArray("hiddenPackages").stringSet()
            Rule(
                id = json.getString("id"),
                name = json.getString("name"),
                marker = json.getString("marker"),
                calendarIds = json.optJSONArray("calendarIds").longSet(),
                leadMinutes = json.optInt("leadMinutes"),
                reminderText = reminder,
                reminderLeadMinutes = json.optInt("reminderLeadMinutes", 10),
                hiddenPackages = packages,
                hideMinutes = if (json.isNull("hideMinutes")) null else json.getInt("hideMinutes"),
                // Leave sound-only preview rules visible and editable after the upgrade.
                enabled = json.optBoolean("enabled", true) && (reminder.isNotBlank() || packages.isNotEmpty()),
            )
        }
    }

    fun saveRules(rules: List<Rule>) {
        require(rules.all { it.validationError(allowDisabledWithoutActions = true) == null })
        val array = JSONArray()
        rules.forEach { rule -> array.put(JSONObject().apply {
            put("id", rule.id); put("name", rule.name); put("marker", rule.marker)
            put("calendarIds", JSONArray(rule.calendarIds.toList()))
            put("soundMode", "UNCHANGED"); put("leadMinutes", rule.leadMinutes)
            put("reminderText", rule.reminderText); put("reminderLeadMinutes", rule.reminderLeadMinutes)
            put("hiddenPackages", JSONArray(rule.hiddenPackages.toList()))
            put("hideMinutes", rule.hideMinutes ?: JSONObject.NULL); put("enabled", rule.enabled)
        }) }
        check(prefs.edit().putString("rules", array.toString()).commit()) { "Could not save rules." }
    }

    fun delivered(now: Long): Map<String, Long> = longMap("delivered").filterValues { it > now }
    fun markDelivered(key: String, end: Long, now: Long) = putLongMap("delivered", delivered(now) + (key to end))
    fun snoozeWindows(): Map<String, Long> = longMap("snooze_windows")
    fun setSnoozeWindows(windows: Map<String, Long>) = putLongMap("snooze_windows", windows)

    private fun longMap(key: String): Map<String, Long> {
        val json = JSONObject(prefs.getString(key, "{}") ?: "{}")
        return json.keys().asSequence().associateWith { json.getLong(it) }
    }
    private fun putLongMap(key: String, values: Map<String, Long>) {
        prefs.edit().putString(key, JSONObject(values).toString()).commit()
    }
    private fun parseArray(raw: String): List<JSONObject> {
        val array = JSONArray(raw)
        return List(array.length()) { array.getJSONObject(it) }
    }
    private fun JSONArray?.stringSet() = this?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() } ?: emptySet()
    private fun JSONArray?.longSet() = this?.let { array -> (0 until array.length()).map { array.getLong(it) }.toSet() } ?: emptySet()
}
