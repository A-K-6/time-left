#!/bin/sh
# The driver runs in the target process. Use only an owned, disposable emulator.
set -eu
serial=${1:?Usage: scripts/benchmark-timer.sh emulator-SERIAL}
case "$serial" in emulator-*) ;; *) echo 'A disposable emulator serial is required.' >&2; exit 2 ;; esac
adb_bin=${ANDROID_HOME:?Set ANDROID_HOME}/platform-tools/adb
# Build with ./gradlew -PperformanceTests=true :app:assembleDebugAndroidTest.
# Install the chosen signed target APK first; both APKs must share a signing key.
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result=$("$adb_bin" -s "$serial" shell am instrument -w dev.calendarquiet.test/dev.calendarquiet.PerformanceRunner)
printf '%s\n' "$result"
printf '%s\n' "$result" | grep -q '^OK (performance journey)$'
if printf '%s\n' "$result" | grep -q 'FAILED'; then exit 1; fi
