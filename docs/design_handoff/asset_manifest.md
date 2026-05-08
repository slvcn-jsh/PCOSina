# PCOSina Design Asset Manifest

Source root:

`design-assets/PCOSINA UI & SVG FILES/`

Generated for the Session 1 design handoff documentation pass.

## Asset handling policy

| Asset kind | Android handling |
|---|---|
| Full-screen PNG | Visual reference only. Use to compare Compose layout, spacing, colors, icons, and hierarchy. |
| Full-screen SVG | Reference/extraction only. Use to inspect vector shapes, text placement, icon sources, and measurements. |
| Individual simple SVG icon | Candidate for Android VectorDrawable XML under `app/src/main/res/drawable/`. |
| Complex SVG illustration | Convert to optimized PNG/WebP under `app/src/main/res/drawable-nodpi/` if it is needed in-app. |
| Background/full-page SVG | Keep as reference unless a small reusable illustration can be extracted. |
| Full app screen | Rebuild in Jetpack Compose with real app data. Do not render as one static image. |

Android resource names must be lowercase snake case. Exported names with spaces, uppercase letters, punctuation, or hyphens are unsafe until renamed.

## Folder inventory summary

| Folder | PNG count | SVG count | Main use |
|---|---:|---:|---|
| Root `PCOSINA UI & SVG FILES` | 0 | 38 | Icon candidates, background/reference SVGs, numeric exports needing inspection |
| `LOGIN SPLASH SCREEN` | 13 | 18 | Splash, login, terms, profile, onboarding, avatar, notification, support references |
| `HOME SCREEN UI` | 5 | 5 | Home base screen and notification variants |
| `PLAN SCREEN UI` | 5 | 5 | Plan screen, swap UI, meal check-in UI |
| `GROCERY SCREEN UI` | 4 | 5 | Grocery, grocery meal card, filter, pantry list |
| `PROGRESS SCREEN UI` | 5 | 5 | Weekly/progress dashboard variants |
| `SETTINGS SCREEN UI` | 11 | 11 | Settings profile, avatar, reminders, reminder time variants |

## Root SVG assets

| Source file | Asset type | Recommended action | Later target if used | Notes |
|---|---|---|---|---|
| `2.svg` | full-screen/reference SVG | reference only | `docs/design_handoff/svg_reference/` | Large export; inspect only. |
| `3.svg`, `13.svg`, `14.svg`, `15.svg`, `16.svg`, `17.svg`, `18.svg`, `19.svg`, `20.svg`, `24.svg`, `25.svg`, `26.svg`, `27.svg`, `28.svg`, `31.svg`, `33.svg`, `34.svg`, `35.svg`, `36.svg`, `38.svg`, `39.svg` | numeric SVG exports | needs inspection before import | TBD | Names are Android-safe but semantically unclear; do not import until mapped to a real component. |
| `ACCOUNT AND SETTING SCREEN_BACKGROUND.svg` | background/reference SVG | reference/extraction only | TBD | Unsafe resource name; likely background/illustration. |
| `APP DIRECTORY_SUPPORTSCREEN.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_app_directory.xml` | Support screen. |
| `CHECK SIGN ICON.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_check_sign.xml` | Success/check states. |
| `FRESH START ICON_SUPPORT SCREEN.svg` | icon/illustration SVG | vector if simple, WebP if complex | `ic_fresh_start.xml` or `img_fresh_start.webp` | Support/tips area. |
| `LOCKED MEAL DAYS ICON.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_locked_meal_day.xml` | Plan locked-day state. |
| `LOGIN_BACKGROUND.svg` | background/reference SVG | reference/extraction only | TBD | Do not use as full-screen UI. |
| `NOTIFICATION.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_notification.xml` | Notification entry point. |
| `READY TO START YOUR MEAL PLAN ICON_HOME.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_ready_meal_plan.xml` | Home screen. |
| `SEND US FEEDBACK_SUPPORTSCREEN.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_send_feedback.xml` | Support feedback action. |
| `SETTING SCREEN.svg` | reference SVG | reference/extraction only | TBD | Unsafe resource name; ambiguous. |
| `SHARE GROCERY LIST_GROCERYSCREEN.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_share_grocery.xml` | Grocery share action. |
| `SUPPORT SCREEN.svg` | reference SVG | reference/extraction only | TBD | Unsafe resource name; likely screen/reference. |
| `VIEW PANTRY LIST_GROCERYSCREEN.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_view_pantry.xml` | Grocery pantry action. |
| `WARNING ALERT SIGN ICON.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_warning_alert.xml` | Warning/validation states. |
| `WEEKLY MEAL SUMMARY_PROGRESS_SCREEN.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_weekly_meal_summary.xml` | Progress summary. |
| `WITHIN THE BUDGET ICON_GROCERY.svg` | icon SVG | import as VectorDrawable if simple | `app/src/main/res/drawable/ic_within_budget.xml` | Grocery budget card. |

## Login, splash, terms, onboarding

Folder: `LOGIN SPLASH SCREEN`

| Source file | Asset type | Recommended action | Usage |
|---|---|---|---|
| `SPLASH SCREEN.png`, `SPLASH SCREEN.svg` | screen PNG/full-screen SVG | reference only | Splash screen Compose rebuild. |
| `LOGIN-4.png`, `LOGIN-4.svg` | screen PNG/full-screen SVG | reference only; extract heart/hero if needed | Login screen Compose rebuild. |
| `LOGIN-1.svg`, `LOGIN-2.svg`, `LOGIN-3.svg` | logo/text/icon SVG candidates | inspect before import | Login branding pieces. |
| `TERMS OF SERVICE 1.png`, `TERMS OF SERVICE 1.svg` | screen PNG/full-screen SVG | reference only | Terms state 1. |
| `TERMS OF SERVICE 2.png`, `TERMS OF SERVICE 2.svg` | screen PNG/full-screen SVG | reference only | Terms state 2. |
| `PERSONAL PROFILE.png`, `PERSONAL PROFILE.svg` | screen PNG/full-screen SVG | reference only | Profile onboarding step. |
| `MEDICAL PROFILE.png`, `MEDICAL PROFILE.svg` | screen PNG/full-screen SVG | reference only | Medical onboarding step. |
| `PREFERENCES AND BUDGET 1.png`, `PREFERENCES AND BUDGET 1.svg` | screen PNG/full-screen SVG | reference only | Preference/budget step 1. |
| `PREFERENCES AND BUDGET 2.png`, `PREFERENCES AND BUDGET 2.svg` | screen PNG/full-screen SVG | reference only | Preference/budget step 2. |
| `GOAL SETTING SCREEN.png`, `GOAL SETTING SCREEN.svg` | screen PNG/full-screen SVG | reference only | Goal selection. |
| `AVATAR UII.png`, `AVATAR UII.svg` | screen PNG/full-screen SVG | reference only; extract avatar pieces only if needed | Avatar selection UI. |
| `NOTIFICATION UI.png`, `NOTIFICATION UI.svg`, `NOTIFICATIONS.svg` | screen/reference/icon candidate | reference only for screen; inspect SVG icon candidate | Notification settings UI. |
| `SUPPORT SCREEN UI.png`, `SUPPORT SCREEN UI.svg` | screen PNG/full-screen SVG | reference only | Support screen visual baseline. |
| `POP UPS ASSETS.png`, `POP UPS ASSETS.svg` | popup reference | reference only; extract small assets if needed | Dialog/popup styling. |
| `PLAN.svg` | text/logo SVG candidate | inspect before import | Possibly plan label/wordmark; do not import until mapped. |

## Home assets

Folder: `HOME SCREEN UI`

| Source file | Asset type | Recommended action | Usage |
|---|---|---|---|
| `HOME SCREEN.png`, `HOME SCREEN.svg` | screen PNG/full-screen SVG | reference only | Main home screen. |
| `HOME SCREEN 1.png`, `HOME SCREEN 1.svg` | screen PNG/full-screen SVG | reference only | Home alternate state. |
| `HOME SCREEN WM NOTIF.png`, `HOME SCREEN WM NOTIF.svg` | screen PNG/full-screen SVG | reference only | Weekly meal notification state. |
| `HOME SCREEN PSM NOTIF.png`, `HOME SCREEN PSM NOTIF.svg` | screen PNG/full-screen SVG | reference only | Progress/summary notification state. |
| `HOME SCREEN GH NOTIF.png`, `HOME SCREEN GH NOTIF.svg` | screen PNG/full-screen SVG | reference only | Grocery/help notification state. |

## Plan assets

Folder: `PLAN SCREEN UI`

| Source file | Asset type | Recommended action | Usage |
|---|---|---|---|
| `PLAN SCREEN.png`, `PLAN SCREEN.svg` | screen PNG/full-screen SVG | reference only | Main plan screen. |
| `PLAN SCREEN-1.png`, `PLAN SCREEN-1.svg` | screen PNG/full-screen SVG | reference only | Alternate plan state. |
| `PLAN SCREEN SWAP UI.png`, `PLAN SCREEN SWAP UI.svg` | modal/screen reference | reference only | Meal swap UI. |
| `PLAN SCREEN MEAL CHECK IN UI 1.png`, `PLAN SCREEN MEAL CHECK IN UI 1.svg` | modal/screen reference | reference only | Meal check-in state 1. |
| `PLAN SCREEN MEAL CHECK IN UI 2.png`, `PLAN SCREEN MEAL CHECK IN UI 2.svg` | modal/screen reference | reference only | Meal check-in state 2. |

## Grocery and pantry assets

Folder: `GROCERY SCREEN UI`

| Source file | Asset type | Recommended action | Usage |
|---|---|---|---|
| `GROCERY SCREEN.png`, `GROCERY SCREEN.svg` | screen PNG/full-screen SVG | reference only | Main grocery screen. |
| `GROCERY MEAL CARD UI.png`, `GROCERY MEAL CARD UI.svg` | component/screen reference | reference only | Grocery meal card design. |
| `GROCERY SCREEN SELECT FILTER UI.png`, `GROCERY SCREEN SELECT FILTER UI.svg` | popup/screen reference | reference only | Filter dialog. |
| `PANTRY LIST PAGE UI.png`, `PANTRY LIST PAGE UI.svg` | screen PNG/full-screen SVG | reference only | Pantry list page/dialog. |
| `GROCERY.svg` | text/icon SVG candidate | inspect before import | Do not import until mapped. |

## Progress assets

Folder: `PROGRESS SCREEN UI`

| Source file | Asset type | Recommended action | Usage |
|---|---|---|---|
| `PROGRESS SCREEN WH UI.png`, `PROGRESS SCREEN WH UI.svg` | screen PNG/full-screen SVG | reference only | Weekly highlights/progress variant. |
| `PROGRESS SCREEN WH UI-1.png`, `PROGRESS SCREEN WH UI-1.svg` | screen PNG/full-screen SVG | reference only | Alternate weekly highlights variant. |
| `PROGRESS SCREEN UI 1.png`, `PROGRESS SCREEN UI 1.svg` | screen PNG/full-screen SVG | reference only | Progress variant 1. |
| `PROGRESS SCREEN UI 2.png`, `PROGRESS SCREEN UI 2.svg` | screen PNG/full-screen SVG | reference only | Progress variant 2. |
| `PROGRESS SCREEN UI 3.png`, `PROGRESS SCREEN UI 3.svg` | screen PNG/full-screen SVG | reference only | Progress variant 3. |

## Settings assets

Folder: `SETTINGS SCREEN UI`

| Source file | Asset type | Recommended action | Usage |
|---|---|---|---|
| `SETTING UI PROFILE.png`, `SETTING UI PROFILE.svg` | screen PNG/full-screen SVG | reference only | Settings profile base. |
| `SETTING UI AVATAR.png`, `SETTING UI AVATAR.svg` | screen PNG/full-screen SVG | reference only | Avatar settings. |
| `SETTING UI PROFILE 2.png`, `SETTING UI PROFILE 2.svg` | screen PNG/full-screen SVG | reference only | Settings profile state 2. |
| `SETTING UI PROFILE 3.png`, `SETTING UI PROFILE 3.svg` | screen PNG/full-screen SVG | reference only | Settings profile state 3. |
| `SETTING UI PROFILE 4.png`, `SETTING UI PROFILE 4.svg` | screen PNG/full-screen SVG | reference only | Settings profile state 4. |
| `SETTING UI REMINDER.png`, `SETTING UI REMINDER.svg` | screen PNG/full-screen SVG | reference only | Reminder settings. |
| `SETTING UI REMINDER 2.png`, `SETTING UI REMINDER 2.svg` | screen PNG/full-screen SVG | reference only | Reminder state 2. |
| `SETTING UI REMINDER 3.png`, `SETTING UI REMINDER 3.svg` | screen PNG/full-screen SVG | reference only | Reminder state 3. |
| `SETTING UI REMINDER 4.png`, `SETTING UI REMINDER 4.svg` | screen PNG/full-screen SVG | reference only | Reminder state 4. |
| `SETTING UI REMINDER 5.png`, `SETTING UI REMINDER 5.svg` | screen PNG/full-screen SVG | reference only | Reminder state 5. |
| `SETTING UI REMINDER TIME.png`, `SETTING UI REMINDER TIME.svg` | screen PNG/full-screen SVG | reference only | Reminder time picker/state. |

## Recommended Android resource naming

Future imports should use names like:

- `ic_app_directory.xml`
- `ic_check_sign.xml`
- `ic_fresh_start.xml`
- `ic_locked_meal_day.xml`
- `ic_notification.xml`
- `ic_ready_meal_plan.xml`
- `ic_send_feedback.xml`
- `ic_share_grocery.xml`
- `ic_view_pantry.xml`
- `ic_warning_alert.xml`
- `ic_weekly_meal_summary.xml`
- `ic_within_budget.xml`
- `img_login_heart_hands.webp`
- `img_avatar_option_*.webp`

Do not import files with names such as `SETTING UI PROFILE.svg` or `HOME SCREEN.png` directly into Android resources.
