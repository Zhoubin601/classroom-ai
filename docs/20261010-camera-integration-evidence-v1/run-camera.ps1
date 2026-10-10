$ErrorActionPreference='Stop'
$testRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$env:ROLE_TEST_MYSQL_PASSWORD=[Guid]::NewGuid().ToString('N')
$env:ROLE_TEST_PASSWORD=[Guid]::NewGuid().ToString('N')
$env:PLAYWRIGHT_MODULE='C:/Users/a3185/AppData/Local/uv/cache/archive-v0/GcBvtI027rUzG_nt/Lib/site-packages/playwright/driver/package'
$env:EXP3_CHROMIUM_PATH='C:/Program Files/Google/Chrome/Application/chrome.exe'
$env:BASE_URL='http://127.0.0.1:15173'
$env:BACKEND_URL='http://127.0.0.1:18081'
$env:SLOWMO='20'
$runResults=@()
try {
  & (Join-Path $PSScriptRoot 'setup.ps1') *> (Join-Path $PSScriptRoot 'setup-camera.log')
  Write-Output 'SETUP ready'
  $testState=Get-Content (Join-Path $PSScriptRoot 'environment.json') | ConvertFrom-Json
  $env:EXP3_TEST_MYSQL_CONTAINER=$testState.mysql
  for($attempt=0;$attempt -lt 30;$attempt++) {
    try {if((Invoke-WebRequest $env:BASE_URL -TimeoutSec 2).StatusCode -eq 200){break}}catch{}
    Start-Sleep -Seconds 1
  }
  foreach($testSuite in @('camera-micro-browser','verify-fixes')) {
    Write-Output ('RUN '+$testSuite)
    & node (Join-Path $PSScriptRoot ($testSuite+'.cjs')) *> (Join-Path $PSScriptRoot ($testSuite+'-final.log'))
    $runResults+=@{suite=$testSuite;exitCode=$LASTEXITCODE}
    $runResults | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'camera-run-results.json')
    Get-Content (Join-Path $PSScriptRoot ($testSuite+'-final.log')) -Tail 15
  }
}finally {
  if(Test-Path (Join-Path $PSScriptRoot 'environment.json')) {
    & (Join-Path $PSScriptRoot 'cleanup.ps1') *> (Join-Path $PSScriptRoot 'cleanup-camera.log')
    Write-Output 'CLEANUP completed'
  }
  Remove-Item Env:ROLE_TEST_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:ROLE_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue
}
if($runResults | Where-Object {$_.exitCode -ne 0}) {exit 1}
