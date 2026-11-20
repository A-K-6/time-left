# Release Time Left for Android

## Build and verify

Use JDK 17, Android SDK platform 36 and Build Tools 35.0.0. The Gradle Wrapper pins Gradle 8.14.8 and checks the distribution checksum. Build the release from a clean checkout.

```sh
./gradlew :core:test :app:lintRelease :app:bundleRelease :app:assembleRelease
```

| Output | Path |
| --- | --- |
| Unsigned Android App Bundle for Google Play | `app/build/outputs/bundle/release/app-release.aab` |
| Unsigned APK for direct distribution or stores that accept APKs | `app/build/outputs/apk/release/app-release-unsigned.apk` |

This candidate uses `dev.calendarquiet` and version `0.6.4` (code `13`). It targets API 36 and runs on Android 13 and later. Check whether the previously distributed package ID is eligible for your store account before committing to that ID and signing identity. Use a higher version code for subsequent releases. [Android package verification](https://developer.android.com/developer-verification/guides/pdf-guides/pdc-guide.pdf) · [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)

## Sign the store builds

- In Android Studio, use **Build → Generate Signed Bundle / APK**. For Google Play, sign the bundle with the upload key and enroll in Play App Signing. For stores that accept APKs, sign the APK with the production app-signing key.
- Decide how stores should share the app-signing identity before the first upload. To allow updates across stores using the same package, use the same app-signing key across those stores. If selecting that route, supply your own app-signing key during Play App Signing setup and keep a separate upload key.
- Store production and upload keystores, recovery copies and passwords outside the repository. The app packages the project and dependency license notices.
- Earlier Time Left previews were signed with a development key. When the production signing certificate differs, a preview user must uninstall the development build before installing the store build. Uninstalling clears local rules, timers and focus progress.

See [Android app signing](https://developer.android.com/studio/publish/app-signing) for supported signing and verification tools.

## Before submission

- Choose a support contact, use it on the store listing, and publish the included [privacy policy](../app/src/main/res/raw/privacy_policy.txt) at a public URL with the contact listed directly on the page. Add the URL to Play Console. The policy is also available under **Setup → Privacy policy**. [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- Complete Data safety for the exact build you submit. Time Left processes calendar and notification data on the device, contains no analytics or backend integration, and requests no Internet permission. Google Play distinguishes on-device processing from collection that transmits data off the device. [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
- Prepare the store listing, screenshots, contact information and content rating. Use synthetic calendar data for screenshots. Upload the signed bundle to a Play testing track and check installation, upgrade, permission disclosures, alarms, notification snoozing and widgets on actual devices.
- Verify the legal registration location is supported for the store account and review which account details are published. [Supported Google Play locations](https://support.google.com/googleplay/android-developer/answer/9306917) · [Developer account details](https://support.google.com/googleplay/android-developer/answer/13628312)
- Personal Play developer accounts created after November 13, 2023 need a closed test with at least 12 opted-in testers for 14 consecutive days before applying for production access. [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)
