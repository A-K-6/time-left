# Time Left v0.3.0 — Pomodoro and native widgets

Verified 2026-11-13. Android package `dev.calendarquiet`, version `0.3.0`, version code `6`, minimum Android API 33, target/compile API 36. The release is an optimized, development-signed APK compatible with the earlier preview signing identity. No physical phone was installed or modified.

## What ships

- A new light/dark visual system: warm paper and forest colors, fine clock numerals, a thin tick-mark dial, compact navigation with consistent outline icons, phase selection and a single primary timer action. The fullscreen focus view follows the same design. System font scaling is retained.
- Configurable focus/short-break/long-break durations and a long break after a selected number of completed focuses. Defaults are 25/5/15 minutes and four focuses. The next session always requires a tap.
- Pause/resume and +1 minute for duration timers. A paused timer keeps its remaining time through process loss and reboot and has no completion alarm. Calendar and free-time deadlines cannot be paused or extended.
- Optional focus labels, 15/25/50-minute quick starts, cycle progress and a fullscreen view with optional Keep screen on. Quick starts preserve the saved normal focus length.
- Two native RemoteViews widgets: timer controls and Quick Focus. Layouts adapt to the actual available size, including a horizontal layout and narrower existing placements. Android/launcher grid sizing varies; the larger widget exposes a second action when space permits.
- Pause/Resume and Stop on the ongoing notification; a next-session button on the finish notification. Immutable PendingIntents include a persistent state revision, so old/repeated commands cannot act on a replacement timer.
- Upgrade migration preserves v0.2.2 duration choices, focus history, calendar selections and existing sound channels. Long breaks use the existing break sound channel.

The shipped Kotlin/Compose, icons, dial and widget layouts are the project's own implementation; no reference APK code or assets were reused.

## Build and static verification

Used JDK 17, Gradle 8.13, Android SDK 36 and Build Tools 35.0.0 from the cached local toolchain. Ran:

```sh
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease
```

All **59 core tests pass**: 45 existing tests plus 14 Pomodoro regressions. Cases cover pause freezing, reboot/clock changes, identity/duration preservation on resume, extension bounds, calendar deadline protection, completed-focus deduplication, variable break intervals, skipped long breaks and focus labels. Debug and optimized release builds pass. Android lint reports **zero errors**; 44 warnings remain, including pre-existing/dependency warnings and native widget preview/style suggestions. No warning was suppressed to obtain the result.

APK signature verification and 16 KiB ZIP alignment pass. Certificate SHA-256 is `2fafa7cd50c089f8b8e7b0a1b8e14db0cfda779ea7ab4bfa024b578f2880e296`, matching the earlier preview. Final APK SHA-256: `43b21fb7990f8c6351cb0b10a1a1039c9d817b3f2fbd6f446864eec7192a88b7`.

## Android runtime checks

Used an isolated AOSP Android 15 / API 35 emulator with synthetic local data.

The dependency-free `TimerSmokeRunner` exercises actual Android preferences, notifications and a real receiver PendingIntent. All **nine checks pass**:

1. Repeated Start cannot replace a running timer.
2. Pause freezes and persists remaining time; the real Pause PendingIntent reaches the receiver.
3. The paused notification uses static remaining time and offers Resume.
4. Reload/resume/extend preserve timer identity and reject a stale Pause command.
5. Stop preserves existing completion history; old Stop/Start commands cannot affect a replacement timer.
6. Calendar deadlines cannot be paused or extended.
7. The next-session action selects a long break after the fourth focus.
8. The v0.2.2 JSON schema migrates without resetting settings or history.
9. An elapsed persisted timer settles once and its finish notification offers the next short break.

Reproduce on a **disposable emulator only** after building debug and androidTest APKs:

```sh
ANDROID_HOME=/path/to/sdk scripts/emulator-smoke.sh emulator-5554
```

The script refuses a physical-device serial, checks the runner's result instead of trusting `am instrument`'s exit code, and stops the emulator app after restoring the fixture settings. These are focused smoke checks, not a general instrumentation-test framework.

Additional observed checks:

- Started a real one-minute focus through the app, paused it, killed the background process and rebooted the emulator. The same timer ID, exact paused remaining milliseconds and focus-history count survived.
- Resumed that timer from the actual launcher widget. With the app in the background, its process killed and screen off, AlarmManager completed it once. The finish notification used the focus sound channel, included the next-break action, and the widget updated. This establishes emulator delivery, not physical-speaker audibility.
- Pinned both widget providers and inspected their actual launcher views. Checked active, paused and ready states and repeated background updates. The implementation supplies narrow vertical and short, wide horizontal variants; launcher grid allocation remains launcher-controlled.
- Inspected light and dark UI, a 320dp-wide display, and enlarged system text (font scale 1.3). Exercised the redesigned long-break selector, Start, Pause, End session confirmation, +1 minute, fullscreen view and return to the Timer tab from Calendar using the notification/widget intent.
- Saved 25/5/15/4 through the actual settings dialog and confirmed the shared persisted settings.
- Installed the optimized signed APK over the test build to verify upgrade startup and retained settings. No physical-device installation was attempted.

Source/diff review was performed directly. Two attempted optional Pi reviews were unavailable (saved Codex login invalid, then the approved Antigravity co-thinking account at quota); neither is counted as review evidence.

Local logs, screenshots, preference snapshots and signature output are retained under ignored `artifacts/pomodoro-validation/` and the task's local validation directory. Release assets include the installable APK and checksum; the release tag identifies the matching source.

## Remaining device boundaries

Physical Samsung/Xiaomi/other OEM background behavior, battery use, lock-screen placement and speaker audibility remain unmeasured. Exact-alarm and notification permission, channel choices, alarm volume and Do Not Disturb still govern delivery. Force-stop prevents scheduled callbacks until reopening. A running duration recovered after reboot uses its saved wall deadline; paused time is stored directly. Calendar edits do not move an already started calendar timer. No HyperIsland, numeric status chip, count-up mode, automatic cycling or reference-app blocking behavior is claimed.
