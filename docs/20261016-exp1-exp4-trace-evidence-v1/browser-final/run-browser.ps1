$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$evidence=$PSScriptRoot
$name='exp4-recheck-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$mysql=$null;$redis=$null;$preview=$null;$app=$null
function Native([string]$exe,[string[]]$commandArgs,[string]$log=''){
 $prior=$ErrorActionPreference;$ErrorActionPreference='Continue'
 try{$nativeOutput=& $exe @commandArgs 2>&1;$exitCode=$LASTEXITCODE}finally{$ErrorActionPreference=$prior}
 if($log){$nativeOutput | Out-File -LiteralPath $log -Encoding utf8}
 if($exitCode -ne 0){throw "Failed: $exe ($exitCode); $log"}
 if(-not $log){$nativeOutput}
}
function WaitHttp([int]$port){for($i=0;$i -lt 90;$i++){try{Invoke-WebRequest "http://127.0.0.1:$port/api/v1/auth/csrf" -UseBasicParsing -TimeoutSec 2 | Out-Null;return}catch{};Start-Sleep -Seconds 1};throw "Backend not ready: $port"}
try{
 if(Get-NetTCPConnection -LocalPort 13316,13317,13319,16319,15179,18089 -State Listen -ErrorAction SilentlyContinue){throw 'Dedicated full-regression ports occupied'}
 $mysql=(Native docker @('run','-d','--rm','--pull=never','--name',$name,'-p','127.0.0.1:13316:3306','-p','127.0.0.1:13317:3306','-p','127.0.0.1:13319:3306','-e','MYSQL_ALLOW_EMPTY_PASSWORD=yes','-e','TZ=Asia/Shanghai','mysql:8.0.36','--default-time-zone=+08:00') | Out-String).Trim()
 $redis=(Native docker @('run','-d','--rm','--pull=never','--name',($name+'-redis'),'-p','127.0.0.1:16319:6379','redis:7-alpine') | Out-String).Trim()
 $ready=$false;for($i=0;$i -lt 90;$i++){try{Native docker @('exec',$mysql,'mysql','--protocol=TCP','-h','127.0.0.1','-uroot','-N','-e','SELECT 1') | Out-Null;$ready=$true;break}catch{};Start-Sleep -Seconds 1};if(-not $ready){throw 'MySQL not ready'}
 Native docker @('exec',$mysql,'mysql','-uroot','-e','CREATE DATABASE us0102_test CHARACTER SET utf8mb4; CREATE DATABASE exp3_test CHARACTER SET utf8mb4; CREATE DATABASE exp4_test CHARACTER SET utf8mb4;') | Out-Null
 $env:PLAYWRIGHT_MODULE='C:/Users/a3185/.vscode/extensions/vscjava.migrate-java-to-azure-1.24.0-win32-x64/node_modules/playwright';$env:EXP3_CHROMIUM_PATH='C:/Program Files/Google/Chrome/Application/chrome.exe';$env:US0102_CHROMIUM_PATH=$env:EXP3_CHROMIUM_PATH
 $env:CLASSROOM_API_PROXY='http://127.0.0.1:18089'
 $preview=Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js','preview','--host','127.0.0.1','--port','15179','--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence 'full-preview.log') -RedirectStandardError (Join-Path $evidence 'full-preview-error.log')
 $env:US0102_PREVIEW_URL='http://127.0.0.1:15179';$env:US0203_BROWSER='true';$env:US0203_EVIDENCE_DIR=Join-Path $evidence 'legacy-browser'
 $env:US0102_MYSQL_URL='jdbc:mysql://127.0.0.1:13316/us0102_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai';$env:EXP3_MYSQL_URL='jdbc:mysql://127.0.0.1:13317/exp3_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai';$env:EXP4_MYSQL_URL='jdbc:mysql://127.0.0.1:13319/exp4_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
 $env:SPRING_DATA_REDIS_HOST='127.0.0.1';$env:SPRING_DATA_REDIS_PORT='16319';$env:SPRING_DATA_REDIS_PASSWORD=''
 $jar=Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
 $app=(Native docker @('run','-d','--rm','--pull=never','--name',($name+'-backend'),'-p','127.0.0.1:18089:8080','--mount',('type=bind,source='+$jar+',target=/audit.jar,readonly'),'-e','SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:13319/exp4_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai','-e','SPRING_DATASOURCE_USERNAME=root','-e','SPRING_DATASOURCE_PASSWORD=','-e','SPRING_DATA_REDIS_HOST=host.docker.internal','-e','SPRING_DATA_REDIS_PORT=16319','-e','APP_SEED_DEMO=false','-e','TZ=Asia/Shanghai','-e','CLASSROOM_UPLOAD_DIR=/tmp/full-regression/faces','-e','CLASSROOM_FFPROBE=/usr/bin/ffprobe','--entrypoint','java','classroom-ai-demo-classroom-backend:latest','-Duser.timezone=Asia/Shanghai','-jar','/audit.jar') | Out-String).Trim()
 WaitHttp 18089
 $env:EXP4_BACKEND_URL='http://127.0.0.1:18089';$env:EXP4_FRONTEND_URL='http://127.0.0.1:15179';$env:EXP4_TEST_MYSQL_CONTAINER=$name;$env:EXP4_TEST_PASSWORD=[guid]::NewGuid().ToString('N');$env:EXP4_EVIDENCE_DIR=Join-Path $evidence 'sprint3-browser';$env:EXP4_VIDEO_FIXTURE=Join-Path $projectRoot 'docs/20261012-exp4-sprint3-verification-v1/synthetic-video.mp4'
 Native node @((Join-Path $evidence 'exp4-browser.cjs')) (Join-Path $evidence 'sprint3-browser.log')
 $env:UI_URL='http://127.0.0.1:15179';$env:RESOURCE_VERSIONS_EVIDENCE=Join-Path $evidence 'resource-browser'
 Native node @((Join-Path $projectRoot 'scripts/tests/resource-versions-browser.cjs')) (Join-Path $evidence 'resource-browser.log')
 Native node @((Join-Path $evidence 'supplement.cjs')) (Join-Path $evidence 'supplement.log')
 @{backend='PASS';sprint3='PASS';resources='PASS'} | ConvertTo-Json | Set-Content (Join-Path $evidence 'full-results.json') -Encoding utf8
}finally{
 foreach($id in @($app,$mysql,$redis)){if($id){try{Native docker @('logs',$id) (Join-Path $evidence ($id.Substring(0,12)+'.log'))}catch{}}}
 if($preview -and -not $preview.HasExited){Stop-Process -Id $preview.Id -Force -ErrorAction SilentlyContinue}
 foreach($id in @($app,$mysql,$redis)){if($id){try{Native docker @('stop',$id) | Out-Null}catch{}}}
 & python (Join-Path $projectRoot 'scripts/tests/sanitize-test-evidence.py') $evidence
}
