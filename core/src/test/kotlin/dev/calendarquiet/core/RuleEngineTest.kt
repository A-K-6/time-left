package dev.calendarquiet.core

import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {
    private val now = 100 * HOUR
    private val rule = Rule(id = "r", name = "Focus", marker = "#focus", soundMode = SoundMode.VIBRATE)
    private fun event(start: Long = now, end: Long = now + HOUR, title: String = "Work #focus") =
        CalendarEvent(1, 7, title, "", start, end)

    @Test fun wholeMarkersAndCalendarScope() {
        assertTrue(RuleEngine.matches(rule, event(title = "Work #FOCUS!")))
        assertFalse(RuleEngine.matches(rule, event(title = "Work #focused")))
        assertFalse(RuleEngine.matches(rule, event(title = "Work #focus-extra")))
        assertFalse(RuleEngine.matches(rule.copy(calendarIds = setOf(8)), event()))
        assertTrue(RuleEngine.matches(rule, event(title = "Work").copy(description = "<p>#focus</p>")))
    }
    @Test fun ignoreAllDayDisabledAndInvalidEvents() {
        assertFalse(RuleEngine.matches(rule, event().copy(allDay = true)))
        assertFalse(RuleEngine.matches(rule.copy(enabled = false), event()))
        assertFalse(RuleEngine.matches(rule, event(end = now)))
    }
    @Test fun calendarCanIdentifyDisabledRulesWithoutEnablingAutomation() {
        val disabled = rule.copy(enabled = false, calendarIds = setOf(7))
        assertTrue(RuleEngine.matches(disabled, event(), includeDisabled = true))
        assertFalse(RuleEngine.matches(disabled, event().copy(calendarId = 8), includeDisabled = true))
        assertFalse(RuleEngine.matches(disabled, event(title = "#focused"), includeDisabled = true))
        assertFalse(RuleEngine.matches(disabled, event().copy(allDay = true), includeDisabled = true))
        assertFalse(RuleEngine.matches(disabled, event().copy(declined = true), includeDisabled = true))
        assertTrue(RuleEngine.sessions(listOf(disabled), listOf(event())).isEmpty())
        assertTrue(RuleEngine.sessions(listOf(rule), listOf(event().copy(declined = true))).isEmpty())
    }
    @Test fun startsBeforeEventAndEndsExclusively() {
        val sessions = RuleEngine.sessions(listOf(rule.copy(leadMinutes = 5)), listOf(event()))
        assertEquals(SoundMode.UNCHANGED, RuleEngine.snapshot(sessions, now - 6 * MINUTE).soundMode)
        assertEquals(SoundMode.VIBRATE, RuleEngine.snapshot(sessions, now - 5 * MINUTE).soundMode)
        assertEquals(SoundMode.UNCHANGED, RuleEngine.snapshot(sessions, now + HOUR).soundMode)
    }
    @Test fun silentWinsOverlapsAndVibrateResumesAfterwards() {
        val sessions = listOf(Session(rule, event()), Session(rule.copy(soundMode = SoundMode.SILENT), event(end = now + 20 * MINUTE)))
        assertEquals(SoundMode.SILENT, RuleEngine.snapshot(sessions, now).soundMode)
        assertEquals(SoundMode.VIBRATE, RuleEngine.snapshot(sessions, now + 20 * MINUTE).soundMode)
    }
    @Test fun snoozeDurationIsCappedAndMergesPerApp() {
        val first = Session(rule.copy(hiddenPackages = setOf("chat", "mail"), hideMinutes = 10), event())
        val second = Session(rule.copy(hiddenPackages = setOf("chat")), event(end = now + 30 * MINUTE))
        assertEquals(mapOf("chat" to now + 30 * MINUTE, "mail" to now + 10 * MINUTE), RuleEngine.snapshot(listOf(first, second), now).snoozeUntil)
        assertEquals(mapOf("chat" to now + 30 * MINUTE), RuleEngine.snapshot(listOf(first, second), now + 10 * MINUTE).snoozeUntil)
        assertEquals(now + HOUR, first.copy(rule = first.rule.copy(hideMinutes = 120)).hideEnd)
    }
    @Test fun recurringOccurrencesHaveDistinctReminderKeys() {
        val sessions = RuleEngine.sessions(listOf(rule.copy(reminderText = "Prepare")), listOf(event(), event(start = now + 24 * HOUR, end = now + 25 * HOUR)))
        assertNotEquals(sessions[0].reminderKey, sessions[1].reminderKey)
    }
    @Test fun remindersDoNotReplayAndStaleRemindersAreSkipped() {
        val session = Session(rule.copy(reminderText = "Prepare", reminderLeadMinutes = 10), event())
        val due = now - 10 * MINUTE
        assertEquals(listOf(session), RuleEngine.snapshot(listOf(session), due).reminders)
        assertTrue(RuleEngine.snapshot(listOf(session), due, setOf(session.reminderKey)).reminders.isEmpty())
        assertTrue(RuleEngine.snapshot(listOf(session), due + 5 * MINUTE).reminders.isEmpty())
    }
    @Test fun nextWakeIncludesReminderAndFixedHideEnd() {
        val session = Session(rule.copy(reminderText = "Prepare", reminderLeadMinutes = 10, hiddenPackages = setOf("chat"), hideMinutes = 15), event())
        assertEquals(now - 10 * MINUTE, RuleEngine.snapshot(listOf(session), now - 20 * MINUTE).nextWake)
        assertEquals(now + 15 * MINUTE, RuleEngine.snapshot(listOf(session), now).nextWake)
    }
    @Test fun manualOverrideCoversChainOfOverlappingEvents() {
        val sessions = listOf(Session(rule, event()), Session(rule, event(now + 30 * MINUTE, now + 2 * HOUR)), Session(rule, event(now + 3 * HOUR, now + 4 * HOUR)))
        assertEquals(now + 2 * HOUR, RuleEngine.soundClusterEnd(sessions, now))
        assertEquals(0, RuleEngine.soundClusterEnd(sessions, now + 2 * HOUR))
    }
    @Test fun deletedEventsRemoveAllActionsAndIdleRefreshIsBounded() {
        val snapshot = RuleEngine.snapshot(RuleEngine.sessions(listOf(rule), emptyList()), now)
        assertEquals(SoundMode.UNCHANGED, snapshot.soundMode)
        assertTrue(snapshot.snoozeUntil.isEmpty())
        assertEquals(now + 6 * HOUR, snapshot.nextWake)
    }
    @Test fun actionlessAndInvalidRulesCannotBeSaved() {
        assertNotNull(rule.copy(soundMode = SoundMode.UNCHANGED).validationError())
        assertNotNull(rule.copy(marker = "focus").validationError())
        assertNotNull(rule.copy(leadMinutes = -1).validationError())
        assertNotNull(rule.copy(hideMinutes = 0).validationError())
        assertNull(rule.copy(soundMode = SoundMode.UNCHANGED, reminderText = "Prepare").validationError())
    }
}
