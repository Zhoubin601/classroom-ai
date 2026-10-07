param([Parameter(Mandatory=$true)][string]$EvidenceDirectory)
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$evidence=[IO.Path]::GetFullPath($EvidenceDirectory)
$state=Get-Content -LiteralPath (Join-Path $evidence 'environment.json') -Raw | ConvertFrom-Json
$env:PLAYWRIGHT_MODULE='C:/Users/a3185/.vscode/extensions/vscjava.migrate-java-to-azure-1.24.0-win32-x64/node_modules/playwright'
$env:EXP3_CHROMIUM_PATH=Join-Path $env:LOCALAPPDATA 'ms-playwright/chromium-1228/chrome-win64/chrome.exe'
$env:ROLE_EVIDENCE_DIR=$evidence
$env:EXP3_FRONTEND_URL=$state.frontend
$env:EXP3_BACKEND_URL=$state.backend
$env:EXP3_EXPIRED_BACKEND_URL=$state.expired
$env:EXP3_TEST_MYSQL_CONTAINER=$state.mysql
$env:EXP3_EVIDENCE_DIR=Join-Path $evidence 'sprint2'
$env:FIX_EXP3_EVIDENCE_DIR=Join-Path $evidence 'office'
$env:ALL_FEATURES_EVIDENCE_DIR=Join-Path $evidence 'all-features'
$env:BASE_URL=$state.frontend
$env:BACKEND_URL=$state.backend
$env:FORWARD_BACKEND='false'
$env:SLOWMO='0'
$runs=@('exp3-real-browser','fix-exp3-acceptance','all-features-playwright','role-connectivity-browser','role-scheduling-browser','role-scope-browser','role-fix-browser')
$results=@()
foreach($run in $runs){
  Write-Output "Running $run"
  & node (Join-Path $PSScriptRoot ($run+'.cjs')) *> (Join-Path $evidence ($run+'.log'))
  $code=$LASTEXITCODE
  $results+=@{script=$run;exitCode=$code}
  $results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'runner-results.json') -Encoding utf8
  Write-Output "$run exit=$code"
}
if($results.Where({$_.exitCode -ne 0}).Count -gt 0){exit 1}
