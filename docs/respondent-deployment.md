# Respondent Testing Deployment

Use this flow when the team needs a single installable PCOSINA build for respondents or teammates. The goal is to avoid Android Studio, ADB, emulators, wireless debugging, and manual Firebase App Check debug token setup for testers.

## Deployment Choice

Respondent testing uses:
- Firebase App Distribution for tester installs.
- Android `staging` APK built from `dev`.
- Render backend at `https://pcosina-backend.onrender.com/`.
- Firebase Auth and Google Sign-In enabled.
- Firestore sync enabled.
- Crashlytics enabled when the Firebase project is configured.
- Backend App Check enforcement disabled only for respondent testing.

The `staging` Android build keeps Firebase App Check support installed but does not send `X-Firebase-AppCheck` to the backend. The backend must be configured with `PCOSINA_ENFORCE_APP_CHECK=false` in the testing environment.

## Before Building

1. Confirm the branch is `dev`.
2. Confirm latest changes are pushed.
3. Confirm Render has deployed the latest `dev` commit.
4. Confirm `https://pcosina-backend.onrender.com/health` and `/health/ready` are healthy.
5. Confirm Render testing env vars match [render-testing-env.md](render-testing-env.md).
6. Confirm no local secret files are staged.

## Required Local Inputs

Release signing must come from environment variables or Gradle properties. Do not commit keystores or passwords.

```powershell
$env:PCOSINA_RELEASE_STORE_FILE="C:\secure\pcosina-release.jks"
$env:PCOSINA_RELEASE_STORE_PASSWORD="<set locally>"
$env:PCOSINA_RELEASE_KEY_ALIAS="<set locally>"
$env:PCOSINA_RELEASE_KEY_PASSWORD="<set locally>"
```

For Firebase App Distribution, use Firebase CLI login or a service account file stored outside the repo.

```powershell
firebase login
```

or:

```powershell
$env:FIREBASE_APPDIST_CREDENTIALS_FILE="C:\secure\firebase-app-distribution-sa.json"
```

## Build Tester APK

```powershell
.\gradlew.bat :app:assembleStaging
```

Output:

```text
app/build/outputs/apk/staging/app-staging.apk
```

The staging APK uses version name suffix `-staging`, for example `1.10.3-staging`.

## Upload to Firebase App Distribution

Create these Firebase App Distribution group aliases first if they do not already exist:

```text
pcosina-respondents
pcosina-team
```

Then upload:

```powershell
.\gradlew.bat :app:appDistributionUploadStaging
```

Release notes are read from:

```text
release-notes/respondent-test-notes.txt
```

To override groups or testers without editing files:

```powershell
$env:FIREBASE_APPDIST_GROUPS="pcosina-respondents,pcosina-team"
$env:FIREBASE_APPDIST_TESTERS="tester1@example.com,tester2@example.com"
```

## Team Operating Rules

- Teammates should not use ADB for respondent testing.
- Everyone should install the same Firebase App Distribution staging build.
- Code changes should still go through GitHub branches and PRs.
- App Check relaxation is for thesis/respondent testing only.
- For production-like testing, use Google Play Internal Testing, Play Integrity App Check, and `PCOSINA_ENFORCE_APP_CHECK=true`.

## Monitoring

After upload, monitor:
- Firebase App Distribution tester install status.
- Crashlytics crashes and non-fatal issues.
- Render logs for backend errors.
- Respondent feedback from the research team channel.
