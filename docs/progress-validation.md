# Time Left v0.6.0 — focus progress

Verified 2026-11-14. Android 13+; package `dev.calendarquiet`, version code 9. Development-signed, optimized R8 preview. Publication is an APK on the release page; no physical-phone installation.

## Delivered behavior

Progress is a primary tab with a horizontally scrolling year heatmap, date inspection, a daily target (1–12, default 4), XP, levels, current/best streaks and six milestones. It follows the existing paper/forest theme in light and dark modes. Rules retains quiet automation; its schedule is now **Rules → View quiet schedule**, with explicit and system Back navigation.

Only a completed Focus timer earns progress: 10 XP per session, independent of its configured duration. Every 100 XP advances a level. Breaks, long breaks, calendar/free-time countdowns and unfinished sessions earn nothing. Goal changes affect target progress and heatmap shading, without rewriting earned XP or streaks. Any positive focus count keeps a day in a streak; yesterday's streak remains available until today ends. Milestones derive from known total completions and the best known streak, so they do not require separate award effects.

Daily buckets are saved atomically with timer completion in the existing private JSON store. Completion removes the active timer and records the daily count together; refresh, process restart and repeated alarms cannot award that timer again. The older recent-timestamp list still prunes at 30 days / 1000 entries, but cumulative daily counts survive it. The model deliberately measures completed sessions, not historical focus minutes: the old schema did not retain those durations.

Migration imports the available completion timestamps once, excludes future timestamps, and preserves existing configuration, active/completed timer state, cycle count and action revision. Earlier missing history is explicitly incomplete, with slashed empty cells before full tracking began. Previously saved historical positive counts remain visible. Local dates are anchored when completion is recorded; legacy timestamps use the migration-time zone. Earned totals/XP and best streak remain stable when travel moves the local date backward. There is no cloud account, leaderboard or invented history.

Progress-only migration and goal edits preserve the timer revision and presentation snapshot. They do not invalidate already posted Pause/Resume/next-session actions or repost the timer notification. The coordinator refresh still processes any genuinely due timer before editing a target.

## Verification

- **74 core tests pass**, including eight progress tests. The history-pruning regression was first observed failing on the prior implementation (expected historical count 1, actual 0), then passes with cumulative buckets. Coverage includes completion once, paused/unfinished/break exclusions, yesterday grace, best streak, goal/level invariance, migration, time-zone anchoring/travel and saturated bucket safety.
- **16 Android smoke checks pass** on the owned disposable AOSP API 35 emulator. Added checks exercise running-notification post-time preservation, an actual pre-edit Pause PendingIntent, one-time migration, persisted XP and goal changes that leave next-session action revisions valid. Existing alarm, notification privacy/countdown, stale-action, long-break, calendar snapshot and quiet-rule safeguards pass.
- Debug and Android-test compilation, lint and optimized release assembly pass. Lint: **0 errors, 44 warnings**, matching the prior count, without suppressions.
- An emulator upgrade from the published v0.5.0 APK preserved saved timer lengths, cycle/history and revision, then imported its one known focus exactly once.
- Actual UI checks cover day-cell selection, Material date selection, goal editing/persistence, light/dark themes, Rules/Schedule navigation, and a 320dp-wide window at font scale 1.3. Date inspection also has a normal button and accessibility action; narrow/short windows and very large fonts use the date-input presentation to keep controls accessible.

The optional Pi co-thinking attempt returned no visible assessment and is not counted as an independent review. Source, invariants and the final diff were reviewed directly.

## Idle work and completion

The heatmap caches its date/cell/month geometry and draws only the visible year. It has no per-second ticker. Its only clock delay is until the next local midnight and suspends when the UI stops. Rewards reuse the existing completion callback: no new job, alarm, service, wake lock, permission, network call or analytics is added. Existing timer/notification/widget clocks retain their prior behavior.

A 30-second signed-release Progress sample, with a real focus running, is recorded in the release's `progress-idle-result.json`. The running-focus window counted 120ms process CPU and 0ms main-thread CPU; its post-reset gfxinfo reported 37 frames, with no before/after frame delta, so that number is not interpreted as newly drawn frames. A separate settled, no-active-timer 30-second window (`progress-settled-idle.json`) counted **10ms process CPU, 0ms main-thread CPU and 0 additional frames** (53 before and after). Counters sample process and main-thread `/proc` ticks at 10ms resolution and app `dumpsys gfxinfo` frames after settling. This short emulator observation excludes notification/launcher rendering and is not a physical battery or energy measurement.

The signed release also runs an actual one-minute focus, then returns Home, loses its background process and turns the screen off before the deadline. The result is recorded in `reward-result.json`: the alarm restarted the process, completed the original timer once, posted finish notification 701 and awarded exactly 10 XP. Reopening Progress retained the same daily bucket without another award. The empty one-focus fixture is emulator-only; release illustration screenshots use separately seeded synthetic history, clearly labeled as demo data. Neither fixture is bundled in the APK.

## Artifact and limits

APK SHA-256: `9d52446e51e1a10d9b3bc11ce85d033988e1eb96a0efe71c0b406df50b3ec11d`.

Signature verification and 16 KiB ZIP alignment pass. The development certificate SHA-256 is unchanged: `2fafa7cd50c089f8b8e7b0a1b8e14db0cfda779ea7ab4bfa024b578f2880e296`. The release includes checksum and build identity, exact source/tag and runtime evidence. Reproduce core/build checks using the README; run Android smoke checks only on a disposable emulator via `scripts/emulator-smoke.sh emulator-SERIAL` after building both test APKs.

Physical OEM battery use, launcher/notification layouts, audible delivery and background restrictions remain unmeasured. This feature does not establish an overall startup, scrolling or battery improvement over v0.5.0. Device force-stop still prevents completion callbacks until reopening. Rewards currently remain device-local, with no export or account sync; clearing app data removes them. Old history already pruned by earlier versions cannot be reconstructed.
