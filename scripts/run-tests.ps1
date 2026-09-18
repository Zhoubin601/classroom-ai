param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) { $mavenPath = $maven.Source } else {
    $mavenPath = Get-ChildItem (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mavenPath) { throw 'Maven not found. Install Maven or add mvn.cmd to PATH.' }
Push-Location (Join-Path $projectRoot 'backend')
try {
    $mavenArgs = @("-Dmaven.repo.local=$env:USERPROFILE/.m2/repository", 'test')
    if ($Offline) { $mavenArgs += '-o' }
    & $mavenPath @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed' }
} finally { Pop-Location }
Push-Location (Join-Path $projectRoot 'frontend')
try {
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
} finally { Pop-Location }
Push-Location $projectRoot
try {
    & python -m unittest discover -s vision/tests -p 'test_*.py'
    if ($LASTEXITCODE -ne 0) { throw 'Edge regression tests failed' }
} finally { Pop-Location }
Write-Host 'All automated checks passed.' -ForegroundColor Green
