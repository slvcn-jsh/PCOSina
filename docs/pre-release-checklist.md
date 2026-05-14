# Pre-Release Checklist

Use this checklist before sending a respondent testing build through Firebase App Distribution.

## Before Building APK

- [ ] `git status` is clean except intentional release-note/doc changes.
- [ ] Current branch is `dev`.
- [ ] Latest changes are pushed.
- [ ] Render deployed the latest `dev` commit.
- [ ] Backend `/health` works.
- [ ] Backend `/health/ready` works.
- [ ] Meal generation works on the deployed backend.
- [ ] `PCOSINA_ENV=staging` for respondent testing.
- [ ] `PCOSINA_ENFORCE_APP_CHECK=false` for respondent testing.
- [ ] Firebase Auth works.
- [ ] Admin/operator allowlist works.
- [ ] No `local.properties`, `.env`, service account JSON, keystore, debug token, or local secret file is committed.

## Before Inviting Testers

- [ ] App installs on at least one physical phone.
- [ ] Google Sign-In works.
- [ ] Onboarding works.
- [ ] Generate plan works.
- [ ] Grocery output appears.
- [ ] Progress logging works.
- [ ] Sign out works.
- [ ] Crash-free smoke test completed.
- [ ] Release notes are prepared.
- [ ] Tester group aliases exist in Firebase App Distribution.

## After Release

- [ ] Monitor Crashlytics.
- [ ] Monitor Render logs.
- [ ] Collect issues from testers.
- [ ] Track tester feedback in the team issue tracker.
- [ ] Tag the release commit if useful.
