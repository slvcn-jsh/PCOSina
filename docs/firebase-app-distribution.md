# Firebase App Distribution

This project is configured for both respondent testing and release APK uploads.

Preferred respondent testing task:

- `:app:appDistributionUploadStaging`

Production-style release task:

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
- `firebaseAppDistributionRespondentGroups=pcosina-respondents,pcosina-team` in `gradle.properties`
- `firebaseAppDistributionRespondentTesters=...` in `gradle.properties`

Notes:
- Group aliases must already exist in Firebase Console before Gradle upload can assign them.
- If no group alias exists yet, keep groups blank and use default testers.

Override at runtime with:

- `FIREBASE_APPDIST_GROUPS`

## Release notes source

Staging release notes are read from:

- `release-notes/respondent-test-notes.txt`

Release notes are read from:

- `release-notes.txt`

## Respondent testing flow

1. Build staging APK:
   - `.\gradlew.bat :app:assembleStaging`
2. Upload to Firebase App Distribution:
   - `.\gradlew.bat :app:appDistributionUploadStaging`

## Release flow

1. Build release APK:
   - `.\gradlew.bat :app:assembleRelease`
2. Upload to Firebase App Distribution:
   - `.\gradlew.bat :app:appDistributionUploadRelease`
