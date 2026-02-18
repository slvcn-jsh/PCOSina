param(
    [string]$Module = "app",
    [string]$TestClass = "",
    [string]$DeviceId = "",
    [string[]]$ExtraGradleArgs = @(),
    [switch]$AttemptAdbFix
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$gradle = Join-Path $root "gradlew.bat"

if (-not (Test-Path $gradle)) {
    throw "Could not find gradlew.bat at $gradle"
}

if (Test-Path (Join-Path $PSScriptRoot "android-env.ps1")) {
    . (Join-Path $PSScriptRoot "android-env.ps1")
}
if (Test-Path (Join-Path $PSScriptRoot "check_adb_access.ps1")) {
    & (Join-Path $PSScriptRoot "check_adb_access.ps1") -AttemptFix:$AttemptAdbFix
}

if ($DeviceId) {
    $env:ANDROID_SERIAL = $DeviceId
}

$args = @(":$Module:connectedDebugAndroidTest")
if (-not [string]::IsNullOrWhiteSpace($TestClass)) {
    $args += "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"
}
if ($ExtraGradleArgs.Count -gt 0) {
    $args += $ExtraGradleArgs
}

Push-Location $root
try {
    & $gradle @args
    if ($LASTEXITCODE -ne 0) {
        throw "connectedDebugAndroidTest failed. Args: $($args -join ' ')"
    }
}
finally {
    Pop-Location
}
