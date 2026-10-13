param([switch]$SkipBuild,[switch]$KeepServices)
$ErrorActionPreference='Continue'
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$evidence=Join-Path $projectRoot 'docs/20261013-exp4-fix-evidence-v1/business'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$mysqlId=$null;$redisId=$null;$backendProcess=$null;$frontendProcess=$null
try {
 if(Get-NetTCPConnection -LocalPort 13319,16319,18089,15179 -State Listen -ErrorAction SilentlyContinue){throw 'Dedicated UI test ports must be free'}
 $name='ui-redesign-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
 $mysqlId=& docker run -d --rm --pull=never --name $name -p 127.0.0.1:13319:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=exp4_test mysql:8.0.36
 if($LASTEXITCODE -ne 0){$mysqlId=$null;throw 'Isolated MySQL failed'}
 $redisId=& docker run -d --rm --pull=never --name ($name+'-redis') -p 127.0.0.1:16319:6379 redis:7-alpine
 if($LASTEXITCODE -ne 0){$redisId=$null;throw 'Isolated Redis failed'}
 for($i=0;$i -lt 60;$i++){& docker exec $mysqlId mysqladmin ping -uroot --silent *> $null;if($LASTEXITCODE -eq 0){break};Start-Sleep -Seconds 1}
 $env:EXP4_MYSQL_URL='jdbc:mysql://127.0.0.1:13319/exp4_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
 $env:SPRING_DATASOURCE_URL=$env:EXP4_MYSQL_URL;$env:SPRING_DATASOURCE_USERNAME='root';$env:SPRING_DATASOURCE_PASSWORD='';$env:SPRING_DATA_REDIS_HOST='127.0.0.1';$env:SPRING_DATA_REDIS_PORT='16319';$env:APP_SEED_DEMO='false'
 $env:CLASSROOM_UPLOAD_DIR=Join-Path $evidence 'uploads/faces';$env:CLASSROOM_FFPROBE=Join-Path $projectRoot 'runtime/tools/ffmpeg/ffprobe.exe'
 if(-not $SkipBuild){
  & (Join-Path $projectRoot 'backend/.tools/apache-maven-3.9.6/bin/mvn.cmd') -f (Join-Path $projectRoot 'backend/pom.xml') -o package '-Dtest=Sprint3MysqlTest,Sprint3BoundaryTest' *> (Join-Path $evidence 'backend-tests.log')
  if($LASTEXITCODE -ne 0){throw 'Backend build/tests failed'}
  & npm.cmd --prefix (Join-Path $projectRoot 'frontend') run build *> (Join-Path $evidence 'frontend-build.log');if($LASTEXITCODE -ne 0){throw 'Frontend build failed'}
 }
 $jar=Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
 $backendProcess=Start-Process java -ArgumentList @('-jar',('"'+$jar+'"'),'--server.port=18089','--spring.datasource.password=') -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence 'backend.log') -RedirectStandardError (Join-Path $evidence 'backend-error.log')
 for($i=0;$i -lt 60;$i++){try{Invoke-WebRequest 'http://127.0.0.1:18089/api/v1/auth/csrf' -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop | Out-Null;break}catch{Start-Sleep -Seconds 1}}
 $env:CLASSROOM_API_PROXY='http://127.0.0.1:18089'
 $frontendProcess=Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js','preview','--host','127.0.0.1','--port','15179','--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence 'frontend.log') -RedirectStandardError (Join-Path $evidence 'frontend-error.log')
 $env:EXP4_BACKEND_URL='http://127.0.0.1:18089';$env:EXP4_FRONTEND_URL='http://127.0.0.1:15179';$env:EXP4_TEST_MYSQL_CONTAINER=$name;$env:EXP4_TEST_PASSWORD=[Guid]::NewGuid().ToString('N')
 $env:EXP4_EVIDENCE=$evidence;$env:PLAYWRIGHT_MODULE=Join-Path $env:USERPROFILE '.vscode/extensions/vscjava.migrate-java-to-azure-1.24.0-win32-x64/node_modules/playwright';$env:EXP3_CHROMIUM_PATH=Join-Path $env:LOCALAPPDATA 'ms-playwright/chromium-1228/chrome-win64/chrome.exe'
 & node (Join-Path $projectRoot 'scripts/tests/ui-business-browser.cjs') *> (Join-Path $evidence 'browser-tests.log');if($LASTEXITCODE -ne 0){throw 'Isolated UI business browser tests failed'}
} finally {
 if(-not $KeepServices){
  if($frontendProcess -and -not $frontendProcess.HasExited){Stop-Process -Id $frontendProcess.Id -Force}
  if($backendProcess -and -not $backendProcess.HasExited){Stop-Process -Id $backendProcess.Id -Force}
  if($mysqlId){& docker stop $mysqlId | Out-Null};if($redisId){& docker stop $redisId | Out-Null}
 }
}
