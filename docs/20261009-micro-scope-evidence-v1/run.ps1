$ErrorActionPreference='Stop'
$testRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$env:ROLE_TEST_MYSQL_PASSWORD=[Guid]::NewGuid().ToString('N')
$env:ROLE_TEST_PASSWORD=[Guid]::NewGuid().ToString('N')
$env:PLAYWRIGHT_MODULE='C:/Users/a3185/AppData/Local/uv/cache/archive-v0/GcBvtI027rUzG_nt/Lib/site-packages/playwright/driver/package'
$env:EXP3_CHROMIUM_PATH='C:/Program Files/Google/Chrome/Application/chrome.exe'
$env:MICRO_SCOPE_EVIDENCE_DIR=$PSScriptRoot
try {
  & (Join-Path $PSScriptRoot 'setup.ps1') *> (Join-Path $PSScriptRoot 'setup.log')
  'SETUP ready: isolated real services'
  for($testAttempt=0;$testAttempt -lt 30;$testAttempt++) {
    try {if((Invoke-WebRequest 'http://127.0.0.1:15173' -TimeoutSec 2).StatusCode -eq 200){break}}catch{}
    Start-Sleep -Seconds 1
  }
  & node (Join-Path $testRoot 'scripts/tests/micro-scope-browser.cjs') *> (Join-Path $PSScriptRoot 'browser.log')
  $testExit=$LASTEXITCODE
  Get-Content (Join-Path $PSScriptRoot 'browser.log') -Tail 14
  if($testExit -ne 0) {throw 'Micro-slice scope browser acceptance failed'}
} finally {
  if(Test-Path (Join-Path $PSScriptRoot 'environment.json')) {
    & (Join-Path $PSScriptRoot 'cleanup.ps1') *> (Join-Path $PSScriptRoot 'cleanup.log')
    'CLEANUP completed'
  }
  Remove-Item Env:ROLE_TEST_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:ROLE_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue
}
