$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent

$env:PLAYWRIGHT_MODULE = 'C:\Users\a3185\.vscode\extensions\vscjava.migrate-java-to-azure-1.24.0-win32-x64\node_modules\playwright'
$env:EXP3_CHROMIUM_PATH = 'C:\Users\a3185\AppData\Local\ms-playwright\chromium-1228\chrome-win64\chrome.exe'
$env:BASE_URL = 'http://127.0.0.1:5173'
$env:BACKEND_URL = 'http://127.0.0.1:8080'
$env:HEADLESS = if ($args -contains '-Headed') { 'false' } else { 'true' }

Write-Host ">>> Running Playwright all-features E2E test suite..." -ForegroundColor Cyan
& node (Join-Path $projectRoot 'scripts/tests/all-features-playwright.cjs')
if ($LASTEXITCODE -ne 0) {
    Write-Error "Playwright all-features test suite failed"
} else {
    Write-Host ">>> Playwright all-features E2E test suite PASSED!" -ForegroundColor Green
}
