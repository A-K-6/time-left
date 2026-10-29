# Time Left

A small, local Android timer for Pomodoros, calendar events and free time. Built for Android 13+ with Kotlin and Compose Material 3. The app keeps the original `dev.calendarquiet` package so an upgrade retains existing settings; the earlier Calendar Quiet tools remain available.

## Timers

The **Timer** tab is the home screen. Nothing starts automatically:

- **Pomodoro:** choose focus and break lengths (1–180 minutes), start either, and see completed focus sessions for today. Each next session starts with a tap. A short focus session can fit the gap before your next busy event without changing your usual focus length.
- **Current event:** choose an event already in progress and count down to its end. Overlapping events are offered individually.
- **Free time:** count down to the next timed busy event, or choose a custom end time. Calendar selection is independent of quiet rules. Declined, all-day, cancelled and explicitly free events do not block free time. Suggestions look ahead seven days; an empty calendar is not presented as unlimited free time.

One active timer is shared by the app, an ongoing notification, the lock screen (subject to system settings), and a resizable **Time Left** home-screen widget. Add it from the Timer page or the launcher's widget picker. A basic Dismiss action clears an active timer without counting it as completed. Pause/resume and automatic Pomodoro cycles are deferred.

Allow notifications to see the timer outside the app and receive a finish notification. Allow **Alarms & reminders** for precise completion. Without this access, Android may delay the finish alert and the widget's completed state; the ongoing notification expires at the deadline. Timer cues play once using alarm volume and still obey your phone’s Do Not Disturb settings. Open **Sounds & vibration** on the Timer page to customize separate focus, break and calendar-timer sounds, test each tone, and enable or disable cues at session start and timer finish. Tone and vibration choices are stored in Android notification settings. Explicit sound/importance choices from the previous finish-alert channel are preserved when upgrading.

Timers are saved before scheduling. Duration timers use elapsed realtime; calendar/free-time timers use a wall-clock deadline. Boot, clock changes, app upgrades and granting exact-alarm access reconcile active timers. Revoking exact-alarm access can stop the process and cancel alarms; reopen the app to reconcile using the remaining access. The app does not run a per-second background service: Android renders notification/widget chronometers, and one AlarmManager callback handles completion. Force-stopping the app prevents callbacks until it is opened again.

Calendar-derived timers capture the deadline at Start. Calendar edits refresh suggestions while the Timer page is open, but do not move a running timer's deadline in this base version. Calendar sync freshness is owned by the phone's calendar account. A duration timer recovered after a reboot uses its last saved wall-clock deadline.

Open **Calendar → Change → Use Google calendars** to use your Google calendars for both the calendar views and Timer suggestions. The source picker shows each account and its provider, including calendars hidden in the phone's calendar display. Explicit selections read those calendars' stored events regardless of phone visibility. The default **Use all visible calendars** follows the phone's visibility and sync flags. A sync-disabled calendar can show cached events when explicitly selected, but its account must resume syncing to receive updates. Empty or unavailable selections never fall back to another account. Quiet rules retain their independent selections.

**Refresh** reloads Android's stored calendar events. It does not fetch directly from Google. **Account sync settings** opens Android's calendar-account settings; enable Calendar sync for the correct Google account there. Source lists and events reload on return and when Android reports calendar changes. Google Calendar, Xiaomi Calendar and other apps can share the same Android Calendar Provider; account names and provider labels identify which calendar owns each event. If Google calendars are absent from the picker, check their device sync first. Data held only inside another app or on the web cannot be read through the provider.

This version implements standard Android notifications and widgets. The ordinary status bar shows the app's timer icon; a numeric status chip, Xiaomi HyperIsland integration, and always-on-display behavior are not claimed. Physical Xiaomi/Samsung behavior still needs device validation.

## Optional quiet rules

Add a marker such as `#focus` to an event title or description in Google Calendar, then configure the matching rule in Calendar Quiet:

- Switch to **Vibrate** or **Silent** a chosen number of minutes before the event; release the sound action at the event's end.
- Show a **custom reminder** before the event. `{event}` inserts the event title.
- **Snooze ordinary notifications from selected apps** until the scheduled event end or for a fixed duration measured from rule activation, capped at the event end.

Rules can match all visible synced calendars or a selected subset. Matching is case-insensitive and uses whole markers: `#focus` does not match `#focused`. Recurring occurrences and calendar edits are read through Android's Calendar Provider. All-day, cancelled and declined events are excluded from automation.

The **Calendar** tab opens in a scrollable **Day** timeline with hourly lines, calendar-colored event blocks, side-by-side overlaps, an all-day strip and a current-time indicator. Previous/next day and Today navigation are available; the **Month** toggle keeps the month grid and agenda. Select a date in Month to open its day. Tap a timeline event for details, its calendar account, quiet-rule status and, for an event underway, a countdown action. Both views use the same calendar sources as Timer suggestions, including events without rules, all-day events and declined invitations. It works while automation is paused. Select a day to see each matching rule's scheduled, active, disabled or paused state, sound mode, app snoozing and quiet window; tap a rule to edit it. All-day and declined events remain visible but do not activate rules. Cancelled events are omitted. Missing action permissions are shown alongside the rule, and an active rule does not guarantee that every optional action succeeded.

Overlapping sound rules use Silent over Vibrate. Notification hiding uses the latest active deadline for each selected app. Manual ringer changes pause sound automation through the connected set of overlapping sound events. Other Do Not Disturb rules are preserved.

## Build

Requirements: JDK 17, Android SDK platform 36 and Build Tools 35.0.0. Android Studio can install the SDK packages and configure the SDK path. Alternatively set `ANDROID_HOME` or an untracked `local.properties` containing `sdk.dir=/your/android/sdk`.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Release shrinking is enabled; release signing is intentionally left for the owner's signing configuration. The local timer preview is `artifacts/time-left-preview.apk`, an optimized release signed with the development debug key for device testing. It is not a production-signed release.

Verified on 2026-10-29: all 39 core tests passed; debug and optimized release builds succeeded; Android lint reported zero errors. APK signing and 16 KiB ZIP alignment were verified. See [timer validation](docs/timer-base-validation.md) and [day-view/sound validation](docs/day-view-sound-validation.md) for emulator checks and remaining device work. No physical-device or battery measurements have been performed.

## Optional quiet-rule setup

1. Install and open the app. Automation starts paused.
2. In **Setup**, allow calendar access. Enable Google Calendar sync on the phone and make the calendar visible in the calendar app.
3. Enable only the optional access needed for your actions: Do Not Disturb for sound mode, notifications for reminders, notification access for snoozing, and Alarms & reminders for precise timing.
4. Edit the starter Focus rule or add a rule. Select calendars, timing, reminder text and apps to snooze.
5. Put the marker in an event, turn automation on, and inspect **Schedule**. Enabling automation also applies rules to events already underway.

Calendar Quiet reads calendar data already synced to Android. It has no Google OAuth client, backend, Internet permission, analytics, accessibility service or busy polling loop. Sync freshness depends on the calendar account's own sync settings.

## Background behavior

- One AlarmManager callback handles the next event/reminder/hiding boundary.
- A JobScheduler content-URI trigger reconciles calendar edits; a batched six-hour job provides a fallback refresh.
- Boot, clock/time-zone changes, app upgrades and access changes rebuild the schedule.
- The notification listener reacts to posted notifications and checks small cached package/deadline settings. Notification bodies are not saved.
- Rules and runtime settings use a small private SharedPreferences store; no database or dependency-injection framework is needed for this first version.

Silent uses an app-owned automatic Do Not Disturb rule to block calls and notification sound/vibration, while leaving media and alarms allowed. Vibrate uses Android's ringer mode and does not override another active DND rule. Calendar Quiet never switches off someone else's DND rule.

## Platform limits

- Without exact-alarm access, Android may delay sound changes and reminders. Even exact alarms are subject to idle-mode frequency limits. This app uses `SCHEDULE_EXACT_ALARM`, not the restricted auto-granted `USE_EXACT_ALARM` permission.
- A notification listener acts **after posting**. Snoozing does not guarantee that an initial sound or pop-up never happens. Sound-mode rules are independent.
- Ongoing/foreground-service notifications, non-clearable notifications, group summaries, calls, alarms, system notifications and media controls are excluded from snoozing.
- Android handles timed notification return. Notifications cancelled by the originating app may not return. Pausing, removing a rule or shortening/cancelling an event does **not** end an existing snooze early. Extending an event takes effect when notifications return and can be snoozed again.
- Notifications in a separate work profile may be inaccessible. Device policy and OEM behavior can impose further restrictions.
- A force-stop prevents automation until the app is opened again. Permission revocation can also prevent actions; Setup and status messages expose missing access.
- If a reminder is more than five minutes late, it is skipped rather than producing an old alert on the next refresh.
- This build has not yet been validated on physical Samsung/Xiaomi devices. Battery consumption and background reliability need device measurement; no battery-use figure is claimed.

## Device verification

Use short test events in a calendar you control. Check each case on both Samsung One UI and Xiaomi HyperOS, with the screen off as well as on:

1. Start from normal ringer mode; verify a Vibrate event activates and restores it. Repeat from a manually selected silent mode.
2. Verify Silent suppresses call/notification sound and vibration, while alarms/media and separate DND rules retain their settings.
3. Change the ringer or deactivate the app's DND rule manually during an event; verify later refreshes do not overwrite the decision.
4. Overlap Silent and Vibrate events, including a chain of overlaps; verify transitions and final restoration.
5. Send an ordinary notification from a selected app before and during an event; verify snoozing and timed return. Confirm unselected apps and ongoing notifications remain visible.
6. Edit, delete, move and repeat events; check a single reminder per occurrence and the existing-snooze limitation.
7. Reboot, change time zone, revoke/regrant each access and test with exact-alarm access disabled.
8. Check behavior with another DND mode active, device battery saver, and manufacturer background restrictions.

On Samsung, inspect Sleeping/Deep sleeping apps if scheduled actions are missed. On Xiaomi, inspect background autostart and per-app battery restrictions. Exact menu names vary; the app links to its Android settings. Change these settings only if testing demonstrates a need.

## Code layout

- `core/.../Timers.kt`: countdown clocks, completion counting and calendar suggestions.
- `app/.../timer/`: persistent timer state, alarms, notifications and the widget.
- `app/.../ui/TimerPage.kt`: timer-first Compose UI.

- `core/`: Android-independent marker matching, rule validation, overlap calculation, timing and reminder deduplication, with JVM tests.
- `app/.../data/`: local rules and calendar-provider queries.
- `app/.../automation/`: scheduling, sound control, reminders and notification snoozing.
- `app/.../ui/`: Material rule editor, schedule and setup.

Official API references: [Calendar Provider](https://developer.android.com/identity/providers/calendar-provider), [Alarm scheduling](https://developer.android.com/develop/background-work/services/alarms), [Notification snoozing](https://developer.android.com/reference/android/service/notification/NotificationListenerService#snoozeNotification(java.lang.String,%20long)), [Automatic DND rules](https://developer.android.com/reference/android/app/NotificationManager#setAutomaticZenRuleState(java.lang.String,%20android.service.notification.Condition)).
