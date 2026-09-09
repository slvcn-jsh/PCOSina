param(
    [string]$Module = "app",
    [string]$TestClass = "",
    [string]$DeviceId = "",
    [string]$PackageName = "com.pcosina.app",
    [string]$GradleUserHome = "",
    [string[]]$ExtraGradleArgs = @(),
    [switch]$AttemptAdbFix,
    [switch]$AllowInstalledAppRemoval
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
if ($DeviceId) {
    $env:ANDROID_SERIAL = $DeviceId
}

if (Test-Path (Join-Path $PSScriptRoot "check_adb_access.ps1")) {
    & (Join-Path $PSScriptRoot "check_adb_access.ps1") -AttemptFix:$AttemptAdbFix
}
if (-not [string]::IsNullOrWhiteSpace($GradleUserHome)) {
    $env:GRADLE_USER_HOME = (Resolve-Path $GradleUserHome).Path
}

function Resolve-AndroidTool {
    param(
        [string]$CommandName,
        [string]$RelativeSdkPath
    )

    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($null -ne $command -and (Test-Path $command.Source)) {
        return $command.Source
    }
    $sdkRoots = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA "Android\Sdk" })
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path $_) }
    foreach ($sdkRoot in $sdkRoots) {
        $candidate = Join-Path $sdkRoot $RelativeSdkPath
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    throw "$CommandName executable not found in PATH or the Android SDK."
}

function Resolve-ApkSigner {
    $command = Get-Command "apksigner" -ErrorAction SilentlyContinue
    if ($null -ne $command -and (Test-Path $command.Source)) {
        return $command.Source
    }
    $sdkRoots = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA "Android\Sdk" })
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path $_) }
    foreach ($sdkRoot in $sdkRoots) {
        $buildToolsRoot = Join-Path $sdkRoot "build-tools"
        if (-not (Test-Path $buildToolsRoot)) {
            continue
        }
        $candidate = Get-ChildItem $buildToolsRoot -Directory |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName "apksigner.bat" } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($candidate) {
            return $candidate
        }
    }
    throw "apksigner executable not found in PATH or Android SDK build-tools."
}

function Get-ApkCertificateSha256 {
    param(
        [string]$ApkSigner,
        [string]$ApkPath
    )

    $certificateOutput = & $ApkSigner verify --print-certs $ApkPath 2>$null
    $match = [regex]::Match(
        ($certificateOutput | Out-String),
        "certificate SHA-256 digest:\s*([0-9a-fA-F]+)",
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if (-not $match.Success) {
        throw "Could not read the APK signing certificate: $ApkPath"
    }
    return $match.Groups[1].Value.ToLowerInvariant()
}

$debugApk = Join-Path $root "$Module/build/outputs/apk/debug/$Module-debug.apk"
Push-Location $root
try {
    & $gradle ":${Module}:assembleDebug" "--no-daemon"
    if ($LASTEXITCODE -ne 0) {
        throw "assembleDebug failed before the device signature preflight."
    }
}
finally {
    Pop-Location
}

$adb = Resolve-AndroidTool -CommandName "adb" -RelativeSdkPath "platform-tools\adb.exe"
$installedPathLine = & $adb shell pm path $PackageName 2>$null | Select-Object -First 1
if ($LASTEXITCODE -eq 0 -and $installedPathLine -match "^package:(.+)$") {
    $preflightDir = Join-Path $root "build\device-preflight"
    New-Item -ItemType Directory -Force $preflightDir | Out-Null
    $installedApk = Join-Path $preflightDir "$PackageName-installed.apk"
    & $adb pull $matches[1].Trim() $installedApk | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not copy the installed $PackageName APK for signature verification."
    }
    $apkSigner = Resolve-ApkSigner
    $installedSha = Get-ApkCertificateSha256 -ApkSigner $apkSigner -ApkPath $installedApk
    $debugSha = Get-ApkCertificateSha256 -ApkSigner $apkSigner -ApkPath $debugApk
    if ($installedSha -ne $debugSha) {
        if (-not $AllowInstalledAppRemoval) {
            throw (
                "Connected debug tests stopped before installation: the installed $PackageName certificate " +
                "does not match the debug APK. This protects the signed-in Firebase tester app and local data. " +
                "Use an emulator, or rerun with -AllowInstalledAppRemoval only after accepting that uninstalling " +
                "the installed app clears its local data and authentication state."
            )
        }
        & $adb uninstall $PackageName
        if ($LASTEXITCODE -ne 0) {
            throw "Could not uninstall $PackageName after explicit -AllowInstalledAppRemoval approval."
        }
    }
}

$gradleArgs = @(":${Module}:connectedDebugAndroidTest")
if (-not [string]::IsNullOrWhiteSpace($TestClass)) {
    $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"
}
if ($ExtraGradleArgs.Count -gt 0) {
    $gradleArgs += $ExtraGradleArgs
}

Push-Location $root
try {
    & $gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "connectedDebugAndroidTest failed. Args: $($gradleArgs -join ' ')"
    }
}
finally {
    Pop-Location
}
