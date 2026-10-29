# Timer base validation — 2026-10-29

The Android package in Time Left 0.2.0 (version code 3) remains `dev.calendarquiet` for upgrades. No remote release or production signing was performed.

## Build and core checks

Used Gradle 8.13, JDK 17, SDK platform 36 and Build Tools 35.0.0 for:

```text
:core:test :app:assembleDebug :app:lintDebug :app:assembleRelease
```

All 28 core tests passed: 12 timer tests, 12 rule-engine tests and 4 calendar-date tests. Timer coverage includes elapsed time versus wall time, clock correction followed by reboot, completion deduplication, local-day counting, overlap handling, calendar selection, free/declined/all-day events and invalid durations. Lint finished with zero errors; dependency and style warnings remain.

The optimized APK is `artifacts/time-left-preview.apk`, signed with the existing development debug key. Signature verification and `zipalign -c -P 16 4` passed. The certificate SHA-256 is `2fafa7cd50c089f8b8e7b0a1b8e14db0cfda779ea7ab4bfa024b578f2880e296`, matching the earlier preview. `artifacts/timer-validation/` holds local screenshots and inspection output.

## Android emulator checks

Used an isolated AOSP Android 15 / API 35 emulator at 360dp width. Calendar fixtures were synthetic and local to that emulator.

- Created a busy event already in progress and started its countdown through the UI. The saved deadline matched the event end.
- Deleted that event. The refreshed suggestion offered free time until the next busy meeting, ignoring an earlier event marked free.
- Started the free-time timer and observed the native notification and home-screen widget countdowns.
- Killed the background app process with `am kill`, preserving scheduled work. The notification remained, and reopening restored the same timer.
- Enabled lock-screen display of silent notifications and observed the countdown on the actual emulator lock screen. Visibility is controlled by system settings.
- Started a one-minute focus session, went home, killed the app process and switched the screen off. The scheduled alarm completed the session, persisted exactly one focus completion and posted the finish notification without reopening the app. The widget changed to the completed state; reopening offered the next session without starting it automatically.
- Installed the optimized, development-signed APK over the debug build. Existing completion history survived and the optimized build started a break countdown.
- Inspected the UI at 320dp width with font scale 1.3. The screen remains scrollable; navigation labels use a single line with ellipsis when necessary.

The earlier full-size widget was reduced to a smaller initial footprint after inspecting it in the launcher. Starting or restoring a timer now scrolls its countdown into view.

## Remaining limits

- No physical Xiaomi/Samsung test, Doze stress test or battery measurement was performed. An emulator screen-off check does not establish OEM background reliability.
- Xiaomi HyperIsland and numeric status chips are deferred. Standard Android notifications, the lock-screen notification and the home-screen widget are implemented.
- Calendar-derived deadlines are captured at Start. Later calendar edits update suggestions but do not move a running timer.
- Exact-alarm access is required for precise completion. Without it, the alert and widget's completed state may be late. Force-stop and alarm-access revocation can prevent callbacks until the app is reopened.
- A basic Dismiss action is provided; pause/resume, automatic cycles and advanced stop behavior are deferred.
