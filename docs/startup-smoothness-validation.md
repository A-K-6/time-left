# Time Left v0.5.0 — smoother navigation and calendar refresh

Verified 2026-11-14 (Asia/Tehran). Package `dev.calendarquiet`, version `0.5.0`, code `8`, Android 13+ (API 33), target/compile API 36. Optimized R8 APK signed with the existing development preview key. Delivery is a GitHub release APK; no physical phone installation is authorized or performed.

## Behavior and implementation

Timer and Calendar share a small application-owned UI snapshot cache. Concurrent reads serialize and reuse the same source list instead of each event query rereading accounts. Event windows have stable day/month boundaries, explicit calendar-selection keys and a four-window LRU bound. Timer suggestions still filter to the actual next seven days. An explicit empty selection cannot reuse the default/all-calendar snapshot.

Foreground provider edits and explicit Refresh invalidate snapshots. Returning from a stopped activity and clock/time-zone changes invalidate them too. Stopping clears extra cached month windows and retains no background UI observer. Every read carries an invalidation generation; a query that finishes after an edit cannot repopulate the cache with the old generation. Automation retains direct provider reads, independent of these UI snapshots.

A refresh keeps the current list visible. Calendar-derived Start actions wait for a successful fresh read; changing the selected calendars/window resets the corresponding presentation. Cancelling an older load cannot clear the replacement load's loading state. Existing permission/status refresh behavior is retained.

Paused quiet automation settles once per process or explicit reconciliation, then ordinary UI resumes skip repeated preference writes, alarm/job cancellations and sound cleanup. Enabled automation, rule edits and system/permission/clock broadcasts still reconcile. Timer persistence, monotonic duration deadlines, notification/widget controls, revision-bound actions and the one completion alarm are unchanged.

`ReportDrawnWhen` now reports readiness after initial calendar loading finishes or presents an error/permission state. Trace sections `TimeLeft.CalendarSources` and `TimeLeft.CalendarEvents` record read durations and aggregate counters only, without account/event content. There is no new background service, wake-lock permission, network permission or analytics.

## Validation

**66 core tests pass**, including invalidation during a read, empty/different selection isolation, bounded retention and date/window changes. **14 Android smoke checks pass**, retaining the previous 12 timer regressions and adding concurrent UI read/source reuse/invalidation and paused UI reconciliation checks. Debug and optimized release builds pass; Android lint reports **zero errors and 44 warnings**, without new warning suppression. Signature verification and 16 KiB ZIP alignment pass.

Runtime checks use a disposable AOSP Android 15 / API 35 emulator. The test script refuses a physical-device serial:

```sh
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease
ANDROID_HOME=/path/to/sdk scripts/emulator-smoke.sh emulator-5554
```

The final signed APK passed foreground provider rename, background-edit/resume, cancellation removal and calendar-permission revocation checks. `dumpsys content` was captured after Home with no app UI calendar observer. The fixture is synthetic: uncancelling a test row required touching its timestamps to restore the provider’s Instances row. Final primary measurements were repeated after checking that row; earlier pre-restoration runs are retained separately.

## Measurement method

Baseline is the published v0.4.0 optimized APK, SHA-256 `598a8bdcf791c680bad4c270538abb2af287d0ab6403364ae85f957925ceda96`. Final v0.5.0 APK SHA-256 is `ef0e0e4ec8efcc3ce8607a697f99693de0b8edc8311dd1600068404c2bf58c98`. Intermediate candidate observations are retained separately, not substituted for the final artifact.

Same owned API 35 emulator, 720×1600 pixels, density 320, font scale 1.0, SwiftShader graphics, paused optional automation, calendar access granted and a synthetic 240-event fixture. No competing UI automation or Gradle builds run during timing samples. Eight process-cold launches use `am start -W` TotalTime; this resets the app process, not device caches or all ART compilation state. Warm WaitTime is dispatch time, not fully drawn UI latency, and is retained only as diagnostic data.

The framework-only Java `PerformanceRunner` runs against both signed/minified versions with the identical test APK. Three runs per version each perform six Timer/Calendar round trips and timeline scrolls. A Window FrameMetrics listener records completed activity-window frames, excludes first-draw frames, and compares TOTAL_DURATION with DEADLINE. This measures activity-window work; dialog windows are excluded. ART runtime allocation/GC counters are sampled before/after the journey. These whole-process counters include the same instrumentation driver in both versions. PSS and heap figures are point samples, not leak proofs. Traced runs are diagnostic extras and are excluded from the three-run medians.

| Metric | v0.4.0 median | v0.5.0 median |
| --- | ---: | ---: |
| Interaction app CPU | 2668.00 ms | 2318.00 ms |
| Managed allocations | 28.60 MB | 26.06 MB |
| Missed frame deadlines | 13.00 frames/run | 4.00 frames/run |
| Frame duration p95 | 20.42 ms | 18.35 ms |
| Cold first display | 528.50 ms | 516.00 ms |
| Foreground process PSS | 33.08 MiB | 32.87 MiB |

PSS values span 33,764–33,911 KiB in baseline runs and 33,316–59,163 KiB in final runs. The 59,163 KiB observation is retained: that run ended with no GC and a 31.6 MB Java heap, versus one GC and about 6.4 MB Java heaps in the other final runs. This is consistent with collection timing; these samples establish neither a memory leak nor a general RAM reduction. There were no blocking GCs in these runs.

Cold startup is not established as faster. The eight-launch medians were 528.5ms before and 516ms after; an earlier final-artifact run gave 553ms. The overlapping ranges and uncontrolled ART/device caches preclude claiming a stable cold-start speedup. Final fully drawn times were [578, 582, 589, 582, 582, 637, 588, 581]ms (median 582.0ms); v0.4.0 did not report this readiness marker.

## Traces, background work and limits

A 20-second diagnostic Perfetto run of the cache implementation recorded **one calendar-source read and two event-window reads** across startup plus six tab round trips. It included app, System UI, launcher and Calendar Provider CPU scheduling, extending coverage beyond app-only counters. The diagnostic trace used candidate SHA-256 `b82450fbbf954bf0f59ff1a5bb608d558ba7979c8313909e941d531b2cd4e1e7`, before the final cancellation/loading and stop-cache cleanup refinements; it is not presented as a final-artifact timing run. Primary metrics above use the final artifact.

In the final signed APK, the 30-second notification-visible sample counted 20ms of app CPU, 280ms in System UI, 60ms in the launcher and 0ms in the Calendar Provider. Over the following **180 seconds with the screen off**, the same app process counted **0ms**, System UI 430ms, launcher 0ms and Calendar Provider 0ms. Counters have 10ms resolution; all tracked PIDs stayed the same. One timer completion alarm remained scheduled. Separate batterystats accounting reported 4ms of app-UID CPU over its wider reset/capture window, consistent with the coarser 10ms sampling result. These are process CPU observations, not device energy.

The final signed APK started an actual one-minute focus through the UI, then its background process was killed and the screen switched off. The alarm restarted the process, completed the original timer once, recorded one focus and posted the app’s finish notification (ID 701). This establishes emulator delivery after process loss, not physical speaker audibility.

These observations are short emulator comparisons without statistical confidence intervals. Frame deadlines, cold startup, memory and actual battery energy still require physical-device validation. CPU time and allocation are not energy or battery-percentage estimates. System UI/launcher/provider activity also includes unrelated system work, so it cannot all be attributed to the timer. Existing OEM alarm, notification, sound-channel, Do Not Disturb and force-stop limits continue to apply. No real-phone battery-life improvement is claimed.

## Reproduction and artifacts

```sh
# Install the chosen optimized APK first, using the same preview key.
ANDROID_HOME=/path/to/sdk python3 scripts/benchmark-startup.py --serial emulator-5554 --label sample --fixture-events 240
./gradlew -PperformanceTests=true :app:assembleDebugAndroidTest
ANDROID_HOME=/path/to/sdk scripts/benchmark-timer.sh emulator-5554
```

Start with an unlocked disposable emulator and an idle timer. The startup script sets font scale 1.0, resets display size, clears emulator logcat during launches and stops the app process. Use a separate synthetic calendar fixture; it does not create one or modify personal calendar entries. Only run one UI driver at a time. Rebuild the test APK without `-PperformanceTests=true` before running timer smoke checks. The performance test APK is not distributed as the app.

Sources for the measurement APIs: [FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics), [fully drawn reporting](https://developer.android.com/topic/performance/vitals/launch-time), [Perfetto analysis](https://perfetto.dev/docs/getting-started/command-line-analysis). Android recommends [physical-device benchmarking](https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile) for representative results.

Release assets include the APK, checksum, source/build identity, raw summarized comparison data and an HTML comparison. Synthetic logs and Perfetto recordings remain in ignored `artifacts/startup-performance/`. Source/diff review was direct. CBM discovery timed out; the optional Pi review could not refresh its saved login (401), and is not counted as independent review evidence.
