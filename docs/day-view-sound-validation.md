# Day view and timer sounds — 2026-10-29

Time Left 0.2.1 (version code 4) adds an hourly day calendar and customizable timer cues. The existing Android package and development signing certificate are retained for upgrades. The development-signed preview is distributed as a private preview.

## Behavior

- Calendar opens in Day view, with previous/next day, Today and Month navigation. The month grid and existing quiet-rule agenda remain available.
- Timed events use their actual start/end positions and calendar colors. Overlapping events receive separate columns. All-day entries appear in a separate strip; free and declined events remain visible.
- Midnight-spanning events are clipped to the displayed day. Layout uses elapsed minutes between local midnights, including 23/25-hour daylight-saving days.
- Short events retain a visible tap target; details show the full title and times. Long-event titles remain visible when their start scrolls above the viewport. A current-time indicator draws over the blocks.
- Tapping a current event can start its countdown and open the Timer tab.
- Timer > Sounds & vibration opens separate focus, break and calendar-timer tone/vibration settings. Each has a test button. Start and finish cues can be independently enabled or disabled. Start cues apply to focus/break sessions.
- Cues play once through Android notification channels with alarm audio attributes. Alarm volume, notification permission, channel settings and Do Not Disturb policy apply. This is not a continuously ringing alarm.
- New sound channels preserve explicit importance/sound choices from the earlier finish-alert channel. Existing phase-channel choices are never reset when reopening the app or upgrading.

## Verification

Gradle 8.13 / JDK 17 / Android SDK 36 / Build Tools 35.0.0:

```text
:core:test :app:assembleDebug :app:lintDebug :app:assembleRelease
```

All 39 core tests passed, including 11 new day-layout tests for overlap groups, column reuse, short events, overnight clipping, invalid/all-day inputs, time zones and daylight-saving transitions. Lint has zero errors; dependency/style warnings remain.

An isolated Android 15 / API 35 emulator was upgraded from the previous preview. Synthetic calendar data was used throughout.

- Inspected Day and Month views with all-day entries, three overlapping events, short events and Persian titles. Selecting October 30 in Month opened that day; Today returned to October 29.
- Opened event details and started the correct event countdown; verified its persisted identity and return to the Timer tab.
- Inspected light/dark themes and 320dp width at font scale 1.3.
- Set the previous finish-alert channel to Silent before creating the new channels. Its importance choice was retained for the new channels.
- Chose Argon for focus and Barium for break using Android's picker. The app reflected both choices; native notification records confirmed distinct sound URIs and alarm audio attributes. Both test notifications were delivered through the appropriate channels.
- Disabled start cues and verified no start notification on a focus session. With the screen off and the background process killed, a one-minute focus completed exactly once and posted its finish alert through the focus sound channel.

- Enabled start cues and disabled finish cues for a one-minute break. Its start alert used the separate break sound; completion posted no finish alert and did not increment the focus count. Restored both cue switches afterward.

Local screenshots and native inspection output are retained in the implementation worktree's ignored `artifacts/day-sound-validation/` directory. Audio routing and notification delivery were checked in the emulator; physical-speaker audibility and manufacturer-specific background behavior need a phone check. HyperIsland/numeric status chips remain outside this update.

The final optimized APK was installed over the debug preview. Separate tone choices survived, the focus test notification used the configured channel, and the day view/event-details dialog rendered correctly. APK signing and 16 KiB ZIP alignment passed. APK SHA-256: `0eaeaeab8967295f754fd3205f932388f10965e4ca78e51f900eb9eea375ec11`.
