param(
    [string]$Module = "app"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$baselineDir = Join-Path $root "$Module\src\androidTest\assets\visual_baselines"
$manifestPath = Join-Path $baselineDir "manifest.sha256"

if (-not (Test-Path $baselineDir)) {
    throw "Baseline directory not found: $baselineDir"
}

$pngFiles = Get-ChildItem -Path $baselineDir -Filter "*.png" | Sort-Object Name
if ($pngFiles.Count -eq 0) {
    throw "No baseline PNG files found under $baselineDir"
}

$lines = $pngFiles | ForEach-Object {
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $($_.Name)"
}

$content = ($lines -join [Environment]::NewLine) + [Environment]::NewLine
Set-Content -Path $manifestPath -Value $content -NoNewline
Write-Host "Updated visual baseline manifest: $manifestPath"
