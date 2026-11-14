# Offline, Sync, and Restore Matrix

Updated: 2026-05-27

PCOSina is local-first. The local database and local artifact stores are the source of truth for daily use. Cloud sync is a best-effort backup and continuity layer for the same Firebase UID; it must not be required for reviewing saved plans, groceries, pantry, progress logs, feedback queue, or reminder settings on the same install.

## User-Visible Behavior

| Scenario | Expected behavior | Product promise |
|---|---|---|
| App close/reopen | Saved local data returns from this phone. | Supported offline. |
| Phone offline | Saved profile, active plan, grocery list, pantry, local progress logs, feedback queue, and reminder settings remain reviewable. | Supported offline for local review. |
| Generate new plan while offline | New generation is blocked until internet is available. Saved plans and groceries remain visible. | Internet required only for new planner calls. |
| Sign out and sign back in on the same install | Per-user local data remains on this phone and loads again for the same account. | Supported unless the user clears local app data. |
| Clear saved week data | Saved plans, grocery snapshots, and local progress logs are removed for the account. Synced week backup is cleared when sync succeeds. | Destructive action after confirmation. |
| Clear app data | Local data is wiped. Only previously synced cloud data can return after login. | Best-effort cloud restore only. |
| Uninstall/reinstall | Local data is wiped. Only previously synced cloud data can return after login. | Best-effort cloud restore only. |
| New device login | Only cloud-synced data for the same Firebase UID can restore. | Best-effort cloud restore only. |

## Data Survival Matrix

| Data type | Same install local restore | Same-UID cloud restore | Notes |
|---|---|---|---|
| Profile | Yes | Yes, when profile upload succeeded | Profile merge is UID-scoped. |
| Current saved plan and active plan id | Yes | Yes, when plan artifact upload succeeded | Plan history list is local-only. |
| Pantry entries | Yes | Yes, when pantry artifact upload succeeded | Structured pantry entries are synced as an artifact. |
| Grocery state, sources, and snapshots | Yes | Yes, when grocery artifact upload succeeded | Grocery restore follows the last synced artifact. |
| Feedback queue, plan feedback tags, and last reviewed week | Yes | Yes, when feedback artifact upload succeeded | Queue-first behavior remains local-first. |
| Progress mode and advanced-card preference | Yes | Yes, when progress UI artifact upload succeeded | This is UI preference state, not clinical progress proof. |
| Daily meal logs, check-ins, and weekly journals | Yes | No | Local-only until dedicated remote storage exists. |
| Notification preferences and reminder cooldown metadata | Yes | Yes, when notification preferences artifact upload succeeded | Actual Android schedules are recreated locally. |
| Notification delivery history | Yes | No | Delivery logs are device/install history. |
| Recipe detail snapshot cache | Yes | No product promise | Used only as an offline fallback for details already cached. |

## Verification Checklist

1. Close and reopen the app with airplane mode enabled. Confirm saved profile, plan, grocery, pantry, progress logs, feedback queue, and notification settings still load.
2. Sign out and sign back in on the same install. Confirm local data for the same account returns.
3. Generate a plan while offline. Confirm the app says: "Internet connection is needed to generate a new plan. You can still view saved plans and groceries offline."
4. With a network-enabled signed-in test account, change profile, active plan, pantry, grocery, feedback tags/queue, progress UI preference, and notification preferences. Wait for sync, then validate Firestore `profiles/{uid}` contains the corresponding scoped artifact fields.
5. Clear app data or reinstall, sign in with the same UID, and confirm only cloud-synced profile/artifacts restore. Confirm local-only daily logs, journals, and notification delivery history do not return.
6. Sign in on a second device with the same UID and repeat the restore check.

## Implementation Evidence

- Android local/profile artifact store: `app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt`
- Reflection/local history store: `app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt`
- User-facing restore copy: `app/src/main/java/com/pcosina/app/ui/screens/SettingsScreen.kt`
- Planner offline failure copy: `app/src/main/java/com/pcosina/app/data/repository/MealPlanRepository.kt`
- Policy tests: `app/src/test/java/com/pcosina/app/ArtifactSyncPolicyTest.kt`, `app/src/test/java/com/pcosina/app/OfflineRestorePolicyTest.kt`
