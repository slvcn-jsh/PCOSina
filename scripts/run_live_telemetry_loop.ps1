param(
    [int]$LoopCount = 1,
    [int]$PauseMs = 1500,
    [string]$BaseUrl = "https://pcosina-backend.onrender.com/",
    [string]$DeviceSerial = "",
    [string]$LiveEmail = $env:PCOSINA_LIVE_EMAIL,
    [string]$LivePassword = $env:PCOSINA_LIVE_PASSWORD
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "android-env.ps1")
& (Join-Path $PSScriptRoot "check_adb_access.ps1")

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

function Get-AdbTargetArgs {
    if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
        return @()
    }
    return @("-s", $DeviceSerial)
}

function Get-ConnectedDevices {
    $targetArgs = Get-AdbTargetArgs
    $output = & $adb @targetArgs devices
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed."
    }
    $rows = @()
    foreach ($line in ($output | Select-Object -Skip 1)) {
        $trimmed = "$line".Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            continue
        }
        $parts = $trimmed -split "\s+"
        if ($parts.Length -lt 2) {
            continue
        }
        $rows += [pscustomobject]@{
            Serial = $parts[0]
            State = $parts[1]
        }
    }
    return $rows
}

& $adb start-server | Out-Null
$devices = Get-ConnectedDevices

Write-Host "adb visible devices:"
if ($devices.Count -eq 0) {
    Write-Host "  <none>"
} else {
    $devices | Format-Table -AutoSize | Out-Host
}

$readyDevices = $devices | Where-Object { $_.State -eq "device" }
$unauthorizedDevices = $devices | Where-Object { $_.State -eq "unauthorized" }

if ($unauthorizedDevices.Count -gt 0) {
    throw "adb sees an unauthorized device. Unlock the phone, accept the USB debugging prompt, then rerun."
}

if ($readyDevices.Count -eq 0) {
    throw "No authorized Android device is connected. Connect the phone with USB debugging enabled, keep it unlocked, run 'adb devices', and rerun."
}

if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $env:ANDROID_SERIAL = $DeviceSerial
}

if (-not $BaseUrl.EndsWith("/")) {
    $BaseUrl = "$BaseUrl/"
}

$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $gradleArgs = @(
        ":app:installDebug",
        ":app:installDebugAndroidTest",
        ":app:connectedDebugAndroidTest",
        "-PdebugBaseUrl=$BaseUrl",
        "-Pandroid.testInstrumentationRunnerArguments.class=com.pcosina.app.LiveTelemetryLoopInstrumentedTest",
        "-Pandroid.testInstrumentationRunnerArguments.pcosina.loopCount=$LoopCount",
        "-Pandroid.testInstrumentationRunnerArguments.pcosina.pauseMs=$PauseMs",
        "-Pandroid.injected.testOnly=false",
        "--no-configuration-cache",
        "--no-daemon"
    )

    if (-not [string]::IsNullOrWhiteSpace($LiveEmail)) {
        $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.pcosina.liveEmail=$LiveEmail"
    }
    if (-not [string]::IsNullOrWhiteSpace($LivePassword)) {
        $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.pcosina.livePassword=$LivePassword"
    }

    & .\gradlew.bat `
        @gradleArgs
} finally {
    Pop-Location
}
