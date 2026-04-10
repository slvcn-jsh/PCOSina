# Firebase App Distribution (Release APK)

This project is configured to upload the release APK using Gradle task:

- `:app:appDistributionUploadRelease`

## Required environment variables

Set one of:

- `FIREBASE_APPDIST_CREDENTIALS_FILE` (preferred), or
- `GOOGLE_APPLICATION_CREDENTIALS`

Optional:

- `FIREBASE_APP_ID` (only needed if auto-detection is not enough)
- `FIREBASE_APPDIST_GROUPS` (comma-separated tester groups)
- `FIREBASE_APPDIST_TESTERS` (comma-separated tester emails)

## Default auto-distribution group

This repo sets a Gradle fallback:

- `firebaseAppDistributionDefaultGroups=` in `gradle.properties`
- `firebaseAppDistributionDefaultTesters=...` in `gradle.properties`

Notes:
- Group aliases must already exist in Firebase Console before Gradle upload can assign them.
- If no group alias exists yet, keep groups blank and use default testers.

Override at runtime with:

- `FIREBASE_APPDIST_GROUPS`

## Release notes source

Release notes are read from:

- `release-notes.txt`

## Typical flow

1. Build release APK:
   - `.\gradlew :app:assembleRelease`
2. Upload to Firebase App Distribution:
   - `.\gradlew :app:appDistributionUploadRelease`
