# Calendar Quiet

A native Android app that applies small, local rules to calendar events. Built for Android 13+ with Kotlin and Compose Material 3; intended for recent Samsung and Xiaomi phones.

## What it does

Add a marker such as `#focus` to an event title or description in Google Calendar, then configure the matching rule in Calendar Quiet:

- Switch to **Vibrate** or **Silent** a chosen number of minutes before the event; release the sound action at the event's end.
- Show a **custom reminder** before the event. `{event}` inserts the event title.
- **Snooze ordinary notifications from selected apps** until the scheduled event end or for a fixed duration measured from rule activation, capped at the event end.

Rules can match all visible synced calendars or a selected subset. Matching is case-insensitive and uses whole markers: `#focus` does not match `#focused`. Recurring occurrences and calendar edits are read through Android's Calendar Provider. All-day, cancelled and declined events are excluded from automation.

The **Calendar** tab shows a browsable month and a daily agenda of events from all visible synced calendars, including events without rules, all-day events and declined invitations. It works while automation is paused. Select a day to see each matching rule's scheduled, active, disabled or paused state, sound mode, app snoozing and quiet window; tap a rule to edit it. All-day and declined events remain visible but do not activate rules. Cancelled events are omitted. Missing action permissions are shown alongside the rule, and an active rule does not guarantee that every optional action succeeded.

Overlapping sound rules use Silent over Vibrate. Notification hiding uses the latest active deadline for each selected app. Manual ringer changes pause sound automation through the connected set of overlapping sound events. Other Do Not Disturb rules are preserved.

## Build

Requirements: JDK 17, Android SDK platform 36 and Build Tools 35.0.0. Android Studio can install the SDK packages and configure the SDK path. Alternatively set `ANDROID_HOME` or an untracked `local.properties` containing `sdk.dir=/your/android/sdk`.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Release shrinking is enabled; release signing is intentionally left for the owner's signing configuration.

The locally built prototype is at `artifacts/calendar-quiet-preview.apk`: the optimized release build signed with the development debug key for device testing. It is not a production-signed release. The unsigned optimized output remains at `app/build/outputs/apk/release/app-release-unsigned.apk`.

Verified on 2026-10-13: all 16 core tests passed, including calendar date-boundary and time-zone coverage; debug and optimized release APK builds succeeded; Android lint reported zero errors (remaining warnings are dependency-update and Kotlin/style suggestions). The optimized preview is approximately 2.21 MiB, and its APK signature and 16 KiB ZIP alignment were verified. No physical-device or battery measurements have been performed.

## First use

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

- `core/`: Android-independent marker matching, rule validation, overlap calculation, timing and reminder deduplication, with JVM tests.
- `app/.../data/`: local rules and calendar-provider queries.
- `app/.../automation/`: scheduling, sound control, reminders and notification snoozing.
- `app/.../ui/`: Material rule editor, schedule and setup.

Official API references: [Calendar Provider](https://developer.android.com/identity/providers/calendar-provider), [Alarm scheduling](https://developer.android.com/develop/background-work/services/alarms), [Notification snoozing](https://developer.android.com/reference/android/service/notification/NotificationListenerService#snoozeNotification(java.lang.String,%20long)), [Automatic DND rules](https://developer.android.com/reference/android/app/NotificationManager#setAutomaticZenRuleState(java.lang.String,%20android.service.notification.Condition)).
