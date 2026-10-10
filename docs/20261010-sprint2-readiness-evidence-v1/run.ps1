$ErrorActionPreference = 'Stop'
$env:ROLE_TEST_MYSQL_PASSWORD = [Guid]::NewGuid().ToString('N')
$env:ROLE_TEST_PASSWORD = [Guid]::NewGuid().ToString('N')
$env:PLAYWRIGHT_MODULE = 'C:/Users/a3185/AppData/Local/uv/cache/archive-v0/GcBvtI027rUzG_nt/Lib/site-packages/playwright/driver/package'
$env:EXP3_CHROMIUM_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
$env:EXP3_FRONTEND_URL = 'http://127.0.0.1:15173'
$env:EXP3_BACKEND_URL = 'http://127.0.0.1:18081'
$env:EXP3_EXPIRED_BACKEND_URL = 'http://127.0.0.1:18082'
$env:EXP3_EVIDENCE_DIR = Join-Path $PSScriptRoot 'browser'
$testExit = 1
try {
    & (Join-Path $PSScriptRoot 'setup.ps1') *> (Join-Path $PSScriptRoot 'setup.log')
    Write-Output 'SETUP: isolated real MySQL/Redis/current backend/frontend ready'
    $state = Get-Content (Join-Path $PSScriptRoot 'environment.json') | ConvertFrom-Json
    $env:EXP3_TEST_MYSQL_CONTAINER = $state.mysql
    $ready = $false
    for ($i=0; $i -lt 30; $i++) {
        try { if ((Invoke-WebRequest $env:EXP3_FRONTEND_URL -TimeoutSec 2).StatusCode -eq 200) {$ready=$true;break} } catch {}
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Test frontend not ready' }
    & node (Join-Path $PSScriptRoot 'sprint2-browser.cjs') *> (Join-Path $PSScriptRoot 'browser.log')
    $testExit = $LASTEXITCODE
    @{exitCode=$testExit; frontend=$env:EXP3_FRONTEND_URL; backend=$env:EXP3_BACKEND_URL; mockedBusinessResponses=$false; observedAt=[DateTimeOffset]::Now.ToString('o')} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'browser-result.json')
    Get-Content (Join-Path $PSScriptRoot 'browser.log') -Tail 16
    & node (Join-Path $PSScriptRoot 'browser-version.cjs') *> (Join-Path $PSScriptRoot 'browser-version.log')
} finally {
    if (Test-Path (Join-Path $PSScriptRoot 'environment.json')) {
        & (Join-Path $PSScriptRoot 'cleanup.ps1') *> (Join-Path $PSScriptRoot 'cleanup.log')
        Write-Output 'CLEANUP: temporary test services removed'
    }
    Remove-Item Env:ROLE_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:ROLE_TEST_PASSWORD -ErrorAction SilentlyContinue
}
exit $testExit
