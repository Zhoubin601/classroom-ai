$ErrorActionPreference = 'Stop'
$testRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$testResults = [ordered]@{}
try {
    & (Join-Path $PSScriptRoot 'setup.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Setup failed' }
    $testState = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'environment.json') -Raw | ConvertFrom-Json
    $env:PLAYWRIGHT_MODULE = Join-Path $env:USERPROFILE '.vscode/extensions/vscjava.migrate-java-to-azure-1.24.0-win32-x64/node_modules/playwright'
    $env:EXP3_CHROMIUM_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
    $env:EXP3_FRONTEND_URL = 'http://127.0.0.1:15173'
    $env:EXP3_BACKEND_URL = 'http://127.0.0.1:18081'
    $env:EXP3_EXPIRED_BACKEND_URL = 'http://127.0.0.1:18082'
    $env:EXP3_TEST_MYSQL_CONTAINER = $testState.mysql
    $env:EXP3_EVIDENCE_DIR = Join-Path $PSScriptRoot 'browser'
    $env:FIX_EXP3_EVIDENCE_DIR = Join-Path $PSScriptRoot 'office-strict'
    $env:EXP3_FIX_EVIDENCE_DIR = Join-Path $PSScriptRoot 'history-scores'
    foreach ($testSpec in @(
        @{name='eight-stories';script='exp3-real-browser.cjs'},
        @{name='office-strict';script='fix-exp3-acceptance.cjs'},
        @{name='history-scores';script='exp3-history-scores.cjs'}
    )) {
        & node (Join-Path $testRoot ('scripts/tests/' + $testSpec.script)) *> (Join-Path $PSScriptRoot ($testSpec.name + '.log'))
        $testResults[$testSpec.name] = $LASTEXITCODE
        Get-Content -LiteralPath (Join-Path $PSScriptRoot ($testSpec.name + '.log')) -Tail 12
    }
    if (@($testResults.Values | Where-Object { $_ -ne 0 }).Count) { throw 'Browser acceptance failed; inspect per-suite logs' }
} finally {
    $testResults | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'browser-exits.json') -Encoding utf8
    $testStatePath = Join-Path $PSScriptRoot 'environment.json'
    if (Test-Path -LiteralPath $testStatePath) {
        $testState = Get-Content -LiteralPath $testStatePath -Raw | ConvertFrom-Json
        if ($testState.frontendPid) {
            $testProcess = Get-Process -Id $testState.frontendPid -ErrorAction SilentlyContinue
            if ($testProcess) { Stop-Process -Id $testProcess.Id -Force }
        }
        foreach ($testContainer in @($testState.expiredBackend,$testState.backend,$testState.redis,$testState.mysql)) {
            & docker logs $testContainer *> (Join-Path $PSScriptRoot ($testContainer + '.log'))
            & docker stop $testContainer | Out-Null
        }
        & docker network rm $testState.network | Out-Null
    }
}
