# Time Left v0.4.0 — countdown presentation and performance

Verified 2026-11-13. Package `dev.calendarquiet`, version `0.4.0`, code `7`, Android 13+ (API 33), target/compile API 36. Optimized R8 release; same development signing identity as v0.3.0. Published as an APK preview, without installing on a physical phone.

## Changes and safeguards

- Compact and expanded ongoing notifications now contain a real Android countdown Chronometer with prominent digits. Paused remaining time uses static text. The public lock-screen version hides the task title. Existing notification channels and their user sound/importance choices remain intact.
- Full screen is an immersive black clock with large native digits, countdown status, end time and direct timer controls. Android owns its visible text ticks; the underlying Compose dial suspends. Calendar/free-time countdowns also support full screen and retain their fixed, nonpausable deadlines.
- Keep screen on defaults off. Opting in only keeps an unpaused, foreground full-screen view awake; leaving, pausing or closing releases it. No timer foreground service or WAKE_LOCK permission was added.
- The main page no longer recomposes every second. Only running digits and dial drawing read the changing countdown. Tick geometry is cached as one Path. Suggestions update at relevant minute/event boundaries; paused/empty timer pages have no second ticker.
- UI clocks and queries use `repeatOnLifecycle(STARTED)`. Calendar ContentObserver registration follows lifecycle events directly and unregisters on Stop, even when background recomposition is paused. Installed-app enumeration happens only when opening the optional quiet-rule editor.
- The coordinator remembers its installed alarm deadline/type/access and published state. Repeated unchanged refreshes preserve those surfaces. Explicit widget updates repaint; boot, clock/access broadcasts and process startup reconcile alarms. Reopening restores a dismissed/missing running notification. Actual Start/Pause/Resume/Extend/Stop/completion changes still publish.

Timer persistence, monotonic duration deadlines, wall-clock calendar deadlines, immutable revision-bound actions, sound settings, history and tap-to-start next sessions retain their previous contracts. Display rounding offsets affect presentation only, never alarm deadlines.

## Build and regression checks

Used cached JDK 17, Gradle 8.13, SDK 36 and Build Tools 35.0.0. Ran core tests, debug and Android-test compilation, debug lint and optimized release assembly. **62 core tests pass**, including three countdown rounding/tick-boundary tests. **12 Android smoke checks pass** on AOSP Android 15 / API 35. Lint: **zero errors, 44 warnings**; no warning suppression was added.

The notification regression check fails against the pre-change binary with `Unchanged refresh reposted the running notification`. The upgraded coordinator preserves the actual NotificationManager post timestamp across eight unchanged refreshes, including explicit alarm reconciliation. Separate checks require explicit repaint, notification cancellation/restoration, native countdown content, redacted task titles and static paused time. Existing pause/reload/action-identity, calendar deadline, migration, long-break and completion-once checks remain passing. Notification delivery is asynchronous; the runner waits for the finish notification before inspecting it.

Reproduce smoke checks on a disposable emulator after building:

```sh
ANDROID_HOME=/path/to/sdk scripts/emulator-smoke.sh emulator-5554
```

The script refuses a physical-device serial. The test APK is not a release asset. Source/diff review was performed directly; the optional Pi read was unavailable because its saved Codex authentication returned 401, and is not counted as an independent review.

Android 16 changed native Chronometer rounding and tick alignment. SDK 36+ uses a separate display offset, checked against the tagged AOSP implementation and a reference tick model; the runtime emulator is API 35. This is source/model evidence for API 36, not an API 36 device test. Sources: [Android 15 Chronometer](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/widget/Chronometer.java), [Android 16 Chronometer](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/widget/Chronometer.java). The compact content fits Android's [48dp collapsed custom-notification limit](https://developer.android.com/about/versions/12/behavior-changes-12#custom-notifications).

## Measured work

Baseline: the published v0.3.0 optimized signed APK, SHA-256 `43b21fb7990f8c6351cb0b10a1a1039c9d817b3f2fbd6f446864eec7192a88b7`. Upgrade profiling build: the v0.4.0 optimized signed APK before the narrow large-text control adjustment, SHA-256 `68f1e4177d8cccdd347f6e80635bfa8ee2ad6316076a30bde3b42c789a6e27a6`.

Same owned AOSP API 35 emulator, 720×1600 pixels, density 320, font scale 1.0, synthetic calendar data, optional quiet automation paused. After startup/interaction settling, sample `/proc/PID/stat` and `/proc/PID/task/PID/stat` before and after each 30-second interval; `CLK_TCK=100`, so one tick is 10ms. Reset and read `dumpsys gfxinfo` for app frames. Screen-off intervals last 60 seconds with battery simulation unplugged and batterystats reset. No builds or competing UI automation run during samples. Calendar access remains granted so provider lifecycle behavior is exercised.

| State | Window | v0.3.0 app CPU | v0.4.0 profiling build CPU | Main thread, old → new | App frames, old → new |
| --- | ---: | ---: | ---: | ---: | ---: |
| idle | 30s | 70ms | 20ms | 50ms → 0ms | 1 → 0 |
| running | 30s | 300ms | 170ms | 60ms → 40ms | 30 → 31 |
| paused | 30s | 60ms | 0ms | 40ms → 0ms | 0 → 0 |
| fullscreen | 30s | 240ms | 130ms | 70ms → 30ms | 60 → 30 |
| background screen on | 30s | not sampled | 0ms | not sampled → 0ms | not sampled → 0 |
| screen off | 60s | 20ms | 0ms | 0ms → 0ms | — → — |

These are short emulator observations, not statistically stable benchmarks, device energy measurements or battery-percentage estimates. Zero means no additional counted 10ms CPU tick in that window. Native notification/widget rendering runs in system/launcher processes and is excluded from these app CPU counters. The screen-off baseline was already low. Intermediate candidate active-page measurements varied; the table retains the complete profiling run, rather than the fastest candidate. After profiling, a narrow-screen check found a wrapping Resume label at font scale 1.3; the final artifact adds a full-width control row only in that layout. The changed full-screen surface was rechecked and sampled again on the signed release. No universal active-timer speedup or battery-life percentage is claimed.

On the profiling build, `dumpsys content` after Home shows no app Calendar Provider observer while calendar permission is granted. The one-minute screen-off batterystats window reports zero CPU for the app UID and no app wake-lock entry; the active alarm listing contains one timer completion alarm. System UID wake locks are not attributed to this app.

Inspected the signed release full-screen clock in portrait and landscape, plus 320dp width at font scale 1.3. The large-text adjustment keeps Resume on one line. Inspected the real collapsed and expanded Android notification; both show the remaining-time digits. AOSP notification screenshots are examples, not OEM layout guarantees.

Final signed APK full-screen repeat (30s): **130ms app CPU, 40ms main-thread CPU, 31 app frames**. The earlier complete-run full-screen sample was 130ms/30ms/30 frames; baseline was 240ms/70ms/60 frames. Small CPU differences at this resolution should not be interpreted as precise savings.

Started an actual one-minute focus through the final signed app, returned Home, killed its background process (verified absent), and switched the emulator screen off. The completion alarm restarted the process, saved one completed focus with the original timer ID, incremented the cycle once and posted the app’s finish notification (ID 701). This verifies emulator alarm delivery after process loss, not physical-speaker audibility.

## Artifact identity and limits

Signature verification and 16 KiB ZIP alignment pass. Certificate SHA-256: `2fafa7cd50c089f8b8e7b0a1b8e14db0cfda779ea7ab4bfa024b578f2880e296`, unchanged from the previous preview. APK SHA-256: `598a8bdcf791c680bad4c270538abb2af287d0ab6403364ae85f957925ceda96`. Release assets include the APK, checksum, source/build identity, summarized samples and visual examples. Raw synthetic emulator logs remain under ignored `artifacts/performance/`.

Physical Samsung/Xiaomi/OEM battery use, notification layout, audible delivery and background restrictions remain unmeasured. Screen-on brightness and the optional Keep screen on setting can dominate consumption. Completion still depends on exact-alarm/notification permissions, channel settings and OS behavior; force-stop prevents callbacks until reopening. Calendar syncing and optional quiet-rule jobs have separate lifecycles. No physical-phone installation, production signing, HyperIsland or numeric status-chip integration is claimed.
