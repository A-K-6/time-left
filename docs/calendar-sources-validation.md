# Calendar sources and refresh validation

The reported mismatch was reproducible with synthetic data on an AOSP Android 15/API 35 emulator. This does not establish the exact Calendar Provider flags or account state on the user's Xiaomi phone.

## Failure and repair

In v0.2.1, `DeviceCalendars.calendars()` queried only `visible=1 AND sync_events=1`. A hidden but synced Google calendar was therefore missing from both the picker and event queries. Separately, the Calendar tab never applied the Timer page's persisted source selection. With two visible sources and only Google selected in Timer, Calendar still showed the local event.

Calendar inventory now includes account type, visibility and sync metadata for all sources. Default selection preserves the prior visible-and-sync-enabled behavior. Explicit selections can read hidden calendars and any stored events in sync-disabled calendars. Empty or missing selections do not fall back to another account. Event queries constrain Instances by selected calendar IDs; recurrence expansion and cancellation filtering remain intact. Timer and both Calendar views observe the same persisted selection. Quiet rules retain the original independent selection/default behavior.

The source picker identifies account/provider, offers **Use Google calendars**, and labels hidden or sync-disabled sources. Source inventory and events react to provider changes and Activity resume. **Refresh** reloads local data. **Account sync settings** opens Android's native account-sync screen; the app does not claim that a local reload proves Google cloud sync. No Internet, account-listing or calendar-write permissions were added. A Google account not exposed through Calendar Provider still needs device sync or a future separate Google integration.

## Automated checks

- `:core:test`: 45 passing tests, including six source-selection cases covering default visibility/sync, hidden Google versus a same-name local source, visibility changes, empty selection, removed IDs and explicitly chosen cached sources.
- `:app:assembleDebug :app:lintDebug :app:assembleRelease`: passed; lint reports zero errors.
- Optimized APK uses versionCode 5 / versionName 0.2.2. Its signature and 16 KiB zip alignment were checked with Android build tools. The development certificate is unchanged from v0.2.1.

## Runtime evidence

Task-owned emulator accounts and event titles are synthetic. Original reproduction, UI dumps, screenshots and verification helpers are retained in ignored `artifacts/calendar-sources-validation/` in the implementation worktree. Cloud requests and the user's calendars were not used as test data.

Observed on the emulator:

- Upgrade from v0.2.1 retained the explicit Google selection and displayed the pre-existing timer history and durations. A hidden Google source appeared in Day and Timer while the visible local source was excluded. Month used the same filter.
- Switching to all visible sources showed the local event; **Use Google calendars** switched both tabs back to Google. Provider/account identity appeared in the picker, event details and Timer suggestion.
- Editing a provider event updated the visible Timer page without Refresh; Calendar showed the revised title on navigation.
- Selecting no calendars excluded all events and produced the explicit Timer empty-selection prompt.
- The account-sync button opened Android Settings, and returning reloaded the source list. The emulator has no real Google account/sync adapter; Google cloud sync was not exercised.
- A force-stop/relaunch retained the source selection. Explicitly choosing a sync-disabled calendar could still read its cached events; the picker labeled the source as potentially outdated.
- The final optimized signed APK retained selection and displayed the hidden Google source and its account. Event details correctly noted that quiet rules skip hidden/sync-disabled sources.
- At 320 dp width with font scale 1.3, the picker remained scrollable, source text wrapped and Done stayed reachable.
- Deleting the selected Google source removed its events from the open Calendar view, showed an unavailable-source message, and never substituted the local calendar.

The optimized APK SHA-256 is `80958f1c3235bbdc7507b0c221e9dff1977d5b5c0dbbb4123bd81373f4221ad9`.

Physical HyperOS verification remains open: confirm the Google account/calendars are listed, choose them, and compare a recent event with Google Calendar on the same phone. The reproduced visibility/selection defects are fixed; the user's actual account/provider state has not been inspected. Direct Google OAuth/cloud reads remain outside this release.
