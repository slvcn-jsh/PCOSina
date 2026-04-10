param(
  [string]$Notes = "",
  [string]$NotesFile = "release-notes.txt",
  [string]$Group = "QUADRANT",
  [string]$AppId = "1:950408114415:android:0b3c55b663b7638c20ab1a",
  [switch]$AllowInsecureLocalSigning,
  [switch]$SkipFirebaseDistribution,
  [string[]]$ExtraGradleArgs = @()
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$gradle = Join-Path $root "gradlew.bat"
$apkPath = Join-Path $root "app/build/outputs/apk/release/app-release.apk"
$notesPath = if ([System.IO.Path]::IsPathRooted($NotesFile)) {
  $NotesFile
} else {
  Join-Path $root $NotesFile
}

if (-not (Test-Path $gradle)) {
  throw "Could not find gradlew.bat at $gradle"
}

if (Test-Path (Join-Path $PSScriptRoot "android-env.ps1")) {
  . (Join-Path $PSScriptRoot "android-env.ps1")
}

if ($AllowInsecureLocalSigning) {
  $env:PCOSINA_ALLOW_INSECURE_RELEASE_SIGNING = "true"
}

if (-not $Notes -and -not (Test-Path $notesPath)) {
  "v1.10.3: Automated release. Add notes here." | Set-Content -Path $notesPath
}

if ($Notes) {
  $Notes | Set-Content -Path $notesPath
}

Write-Host "Building release APK..."
Push-Location $root
try {
  $gradleArgs = @(":app:assembleRelease", "--no-daemon")
  if ($ExtraGradleArgs.Count -gt 0) {
    $gradleArgs += $ExtraGradleArgs
  }
  & $gradle @gradleArgs
  if ($LASTEXITCODE -ne 0) {
    throw "assembleRelease failed. Args: $($gradleArgs -join ' ')"
  }
}
finally {
  Pop-Location
}

if (-not (Test-Path $apkPath)) {
  throw "APK not found at $apkPath"
}

if ($SkipFirebaseDistribution) {
  Write-Host "Skipping Firebase App Distribution upload."
  Write-Host "Release APK ready at $apkPath"
  return
}

$firebase = Get-Command firebase -ErrorAction SilentlyContinue
if ($null -eq $firebase) {
  throw "firebase CLI not found. Install it or rerun with -SkipFirebaseDistribution."
}

Write-Host "Distributing to Firebase App Distribution..."
& $firebase.Source appdistribution:distribute $apkPath --app $AppId --groups $Group --release-notes-file $notesPath
if ($LASTEXITCODE -ne 0) {
  throw "firebase appdistribution:distribute failed."
}

Write-Host "Done."
