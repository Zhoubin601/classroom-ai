param([switch]$Headed)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent

if (-not $env:PLAYWRIGHT_MODULE) {
    $candidate = Get-ChildItem (Join-Path $env:USERPROFILE '.vscode/extensions') -Directory -Filter 'vscjava.migrate-java-to-azure-*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'node_modules/playwright' } |
        Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($candidate) { $env:PLAYWRIGHT_MODULE = $candidate }
}
if (-not $env:EXP3_CHROMIUM_PATH) {
    $chrome = Join-Path $env:ProgramFiles 'Google/Chrome/Application/chrome.exe'
    if (Test-Path $chrome) { $env:EXP3_CHROMIUM_PATH = $chrome }
}
if (-not $env:BASE_URL) { $env:BASE_URL = 'http://127.0.0.1:5173' }
if (-not $env:BACKEND_URL) { $env:BACKEND_URL = 'http://127.0.0.1:8080' }
$env:HEADLESS = if ($Headed) { 'false' } else { 'true' }

Write-Host ">>> Running Playwright all-features E2E test suite..." -ForegroundColor Cyan
& node (Join-Path $projectRoot 'scripts/tests/all-features-playwright.cjs')
if ($LASTEXITCODE -ne 0) {
    Write-Error "Playwright all-features test suite failed"
} else {
    Write-Host ">>> Playwright all-features E2E test suite PASSED!" -ForegroundColor Green
}
