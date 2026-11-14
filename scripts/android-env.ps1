$root = Resolve-Path (Join-Path $PSScriptRoot "..")

function Add-OptionFlag {
    param(
        [string]$CurrentValue,
        [string]$Flag
    )

    if ([string]::IsNullOrWhiteSpace($CurrentValue)) {
        return $Flag
    }
    if ($CurrentValue.Contains($Flag)) {
        return $CurrentValue
    }
    return "$Flag $CurrentValue".Trim()
}

$defaultJavaHomeCandidates = @(
    "C:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\Android\Android Studio1\jbr",
    "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\jbr"
)
$defaultJavaHome = $defaultJavaHomeCandidates |
    Where-Object { Test-Path (Join-Path $_ "bin\java.exe") } |
    Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    if ($defaultJavaHome) {
        $env:JAVA_HOME = $defaultJavaHome
    } else {
        throw "JAVA_HOME is not set and no usable Android Studio/JetBrains JBR was found."
    }
} elseif (-not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    throw "JAVA_HOME does not contain bin\java.exe: $env:JAVA_HOME"
}

$javaBin = Join-Path $env:JAVA_HOME "bin"
if (-not $env:Path.Contains($javaBin)) {
    $env:Path = "$javaBin;$env:Path"
}

$env:GRADLE_USER_HOME = Join-Path $root ".gradle-user"
$env:KOTLIN_DAEMON_RUN_FILES_PATH = Join-Path $root ".kotlin-daemon"
$toolHome = Join-Path $root ".android-user-home"
$legacyAndroidDotDir = Join-Path $toolHome ".android"

$env:ANDROID_USER_HOME = $legacyAndroidDotDir
$env:HOME = $toolHome
$env:USERPROFILE = $toolHome
$env:JAVA_TOOL_OPTIONS = Add-OptionFlag -CurrentValue $env:JAVA_TOOL_OPTIONS -Flag "-Duser.home=$toolHome"
$env:GRADLE_OPTS = Add-OptionFlag -CurrentValue $env:GRADLE_OPTS -Flag "-Duser.home=$toolHome"

if (Test-Path Env:ANDROID_PREFS_ROOT) {
    Remove-Item Env:ANDROID_PREFS_ROOT
}
if (Test-Path Env:ANDROID_SDK_HOME) {
    Remove-Item Env:ANDROID_SDK_HOME
}

New-Item -ItemType Directory -Force @(
    $env:GRADLE_USER_HOME,
    $env:KOTLIN_DAEMON_RUN_FILES_PATH,
    $toolHome,
    $legacyAndroidDotDir
) | Out-Null
New-Item -ItemType File -Force (Join-Path $legacyAndroidDotDir "analytics.settings") | Out-Null

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
Write-Host "ANDROID_USER_HOME=$env:ANDROID_USER_HOME"
Write-Host "HOME=$env:HOME"
Write-Host "USERPROFILE=$env:USERPROFILE"
Write-Host "KOTLIN_DAEMON_RUN_FILES_PATH=$env:KOTLIN_DAEMON_RUN_FILES_PATH"
