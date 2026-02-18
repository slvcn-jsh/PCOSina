Place optional PNG baselines here for compact-width visual regression tests.

Expected filenames:
- `compact_profile_step3.png`
- `compact_chip_row.png`
- `compact_dashboard_today_outcome.png`
- `compact_progress_top_section.png`
- `compact_mealplan_top_section.png`
- `compact_login_first_win_card.png`
- `compact_profile_first_win_card.png`
- `compact_goal_handoff_card.png`
- `manifest.sha256` (hash lock for committed baseline images)

When present, `CompactWidthVisualRegressionTest` compares current captures to these baselines.
When absent, tests still capture screenshots and run smoke assertions.

Optional instrumentation args for emulator variance tuning:
- `visual_delta_threshold` (default `10.0`)
- `visual_sample_divisor_x` (default `72`)
- `visual_sample_divisor_y` (default `96`)
- `refresh_visual_baseline` (default `false`) — skips delta assert and only refreshes current captures
- `require_visual_baseline` (default `false`) — fails if baseline PNG is missing

Suggested refresh workflow (when intentional UI changes land):
1. Run androidTest with `refresh_visual_baseline=true` to write fresh captures.
2. Pull files from app cache folder `visual_regression/current/`.
3. Replace matching PNGs in this `visual_baselines/` folder.
4. Re-run androidTest with `require_visual_baseline=true` to verify drift thresholds.
5. If PNGs were edited manually, run `scripts/update_visual_baseline_manifest.ps1`.

Scripted helper (PowerShell):
- `scripts/refresh_visual_baselines.ps1`
- `scripts/check_adb_access.ps1` runs as a preflight and fails fast on local adb profile/ACL issues.
- Optional for local remediation: `scripts/check_adb_access.ps1 -AttemptFix`.
- `scripts/run_connected_android_tests.ps1` runs connected tests with adb preflight.
- Optional for local remediation: `scripts/run_connected_android_tests.ps1 -AttemptAdbFix`.
- `scripts/update_visual_baseline_manifest.ps1` regenerates `manifest.sha256` for committed PNGs.
- Optional: pass `-VerifyAfterRefresh` for a strict follow-up pass.
- Optional: pass `-DeviceId emulator-5554` for multi-device setups.

CI note:
- `.github/workflows/ci.yml` runs compact visual regression on pull requests using a pinned `pixel_4` / API 34 emulator profile.
- Strict CI verification uses `visual_delta_threshold=6.0` when the full compact baseline set is committed.
