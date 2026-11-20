# Release Time Left for Android

## Build and verify

Use JDK 17, Android SDK platform 36 and Build Tools 35.0.0. Build the release from a clean checkout.

```sh
./gradlew :core:test :app:lintRelease :app:bundleRelease :app:assembleRelease
```

| Output | Path |
| --- | --- |
| Unsigned Android App Bundle for Google Play | `app/build/outputs/bundle/release/app-release.aab` |
| Unsigned APK for direct distribution or stores that accept APKs | `app/build/outputs/apk/release/app-release-unsigned.apk` |

Keep `dev.calendarquiet` as the app ID. This candidate is version `0.6.1` (code `10`), targets API 36, and runs on Android 13 and later. Use a higher version code for subsequent releases. [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)

## Sign the store builds

- In Android Studio, use **Build → Generate Signed Bundle / APK**. For Google Play, sign the bundle with the upload key and enroll in Play App Signing. For stores that accept APKs, sign the APK with the production app-signing key.
- Decide how stores should share the app-signing identity before the first upload. To allow updates across stores using the same package, use the same app-signing key across those stores. If selecting that route, supply your own app-signing key during Play App Signing setup and keep a separate upload key.
- Store production and upload keystores, recovery copies and passwords outside the repository. Keep a copy of the project license with any separately distributed release files.
- Earlier Time Left previews were signed with a development key. When the production signing certificate differs, a preview user must uninstall the development build before installing the store build. Uninstalling clears local rules, timers and focus progress.

See [Android app signing](https://developer.android.com/studio/publish/app-signing) for supported signing and verification tools.

## Before submission

- Publish the included [privacy policy](../app/src/main/res/raw/privacy_policy.txt) at a publicly accessible URL you control, with an active way to contact you for privacy questions. The app also shows this policy under **Setup → Privacy policy**. Add the URL to Play Console. [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- Complete Data safety for the exact build you submit. Time Left processes calendar and notification data on the device, contains no analytics or backend integration, and requests no Internet permission. Google Play distinguishes on-device processing from collection that transmits data off the device. [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
- Prepare the store listing, screenshots, contact information and content rating. Use synthetic calendar data for screenshots. Upload the signed bundle to a Play testing track and check installation, upgrade, permission disclosures, alarms, notification snoozing and widgets on actual devices.
- Personal Play developer accounts created after November 13, 2023 need a closed test with at least 12 opted-in testers for 14 consecutive days before applying for production access. [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)
