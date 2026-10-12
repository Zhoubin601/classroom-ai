$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$evidence=Join-Path $projectRoot 'docs/20261011-sprint3-evidence-v1'
if(Get-NetTCPConnection -LocalPort 15179 -State Listen -ErrorAction SilentlyContinue){throw 'Dedicated preview port must be free'}
$legacyEnv=@{}
foreach($key in @('PLAYWRIGHT_MODULE','US0102_CHROMIUM_PATH','US0102_PREVIEW_URL','US0203_BROWSER')){$legacyEnv[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
if(-not $env:PLAYWRIGHT_MODULE){$env:PLAYWRIGHT_MODULE=Join-Path $env:USERPROFILE '.vscode/extensions/vscjava.migrate-java-to-azure-1.24.0-win32-x64/node_modules/playwright'}
if(-not $env:US0102_CHROMIUM_PATH){$env:US0102_CHROMIUM_PATH=Join-Path $env:LOCALAPPDATA 'ms-playwright/chromium-1228/chrome-win64/chrome.exe'}
$env:US0102_PREVIEW_URL='http://127.0.0.1:15179'
$env:US0203_BROWSER='true'
$previewProcess=Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js','preview','--host','127.0.0.1','--port','15179','--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence 'legacy-ui-frontend.log') -RedirectStandardError (Join-Path $evidence 'legacy-ui-frontend-error.log')
try{
 & (Join-Path $projectRoot 'scripts/run-us0102-mysql-tests.ps1') -Tests CourseBrowserMysqlTest *> (Join-Path $evidence 'legacy-browser-regression.log')
}finally{
 # Only terminate the child process created and retained by this invocation.
 if(-not $previewProcess.HasExited){$previewProcess.Kill()}
 foreach($key in $legacyEnv.Keys){[Environment]::SetEnvironmentVariable($key,$legacyEnv[$key],'Process')}
}
