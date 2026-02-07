$root = Resolve-Path (Join-Path $PSScriptRoot "..")

$env:JAVA_HOME = "C:\\Program Files\\Android\\Android Studio\\jbr"
$env:Path = "$env:JAVA_HOME\\bin;$env:Path"
$env:GRADLE_USER_HOME = "$root\\.gradle-user"
$env:ANDROID_USER_HOME = "$root\\.android"
$env:KOTLIN_DAEMON_RUN_FILES_PATH = "$root\\.kotlin-daemon"

New-Item -ItemType Directory -Force $env:GRADLE_USER_HOME,$env:ANDROID_USER_HOME,$env:KOTLIN_DAEMON_RUN_FILES_PATH | Out-Null

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
Write-Host "ANDROID_USER_HOME=$env:ANDROID_USER_HOME"
Write-Host "KOTLIN_DAEMON_RUN_FILES_PATH=$env:KOTLIN_DAEMON_RUN_FILES_PATH"
