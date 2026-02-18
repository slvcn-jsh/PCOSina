param(
    [string]$AdbPath = "",
    [switch]$AttemptFix
)

$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    param([string]$InputPath)

    if (-not [string]::IsNullOrWhiteSpace($InputPath) -and (Test-Path $InputPath)) {
        return (Resolve-Path $InputPath).Path
    }

    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $command -and (Test-Path $command.Source)) {
        return $command.Source
    }

    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidate = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidate = Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidate = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "adb executable not found. Install Android platform-tools or provide -AdbPath."
}

$adb = Resolve-AdbPath -InputPath $AdbPath
$oldErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $command = '"' + $adb + '" version 2>&1'
    $output = cmd /c $command
    $exitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $oldErrorActionPreference
}
$text = ($output | Out-String).Trim()

if ($exitCode -ne 0 -or $text -match "Cannot mkdir\s+'([^']+\.android)'") {
    $match = [regex]::Match(
        $text,
        "Cannot mkdir\s+'([^']+\.android)'",
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    $blockedPath = if ($match.Success) { $match.Groups[1].Value } else { "<unknown>" }
    if ($AttemptFix -and $match.Success) {
        try {
            New-Item -ItemType Directory -Force $blockedPath | Out-Null
            New-Item -ItemType File -Force (Join-Path $blockedPath "analytics.settings") | Out-Null
            $retryOutput = cmd /c ('"' + $adb + '" version 2>&1')
            if ($LASTEXITCODE -eq 0) {
                Write-Host "adb preflight auto-fix succeeded: $blockedPath"
                Write-Host "adb preflight OK: $adb"
                return
            }
        } catch {
            # Fall through to explicit remediation message.
        }
    }

    $remediation = if ($match.Success) {
        "Try (may require elevated shell): " +
            "New-Item -ItemType Directory -Force '$blockedPath'; " +
            "New-Item -ItemType File -Force '$blockedPath\\analytics.settings'. " +
            "If access is denied, rerun this command in an elevated PowerShell session."
    } else {
        "Ensure the executing account has a writable home profile or pre-create the required .android directory."
    }
    throw (
        "adb preflight failed. adb output: $text`n" +
            "Likely profile/ACL issue creating .android path ($blockedPath). " +
            $remediation
    )
}

Write-Host "adb preflight OK: $adb"
