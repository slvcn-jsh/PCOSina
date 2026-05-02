# PCOSINA Notification + Feedback Verification Ledger

Last updated: 2026-02-12

## Environment Baseline
- JDK: `C:\Program Files\Android\Android Studio\jbr`
- Gradle user home: `.gradle-user-home`
- Android user home: `.android-user`

### Compile Proof Command
```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME='C:\Users\salva\AndroidStudioProjects\PCOSINA2\.gradle-user-home'
$env:ANDROID_USER_HOME='C:\Users\salva\AndroidStudioProjects\PCOSINA2\.android-user'
./gradlew :app:compileDebugKotlin
```

Expected terminal tail:
- `BUILD SUCCESSFUL`

## Vertical Slice Matrix
| Slice | Scope | Proof Type | Proof Path |
|---|---|---|---|
| Foundation | Notification channels + permission gate + scheduler + persisted prefs | Grep + Manual | `NotificationHelper.kt`, `NotificationScheduler.kt`, `SettingsScreen.kt`, `UserPreferencesRepository.kt` |
| Meal reminders | Breakfast/Lunch/Dinner schedule with time prefs + caps + quiet-hours | Manual + Debug panel | Settings → Notifications + Debug details |
| Weekly reset | Weekly reminder scheduling + anti-spam | Grep + Manual | `NotificationScheduler.WeeklyResetWorker` |
| Plan ready | Trigger on generation success only | Grep + Manual | `MealPlanRefinedScreen.kt`, `NotificationScheduler.notifyPlanReady` |
| Grocery sync status | User-initiated success/failure notifications only | Grep + Manual | `MealPlanRefinedScreen.kt`, `NotificationScheduler.notifyGrocerySyncResult` |
| In-app feedback | Reusable feedback kit + refined inline feedback cards | Grep + Manual | `FeedbackKit.kt`, `MealPlanRefinedScreen.kt`, `ProgressRefinedScreen.kt` |

## Foundation Verification Checklist
1. Channels created at app startup.
   - Check: `PcosinaApp.kt` calls `NotificationHelper.createChannels(this)`.
2. Runtime permission handled for Android 13+.
   - Check: Settings master toggle requests `POST_NOTIFICATIONS`.
3. App-level notification enablement enforced.
   - Check: `NotificationManagerCompat.areNotificationsEnabled()` in `NotificationHelper.canPostNotifications`.
4. User-scoped scheduling and cancellation.
   - Check: unique work names include `_$userId`.
5. Quiet hours enforced at dispatch.
   - Check: `dispatchAndTrackNotification` blocks non-debug events in quiet hours.
6. Caps enforced with persisted timestamps.
   - Check: `getNotificationLastFired` + interval checks in workers.
7. Logged-out suppression.
   - Check: `isUserSessionValid(userId)` guard in workers and dispatch helper.
8. Debug surface present.
   - Check in Settings: permission state, next scheduled times, worker summaries, last delivered timestamps, test trigger.
9. Weekly reset schedule is user-configurable.
   - Check in Settings: `Weekly reset day` + `Weekly reset time` and verify summary line updates.
10. Weekly reset immediate trigger path is testable.
   - Check in DEBUG panel: `Send weekly reset now`.

## Runtime Log Expectations
Tag: `PCOSINA-Notification`

Expected lines:
- `dispatch blocked: user session invalid`
- `dispatch blocked: master disabled`
- `dispatch blocked: type disabled`
- `dispatch blocked: quiet hours`
- `notification delivered type=<eventType> user=<uid>`

## Manual Smoke Script
1. Enable notification master toggle in Settings.
2. Accept notification permission prompt (Android 13+).
3. Enable meal reminders and set a near-future time.
4. Confirm debug panel shows scheduled workers and next reminder summary.
5. Tap `Send test notification`; confirm local notification appears.
6. Disable master toggle; confirm workers clear and no notification fires.
