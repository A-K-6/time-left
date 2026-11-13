#!/bin/sh
# Install and test only on a disposable emulator. Never selects a physical phone.
set -eu
serial=${1:?Usage: scripts/emulator-smoke.sh emulator-SERIAL}
case "$serial" in emulator-*) ;; *) echo 'A disposable emulator serial is required.' >&2; exit 2 ;; esac
adb_bin=${ANDROID_HOME:?Set ANDROID_HOME}/platform-tools/adb
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$adb_bin" -s "$serial" shell pm grant dev.calendarquiet android.permission.POST_NOTIFICATIONS
"$adb_bin" -s "$serial" shell appops set dev.calendarquiet SCHEDULE_EXACT_ALARM allow
result=$("$adb_bin" -s "$serial" shell am instrument -w dev.calendarquiet.test/dev.calendarquiet.TimerSmokeRunner)
printf '%s\n' "$result"
"$adb_bin" -s "$serial" shell am force-stop dev.calendarquiet
# am instrument itself can exit zero for a failed check.
printf '%s\n' "$result" | grep -Eq '^OK \([0-9]+ checks\)$'
if printf '%s\n' "$result" | grep -q 'FAILED'; then exit 1; fi
