param(
    [string]$PackageName = "com.pcosina.app",
    [string]$Module = "app",
    [string]$DeviceId = "",
    [switch]$VerifyAfterRefresh
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$gradle = Join-Path $root "gradlew.bat"
$baselineDir = Join-Path $root "$Module\src\androidTest\assets\visual_baselines"
$tmpDir = Join-Path $root "tmp\visual_baselines"
$remoteDir = "/data/user/0/$PackageName/cache/visual_regression/current"
$baselineFiles = @(
    "compact_profile_step3.png",
    "compact_chip_row.png",
    "compact_dashboard_today_outcome.png",
    "compact_progress_top_section.png",
    "compact_mealplan_top_section.png",
    "compact_login_first_win_card.png",
    "compact_profile_first_win_card.png",
    "compact_goal_handoff_card.png"
)

if (-not (Test-Path $gradle)) {
    throw "Could not find gradlew.bat at $gradle"
}

if (Test-Path (Join-Path $PSScriptRoot "android-env.ps1")) {
    . (Join-Path $PSScriptRoot "android-env.ps1")
}
if (Test-Path (Join-Path $PSScriptRoot "check_adb_access.ps1")) {
    & (Join-Path $PSScriptRoot "check_adb_access.ps1") -AttemptFix
}

New-Item -ItemType Directory -Force $baselineDir, $tmpDir | Out-Null

Push-Location $root
try {
    $testArgs = @(
        ":$Module:connectedDebugAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.class=com.pcosina.app.CompactWidthVisualRegressionTest",
        "-Pandroid.testInstrumentationRunnerArguments.refresh_visual_baseline=true"
    )
    if ($DeviceId) {
        $env:ANDROID_SERIAL = $DeviceId
    }

    & $gradle @testArgs
    if ($LASTEXITCODE -ne 0) {
        throw "connectedDebugAndroidTest failed (refresh pass)."
    }

    $adbBase = @("adb")
    if ($DeviceId) {
        $adbBase += @("-s", $DeviceId)
    }

    foreach ($file in $baselineFiles) {
        $localTmp = Join-Path $tmpDir $file
        $target = Join-Path $baselineDir $file
        $adbCmd = "$($adbBase -join ' ') exec-out run-as $PackageName cat $remoteDir/$file > `"$localTmp`""
        cmd /c $adbCmd | Out-Null
        if ((Test-Path $localTmp) -and ((Get-Item $localTmp).Length -gt 0)) {
            Copy-Item -Force $localTmp $target
            Write-Host "Updated baseline: $target"
        } else {
            Write-Warning "Could not pull $file. Ensure test ran and app cache is accessible via run-as."
        }
    }

    if (Test-Path (Join-Path $PSScriptRoot "update_visual_baseline_manifest.ps1")) {
        & (Join-Path $PSScriptRoot "update_visual_baseline_manifest.ps1") -Module $Module
    }

    if ($VerifyAfterRefresh) {
        $verifyArgs = @(
            ":$Module:connectedDebugAndroidTest",
            "-Pandroid.testInstrumentationRunnerArguments.class=com.pcosina.app.CompactWidthVisualRegressionTest",
            "-Pandroid.testInstrumentationRunnerArguments.require_visual_baseline=true",
            "-Pandroid.testInstrumentationRunnerArguments.visual_delta_threshold=6.0"
        )
        & $gradle @verifyArgs
        if ($LASTEXITCODE -ne 0) {
            throw "connectedDebugAndroidTest failed (verify pass)."
        }
    }
}
finally {
    Pop-Location
}
