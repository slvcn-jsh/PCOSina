param(
  [string]$Notes = "",
  [string]$NotesFile = "release-notes.txt",
  [string]$Group = "QUADRANT",
  [string]$AppId = "1:950408114415:android:0b3c55b663b7638c20ab1a"
)

$ErrorActionPreference = "Stop"

$apkPath = "app/build/outputs/apk/release/app-release.apk"

if (-not $Notes -and -not (Test-Path $NotesFile)) {
  "v1.08.2: Automated release. Add notes here." | Set-Content -Path $NotesFile
}

if ($Notes) {
  $Notes | Set-Content -Path $NotesFile
}

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "Building release APK..."
.\gradlew.bat assembleRelease --no-daemon

if (-not (Test-Path $apkPath)) {
  throw "APK not found at $apkPath"
}

Write-Host "Distributing to Firebase App Distribution..."
firebase appdistribution:distribute $apkPath --app $AppId --groups $Group --release-notes-file $NotesFile

Write-Host "Done."
