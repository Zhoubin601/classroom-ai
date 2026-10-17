$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$evidence=$PSScriptRoot
$name='exp4-20261017-fixed-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$mysql=$null;$redis=$null;$preview=$null;$app=$null
$results=[ordered]@{startedAt=(Get-Date).ToString('o');head=(& git -C $projectRoot rev-parse HEAD);backend='NOT_RUN';sprint1Browser='NOT_RUN';sprint3='NOT_RUN';resources='NOT_RUN';supplement='NOT_RUN';failures=@()}
$results['sourceState']='Current uncommitted US07 upload policy and BOPPPS copy fixes; see runtime-artifacts.json.'
function SaveResults { $results | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $evidence 'full-results.json') -Encoding utf8 }
function Stage([string]$name,[scriptblock]$action){
 Write-Output ('START '+$name+' '+(Get-Date).ToString('o'))
 try{& $action;$results[$name]='PASS'}catch{$results[$name]='FAIL';$results.failures+=($_.Exception.Message);Write-Output ('FAIL '+$name+': '+$_.Exception.Message)}
 SaveResults
 Write-Output ('END '+$name+' '+$results[$name]+' '+(Get-Date).ToString('o'))
}
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
 @{mysqlName=$name;mysqlId=$mysql;redisName=($name+'-redis');backendPort=18089;frontendPort=15179} | ConvertTo-Json | Set-Content (Join-Path $evidence 'isolated-services.json') -Encoding utf8
 $ready=$false;for($i=0;$i -lt 90;$i++){try{Native docker @('exec',$mysql,'mysql','--protocol=TCP','-h','127.0.0.1','-uroot','-N','-e','SELECT 1') | Out-Null;$ready=$true;break}catch{};Start-Sleep -Seconds 1};if(-not $ready){throw 'MySQL not ready'}
 Native docker @('exec',$mysql,'mysql','-uroot','-e','CREATE DATABASE us0102_test CHARACTER SET utf8mb4; CREATE DATABASE exp3_test CHARACTER SET utf8mb4; CREATE DATABASE exp4_test CHARACTER SET utf8mb4;') | Out-Null
 $env:PLAYWRIGHT_MODULE='C:/Users/a3185/.vscode/extensions/vscjava.migrate-java-to-azure-1.24.0-win32-x64/node_modules/playwright';$env:EXP3_CHROMIUM_PATH='C:/Program Files/Google/Chrome/Application/chrome.exe';$env:US0102_CHROMIUM_PATH=$env:EXP3_CHROMIUM_PATH
 Native docker @('run','--rm','--pull=never','--mount',('type=bind,source='+(Join-Path $projectRoot 'docs/20261012-exp4-sprint3-verification-v1/synthetic-video.mp4')+',target=/fixture.mp4,readonly'),'--entrypoint','/usr/bin/ffprobe','classroom-ai-demo-classroom-backend:latest','-v','error','-show_entries','format=duration','-show_entries','stream=codec_name,width,height','-of','json','/fixture.mp4') (Join-Path $evidence 'video-fixture-ffprobe.json')
 $env:CLASSROOM_API_PROXY='http://127.0.0.1:18089'
 Write-Output 'Waiting for the parent to confirm the final frontend build is ready.'
 for($readyWait=0;$readyWait -lt 120;$readyWait++){if(Test-Path (Join-Path $evidence 'frontend-ready')){break};Start-Sleep -Seconds 1}
 if(-not (Test-Path (Join-Path $evidence 'frontend-ready'))){throw 'Final frontend build confirmation not received'}
 $preview=Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js','preview','--host','127.0.0.1','--port','15179','--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence 'full-preview.log') -RedirectStandardError (Join-Path $evidence 'full-preview-error.log')
 $env:US0102_PREVIEW_URL='http://127.0.0.1:15179';$env:US0203_BROWSER='true';$env:US0203_EVIDENCE_DIR=Join-Path $evidence 'legacy-browser'
 $env:US0102_MYSQL_URL='jdbc:mysql://127.0.0.1:13316/us0102_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai';$env:EXP3_MYSQL_URL='jdbc:mysql://127.0.0.1:13317/exp3_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai';$env:EXP4_MYSQL_URL='jdbc:mysql://127.0.0.1:13319/exp4_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
 $env:SPRING_DATA_REDIS_HOST='127.0.0.1';$env:SPRING_DATA_REDIS_PORT='16319';$env:SPRING_DATA_REDIS_PASSWORD=''
 Stage 'backend' { Push-Location (Join-Path $projectRoot 'backend');try{Native '.tools/apache-maven-3.9.6/bin/mvn.cmd' @('-o','test') (Join-Path $evidence 'backend-full.log')}finally{Pop-Location} }
 New-Item -ItemType Directory -Force (Join-Path $evidence 'backend-full-xml') | Out-Null;Copy-Item (Join-Path $projectRoot 'backend/target/surefire-reports/TEST-*.xml') (Join-Path $evidence 'backend-full-xml')
 $sprint1Xml=Join-Path $evidence 'backend-full-xml/TEST-com.classroom.ai.modules.course.CourseBrowserMysqlTest.xml'
 if(Test-Path $sprint1Xml){[xml]$browserXml=Get-Content -Raw -LiteralPath $sprint1Xml;$results.sprint1Browser=if([int]$browserXml.testsuite.tests -gt 0 -and [int]$browserXml.testsuite.skipped -eq 0 -and [int]$browserXml.testsuite.failures -eq 0 -and [int]$browserXml.testsuite.errors -eq 0){'PASS'}else{'FAIL_OR_SKIPPED'};SaveResults}
 $jar=Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
 $app=(Native docker @('run','-d','--rm','--pull=never','--name',($name+'-backend'),'-p','127.0.0.1:18089:8080','--mount',('type=bind,source='+$jar+',target=/audit.jar,readonly'),'-e','SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:13319/exp4_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai','-e','SPRING_DATASOURCE_USERNAME=root','-e','SPRING_DATASOURCE_PASSWORD=','-e','SPRING_DATA_REDIS_HOST=host.docker.internal','-e','SPRING_DATA_REDIS_PORT=16319','-e','APP_SEED_DEMO=false','-e','TZ=Asia/Shanghai','-e','CLASSROOM_UPLOAD_DIR=/tmp/full-regression/faces','-e','CLASSROOM_FFPROBE=/usr/bin/ffprobe','--entrypoint','java','classroom-ai-demo-classroom-backend:latest','-Duser.timezone=Asia/Shanghai','-jar','/audit.jar') | Out-String).Trim()
 WaitHttp 18089
 $env:EXP4_BACKEND_URL='http://127.0.0.1:18089';$env:EXP4_FRONTEND_URL='http://127.0.0.1:15179';$env:EXP4_TEST_MYSQL_CONTAINER=$name;$env:EXP4_TEST_PASSWORD=[guid]::NewGuid().ToString('N');$env:EXP4_EVIDENCE_DIR=Join-Path $evidence 'sprint3-browser';$env:EXP4_VIDEO_FIXTURE=Join-Path $projectRoot 'docs/20261012-exp4-sprint3-verification-v1/synthetic-video.mp4'
 Stage 'sprint3' { Native node @((Join-Path $evidence 'exp4-browser.cjs')) (Join-Path $evidence 'sprint3-browser.log') }
 $env:UI_URL='http://127.0.0.1:15179';$env:RESOURCE_VERSIONS_EVIDENCE=Join-Path $evidence 'resource-browser'
 Stage 'resources' { Native node @((Join-Path $projectRoot 'scripts/tests/resource-versions-browser.cjs')) (Join-Path $evidence 'resource-browser.log') }
 Stage 'supplement' { Native node @((Join-Path $evidence 'supplement.cjs')) (Join-Path $evidence 'supplement.log') }
}catch{
 $results.failures+=$_.Exception.Message;Write-Output ('RUNNER FAIL: '+$_.Exception.Message)
}finally{
 foreach($id in @($app,$mysql,$redis)){if($id){try{Native docker @('logs',$id) (Join-Path $evidence ($id.Substring(0,12)+'.log'))}catch{}}}
 if($preview -and -not $preview.HasExited){Stop-Process -Id $preview.Id -Force -ErrorAction SilentlyContinue}
 foreach($id in @($app,$mysql,$redis)){if($id){try{Native docker @('stop',$id) | Out-Null}catch{}}}
 & python (Join-Path $projectRoot 'scripts/tests/sanitize-test-evidence.py') $evidence
 & python (Join-Path $evidence 'sanitize-extra.py') $evidence
 $results.finishedAt=(Get-Date).ToString('o');SaveResults
}


