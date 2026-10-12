param([switch]$SkipTests)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$evidence=Join-Path $projectRoot 'docs/20261011-sprint3-evidence-v1'
$mysqlId=$null;$redisId=$null;$backendProcess=$null;$frontendProcess=$null
$envNames=@('EXP4_MYSQL_URL','SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','SPRING_DATA_REDIS_PORT','SPRING_DATA_REDIS_HOST','APP_SEED_DEMO','CLASSROOM_UPLOAD_DIR','CLASSROOM_FFPROBE','CLASSROOM_API_PROXY','EXP4_BACKEND_URL','EXP4_FRONTEND_URL','EXP4_TEST_MYSQL_CONTAINER','EXP4_TEST_PASSWORD','PLAYWRIGHT_MODULE','EXP3_CHROMIUM_PATH')
$saved=@{};foreach($name in $envNames){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try{
 if(Get-NetTCPConnection -LocalPort 13319,16319,18089,15179 -State Listen -ErrorAction SilentlyContinue){throw 'Dedicated test ports must be free'}
 New-Item -ItemType Directory -Force -Path $evidence | Out-Null
 $name='exp4-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
 $mysqlId=& docker run -d --rm --pull=never --name $name -p 127.0.0.1:13319:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=exp4_test mysql:8.0.36
 if($LASTEXITCODE -ne 0){$mysqlId=$null;throw 'Isolated MySQL failed'}
 $redisId=& docker run -d --rm --pull=never --name ($name+'-redis') -p 127.0.0.1:16319:6379 redis:7-alpine
 if($LASTEXITCODE -ne 0){$redisId=$null;throw 'Isolated Redis failed'}
 for($i=0;$i -lt 60;$i++){& docker exec $mysqlId mysqladmin ping -uroot --silent *>$null;if($LASTEXITCODE -eq 0){break};Start-Sleep -Seconds 1}
 $env:EXP4_MYSQL_URL='jdbc:mysql://127.0.0.1:13319/exp4_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
 $maven=Join-Path $projectRoot 'backend/.tools/apache-maven-3.9.6/bin/mvn.cmd'
 $args=@('-f',(Join-Path $projectRoot 'backend/pom.xml'),'-o','package');if($SkipTests){$args+= '-DskipTests'}
 $buildLog=if($SkipTests){'backend-browser-rebuild.log'}else{'backend-final.log'}
 & $maven @args *> (Join-Path $evidence $buildLog);if($LASTEXITCODE -ne 0){throw 'Backend tests/build failed; see backend-final.log'}
 & npm.cmd --prefix (Join-Path $projectRoot 'frontend') run build *> (Join-Path $evidence 'frontend-final-build.log');if($LASTEXITCODE -ne 0){throw 'Frontend build failed'}
 $env:SPRING_DATASOURCE_URL=$env:EXP4_MYSQL_URL;$env:SPRING_DATASOURCE_USERNAME='root';$env:SPRING_DATASOURCE_PASSWORD='';$env:SPRING_DATA_REDIS_HOST='127.0.0.1';$env:SPRING_DATA_REDIS_PORT='16319';$env:APP_SEED_DEMO='false'
 $env:CLASSROOM_UPLOAD_DIR=Join-Path $projectRoot ('runtime/sprint3-browser/'+$name+'/faces');$env:CLASSROOM_FFPROBE=Join-Path $projectRoot 'runtime/tools/ffmpeg/ffprobe.exe'
 if(-not(Test-Path $env:CLASSROOM_FFPROBE)){throw 'Run setup-video-tools.ps1 first'}
 $jar=Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
 $backendProcess=Start-Process java -ArgumentList @('-jar',('"'+$jar+'"'),'--server.port=18089') -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence 'browser-backend.log') -RedirectStandardError (Join-Path $evidence 'browser-backend-error.log')
 for($i=0;$i -lt 60;$i++){try{Invoke-WebRequest 'http://127.0.0.1:18089/api/v1/auth/csrf' -UseBasicParsing -TimeoutSec 2 | Out-Null;break}catch{Start-Sleep -Seconds 1}}
 $env:CLASSROOM_API_PROXY='http://127.0.0.1:18089'
 $frontendProcess=Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js','preview','--host','127.0.0.1','--port','15179','--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence 'browser-frontend.log') -RedirectStandardError (Join-Path $evidence 'browser-frontend-error.log')
 for($i=0;$i -lt 30;$i++){try{Invoke-WebRequest 'http://127.0.0.1:15179' -UseBasicParsing -TimeoutSec 2 | Out-Null;break}catch{Start-Sleep -Seconds 1}}
 $env:EXP4_BACKEND_URL='http://127.0.0.1:18089';$env:EXP4_FRONTEND_URL='http://127.0.0.1:15179';$env:EXP4_TEST_MYSQL_CONTAINER=$name;$env:EXP4_TEST_PASSWORD=[Guid]::NewGuid().ToString('N')
 if(-not $env:PLAYWRIGHT_MODULE){$candidate=Get-ChildItem (Join-Path $env:USERPROFILE '.vscode/extensions') -Directory -Filter 'vscjava.migrate-java-to-azure-*' | Sort-Object Name -Descending | Select-Object -First 1;$env:PLAYWRIGHT_MODULE=Join-Path $candidate.FullName 'node_modules/playwright'}
 if(-not $env:EXP3_CHROMIUM_PATH){$env:EXP3_CHROMIUM_PATH=Join-Path $env:LOCALAPPDATA 'ms-playwright/chromium-1228/chrome-win64/chrome.exe'}
 Push-Location $projectRoot;try{& node scripts/tests/exp4-browser.cjs *> (Join-Path $evidence 'browser-tests.log');if($LASTEXITCODE -ne 0){throw 'Real browser acceptance failed; see browser-tests.log'}}finally{Pop-Location}
 Stop-Process -Id $backendProcess.Id -Force;$backendProcess=$null
 & python (Join-Path $projectRoot 'scripts/tests/exp4-legacy-schema.py')
 & docker exec $mysqlId mysql -uroot -e 'CREATE DATABASE exp4_upgrade CHARACTER SET utf8mb4;'
 & docker cp (Join-Path $projectRoot 'runtime/sprint3-test/legacy-schema.sql') "${mysqlId}:/tmp/legacy.sql"
 & docker exec $mysqlId mysql -uroot exp4_upgrade -e 'source /tmp/legacy.sql'
 & docker cp (Join-Path $projectRoot 'scripts/deploy/mysql/init/06_exp3_sprint2.sql') "${mysqlId}:/tmp/sprint2.sql"
 & docker exec $mysqlId mysql -uroot exp4_upgrade -e 'source /tmp/sprint2.sql'
 if($LASTEXITCODE -ne 0){throw 'Synthetic legacy schema failed'}
 $env:SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13319/exp4_upgrade?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
 $proof=@()
 foreach($pass in 1..2){
  $backendProcess=Start-Process java -ArgumentList @('-jar',('"'+$jar+'"'),'--server.port=18089') -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence "upgrade-$pass.log") -RedirectStandardError (Join-Path $evidence "upgrade-$pass-error.log")
  $ready=$false
  for($i=0;$i -lt 60;$i++){try{Invoke-WebRequest 'http://127.0.0.1:18089/api/v1/auth/csrf' -UseBasicParsing -TimeoutSec 2 | Out-Null;$rows=& docker exec $mysqlId mysql -uroot exp4_upgrade -N -e 'SELECT COUNT(DISTINCT series_id),COUNT(*) FROM t_course_resource; SELECT COUNT(*) FROM s3_advice; SELECT COUNT(*) FROM t_supervision_evaluation WHERE template_version_id IS NULL AND weight_snapshot IS NULL;';if(($rows -join '|') -eq "2`t2|1|1"){$ready=$true;break}}catch{};Start-Sleep -Seconds 1}
  if(-not $ready){throw 'Legacy upgrade failed or duplicated historical records'}
  $proof+=@{pass=$pass;independentSeries=2;resources=2;wholeTextAdvice=1;unknownWeightEvaluations=1}
  Stop-Process -Id $backendProcess.Id -Force;$backendProcess=$null
 }
 $proof | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'upgrade-results.json') -Encoding UTF8
 Write-Output 'Sprint 3 isolated acceptance passed'
}finally{
 if($frontendProcess -and -not $frontendProcess.HasExited){Stop-Process -Id $frontendProcess.Id -Force}
 if($backendProcess -and -not $backendProcess.HasExited){Stop-Process -Id $backendProcess.Id -Force}
 if($mysqlId){& docker stop $mysqlId | Out-Null};if($redisId){& docker stop $redisId | Out-Null}
 foreach($name in $envNames){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
