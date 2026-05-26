param(
    [string]$DeviceId = "",
    [switch]$AttemptAdbFix,
    [switch]$SkipBackendBenchmark,
    [switch]$SkipConnectedTests
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$reportsDir = Join-Path $root "benchmarks\reports"
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$reportPrefix = "defense_smoke_$timestamp"
$evidencePath = Join-Path $reportsDir "$reportPrefix.summary.txt"

New-Item -ItemType Directory -Force $reportsDir | Out-Null

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "== $Message =="
}

function Resolve-AdbPath {
    $candidates = @()
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $adbCommand) {
        $candidates += $adbCommand.Source
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe")
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
    }
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
    }
    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }
    throw "adb executable not found. Install Android platform-tools or set ANDROID_HOME/ANDROID_SDK_ROOT."
}

function Add-Summary {
    param([string]$Line)
    Add-Content -Path $evidencePath -Value $Line
}

Set-Content -Path $evidencePath -Value @(
    "PCOSina defense smoke evidence",
    "Started: $(Get-Date -Format o)",
    "Root: $root",
    ""
)

if (Test-Path (Join-Path $PSScriptRoot "android-env.ps1")) {
    . (Join-Path $PSScriptRoot "android-env.ps1")
}

$adb = Resolve-AdbPath
if (Test-Path (Join-Path $PSScriptRoot "check_adb_access.ps1")) {
    & (Join-Path $PSScriptRoot "check_adb_access.ps1") -AdbPath $adb -AttemptFix:$AttemptAdbFix
}

if ($DeviceId) {
    $env:ANDROID_SERIAL = $DeviceId
}

if (-not $SkipConnectedTests) {
    Write-Step "Checking connected Android device"
    $devicesOutput = & $adb devices -l
    $devicesText = ($devicesOutput | Out-String).Trim()
    Write-Host $devicesText
    Add-Summary "ADB devices:"
    Add-Summary $devicesText
    Add-Summary ""

    $readyDevices = @(
        $devicesOutput |
            Where-Object { $_ -match "\sdevice\s" -and $_ -notmatch "^List of devices attached" }
    )
    if ($readyDevices.Count -eq 0) {
        throw "No authorized Android device/emulator is connected. Pair wireless debugging or connect USB, then rerun."
    }
}

if (-not $SkipBackendBenchmark) {
    Write-Step "Running 20-profile backend planner benchmark"
    $defaultModelPath = Join-Path $root "ml\offline_training\artifacts\model_v1\lightgbm_v1_model.txt"
    $defaultMetricsPath = Join-Path $root "ml\offline_training\artifacts\model_v1\training_metrics.json"
    if ([string]::IsNullOrWhiteSpace($env:PCOSINA_ML_MODEL_PATH) -and (Test-Path $defaultModelPath)) {
        $env:PCOSINA_ML_MODEL_PATH = $defaultModelPath
    }
    if ([string]::IsNullOrWhiteSpace($env:PCOSINA_ML_METRICS_PATH) -and (Test-Path $defaultMetricsPath)) {
        $env:PCOSINA_ML_METRICS_PATH = $defaultMetricsPath
    }

    Push-Location $root
    try {
        & python scripts/benchmark_planner_profiles.py `
            --runs 1 `
            --require-ml-ready `
            --fail-on-regression `
            --max-failures 0 `
            --max-runtime-ms 5000 `
            --p95-runtime-ms 3000 `
            --report-prefix $reportPrefix
        if ($LASTEXITCODE -ne 0) {
            throw "Backend planner benchmark failed."
        }
    }
    finally {
        Pop-Location
    }
    Add-Summary "Backend benchmark: PASSED"
    Add-Summary "Backend benchmark JSON: benchmarks/reports/$reportPrefix.json"
    Add-Summary "Backend benchmark CSV: benchmarks/reports/$reportPrefix.csv"
    Add-Summary ""
}

if (-not $SkipConnectedTests) {
    Write-Step "Running connected Android smoke tests"
    $criticalTestBatches = @(
        @("com.pcosina.app.CurrentCoreFlowUiTest"),
        @(
            "com.pcosina.app.AppLaunchTest",
            "com.pcosina.app.MealPlanNoSafePlanUiTest#offlineSavedWeek_keepsPlanVisibleAndDisablesGenerateNewWeek",
            "com.pcosina.app.GroceryFeedbackSemanticsUiTest#expandCollapseAll_showsConfirmationBanner",
            "com.pcosina.app.ProgressPerUserPersistenceUiTest"
        )
    )

    foreach ($batch in $criticalTestBatches) {
        $testArg = $batch -join ","
        $extraArgs = @(
            "-Pandroid.testInstrumentationRunnerArguments.class=$testArg"
        )

        & (Join-Path $PSScriptRoot "run_connected_android_tests.ps1") `
            -DeviceId $DeviceId `
            -AttemptAdbFix:$AttemptAdbFix `
            -ExtraGradleArgs $extraArgs
    }

    Add-Summary "Connected Android smoke tests: PASSED"
    Add-Summary "Connected test classes:"
    foreach ($batch in $criticalTestBatches) {
        foreach ($testName in $batch) {
            Add-Summary "- $testName"
        }
    }
    Add-Summary ""

    $screenshotName = "$reportPrefix.device.png"
    $remoteScreenshot = "/sdcard/$screenshotName"
    $localScreenshot = Join-Path $reportsDir $screenshotName
    Write-Step "Capturing final device screenshot"
    & $adb shell screencap -p $remoteScreenshot | Out-Null
    & $adb pull $remoteScreenshot $localScreenshot | Out-Null
    & $adb shell rm $remoteScreenshot | Out-Null
    Add-Summary "Device screenshot: benchmarks/reports/$screenshotName"
    Add-Summary ""
}

Add-Summary "Finished: $(Get-Date -Format o)"

Write-Host ""
Write-Host "Defense smoke evidence complete:"
Write-Host $evidencePath
