param(
    [string]$EvidenceDir,
    [ValidateSet('Auto','Native','Docker')][string]$BackendMode='Auto',
    [string]$BackendImage='classroom-ai-demo-classroom-backend:latest'
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $EvidenceDir){$EvidenceDir=Join-Path $projectRoot ('docs/runtime/sprint2-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))}
$evidence=[IO.Path]::GetFullPath($EvidenceDir)
if(Test-Path $evidence){if(Get-ChildItem -LiteralPath $evidence -Force){throw 'Evidence directory must be new or empty'}}
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$name='classroom-exp3-browser-'+[guid]::NewGuid().ToString('N').Substring(0,10)
$runDir=Join-Path $projectRoot ('runtime/browser-tests/'+$name)
New-Item -ItemType Directory -Force -Path (Join-Path $runDir 'uploads') | Out-Null
$containers=New-Object System.Collections.Generic.List[string]
$processes=New-Object System.Collections.Generic.List[object]
$results=[ordered]@{}
$envNames=@('EXP3_FRONTEND_URL','EXP3_BACKEND_URL','EXP3_EXPIRED_BACKEND_URL','EXP3_CONFIG_BACKEND_URL','EXP3_TEST_MYSQL_CONTAINER','EXP3_EVIDENCE_DIR','FIX_EXP3_EVIDENCE_DIR','EXP3_FIX_EVIDENCE_DIR','EXP3_RACE_EVIDENCE_DIR','EXP3_CONFIG_EVIDENCE_DIR','LOGIN_PROBE_EVIDENCE','LOGIN_START_EVIDENCE','UI_URL','CLASSROOM_API_PROXY','PLAYWRIGHT_MODULE','EXP3_CHROMIUM_PATH','SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','SPRING_DATA_REDIS_HOST','SPRING_DATA_REDIS_PORT','SPRING_DATA_REDIS_PASSWORD','APP_SEED_DEMO','CLASSROOM_UPLOAD_DIR','CLASSROOM_FFPROBE')
$saved=@{};foreach($key in $envNames){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
function InvokeNative([string]$exe,[string[]]$commandArgs,[string]$log=''){
    # Windows PowerShell 5 treats native stderr as errors when redirected.
    $priorPreference=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{if($log){$nativeOutput=& $exe @commandArgs 2>&1;$exitCode=$LASTEXITCODE;$nativeOutput | Out-File -LiteralPath $log -Encoding utf8}else{$nativeOutput=& $exe @commandArgs 2>&1;$exitCode=$LASTEXITCODE}}finally{$ErrorActionPreference=$priorPreference}
    if($exitCode -ne 0){throw ('Command failed ('+$exitCode+'): '+$exe+'; see '+$log)}
    if(-not $log){return $nativeOutput}
}
function WaitHttp([int]$port){
    for($i=0;$i -lt 90;$i++){try{Invoke-WebRequest ('http://127.0.0.1:'+$port+'/api/v1/auth/csrf') -UseBasicParsing -TimeoutSec 2 | Out-Null;return}catch{};Start-Sleep -Seconds 1}
    throw ('Backend not ready: '+$port)
}
function StartBackend([int]$port,[string]$label,[string[]]$flags=@()){
    $jar=Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
    if($script:dockerBackend){
        $appArgs=@('run','-d','--rm','--pull=never','--name',($name+'-'+$label),'-p',('127.0.0.1:'+ $port+':8080'),'--mount',('type=bind,source='+$jar+',target=/audit.jar,readonly'),'--mount',('type=bind,source='+(Join-Path $runDir 'uploads')+',target=/audit/uploads'),'-e','SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:13318/classroom_ai?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai','-e','SPRING_DATASOURCE_USERNAME=root','-e','SPRING_DATASOURCE_PASSWORD=root','-e','SPRING_DATA_REDIS_HOST=host.docker.internal','-e','SPRING_DATA_REDIS_PORT=16318','-e','APP_SEED_DEMO=false','-e','TZ=Asia/Shanghai','-e','CLASSROOM_UPLOAD_DIR=/audit/uploads/faces','-e','CLASSROOM_FFPROBE=/usr/bin/ffprobe','--entrypoint','java',$BackendImage,'-Duser.timezone=Asia/Shanghai','-jar','/audit.jar')+$flags
        $id=(InvokeNative 'docker' $appArgs | Out-String).Trim();$containers.Add($id)
    }else{
        $javaArgs=@('-Duser.timezone=Asia/Shanghai','-jar',('"'+$jar+'"'),('--server.port='+$port))+$flags
        $process=Start-Process java -ArgumentList $javaArgs -WorkingDirectory $runDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence ($label+'.stdout.log')) -RedirectStandardError (Join-Path $evidence ($label+'.stderr.log'))
        $processes.Add($process)
    }
    WaitHttp $port
}
try{
    if(Get-NetTCPConnection -LocalPort 13318,16318,18081,18082,18083,15173 -State Listen -ErrorAction SilentlyContinue){throw 'Dedicated test ports occupied'}
    InvokeNative 'docker' @('info','--format','{{.ServerVersion}}') (Join-Path $evidence 'docker-version.txt')
    $soffice=Get-Command soffice -ErrorAction SilentlyContinue
    $script:dockerBackend=$BackendMode -eq 'Docker' -or ($BackendMode -eq 'Auto' -and -not $soffice -and -not $env:CLASSROOM_SOFFICE)
    if($script:dockerBackend){InvokeNative 'docker' @('image','inspect',$BackendImage,'--format','{{.Id}}') (Join-Path $evidence 'backend-runtime-image.txt')}
    if(-not(Test-Path (Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'))){throw 'Build the current backend JAR first'}
    if(-not(Test-Path (Join-Path $projectRoot 'frontend/dist/index.html'))){throw 'Build the current frontend first'}
    if(-not $env:PLAYWRIGHT_MODULE){$env:PLAYWRIGHT_MODULE=Get-ChildItem (Join-Path $env:USERPROFILE '.vscode/extensions') -Directory -Filter 'vscjava.migrate-java-to-azure-*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object {Join-Path $_.FullName 'node_modules/playwright'} | Where-Object {Test-Path $_} | Select-Object -First 1}
    if(-not $env:EXP3_CHROMIUM_PATH){foreach($candidate in @((Join-Path $env:LOCALAPPDATA 'ms-playwright/chromium-1228/chrome-win64/chrome.exe'),(Join-Path $env:ProgramFiles 'Google/Chrome/Application/chrome.exe'))){if(Test-Path $candidate){$env:EXP3_CHROMIUM_PATH=$candidate;break}}}
    $mysql=(InvokeNative 'docker' @('run','-d','--rm','--pull=never','--name',$name,'-p','127.0.0.1:13318:3306','-e','MYSQL_ROOT_PASSWORD=root','-e','TZ=Asia/Shanghai','mysql:8.0.36','--default-time-zone=+08:00') | Out-String).Trim();$containers.Add($mysql)
    $ready=$false
    for($i=0;$i -lt 90;$i++){
        try{InvokeNative 'docker' @('exec','-e','MYSQL_PWD=root',$mysql,'mysql','--protocol=TCP','-h','127.0.0.1','-uroot','-N','-e','SELECT 1') | Out-Null;$ready=$true;break}catch{}
        Start-Sleep -Seconds 1
    }
    if(-not $ready){throw 'Isolated MySQL not ready'}
    foreach($item in @(@('initialize.sql','/tmp/initialize.sql'),@('scripts/deploy/mysql/init/06_exp3_sprint2.sql','/tmp/sprint2.sql'))){InvokeNative 'docker' @('cp',(Join-Path $projectRoot $item[0]),($mysql+':'+$item[1])) | Out-Null}
    InvokeNative 'docker' @('exec','-e','MYSQL_PWD=root',$mysql,'mysql','-uroot','-e','source /tmp/initialize.sql') (Join-Path $evidence 'initialize.log')
    InvokeNative 'docker' @('exec','-e','MYSQL_PWD=root',$mysql,'mysql','-uroot','classroom_ai','-e','source /tmp/sprint2.sql') (Join-Path $evidence 'migration.log')
    InvokeNative 'docker' @('exec','-e','MYSQL_PWD=root',$mysql,'mysql','-uroot','classroom_ai','-e',"UPDATE t_user_account SET password='123456' WHERE username IN ('director','supervisor','guojun','liubo','jiangly');") | Out-Null
    $redis=(InvokeNative 'docker' @('run','-d','--rm','--pull=never','--name',($name+'-redis'),'-p','127.0.0.1:16318:6379','redis:7.2-alpine') | Out-String).Trim();$containers.Add($redis)
    $env:SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13318/classroom_ai?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai';$env:SPRING_DATASOURCE_USERNAME='root';$env:SPRING_DATASOURCE_PASSWORD='root';$env:SPRING_DATA_REDIS_HOST='127.0.0.1';$env:SPRING_DATA_REDIS_PORT='16318';$env:SPRING_DATA_REDIS_PASSWORD='';$env:APP_SEED_DEMO='false';$env:CLASSROOM_UPLOAD_DIR=Join-Path $runDir 'uploads/faces'
    $probe=Join-Path $projectRoot 'runtime/tools/ffmpeg/ffprobe.exe';if(Test-Path $probe){$env:CLASSROOM_FFPROBE=$probe}
    StartBackend 18081 'backend'
    # Wait for actual migration state, not merely a CSRF HTTP listener.
    $ready=$false
    for($i=0;$i -lt 60;$i++){try{$count=InvokeNative 'docker' @('exec','-e','MYSQL_PWD=root',$mysql,'mysql','-uroot','classroom_ai','-N','-e','SELECT COUNT(*) FROM s3_template_version');if([int]($count | Out-String).Trim() -gt 0){$ready=$true;break}}catch{};Start-Sleep -Seconds 1}
    if(-not $ready){throw 'Template migration not ready'}
    StartBackend 18082 'expired' @('--classroom.resource.preview-minutes=0')
    StartBackend 18083 'configurable' @('--classroom.resource.allowed-extensions=pdf','--classroom.resource.max-file-bytes=1024','--classroom.supervision.feedback-delay-hours=0')
    $env:CLASSROOM_API_PROXY='http://127.0.0.1:18081'
    $frontend=Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js','preview','--host','127.0.0.1','--port','15173','--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence 'frontend.stdout.log') -RedirectStandardError (Join-Path $evidence 'frontend.stderr.log');$processes.Add($frontend)
    $ready=$false;for($i=0;$i -lt 30;$i++){try{Invoke-WebRequest 'http://127.0.0.1:15173' -UseBasicParsing -TimeoutSec 2 | Out-Null;$ready=$true;break}catch{};Start-Sleep -Seconds 1};if(-not $ready){throw 'Frontend not ready'}
    $env:EXP3_FRONTEND_URL='http://127.0.0.1:15173';$env:EXP3_BACKEND_URL='http://127.0.0.1:18081';$env:EXP3_EXPIRED_BACKEND_URL='http://127.0.0.1:18082';$env:EXP3_CONFIG_BACKEND_URL='http://127.0.0.1:18083';$env:EXP3_TEST_MYSQL_CONTAINER=$mysql;$env:UI_URL=$env:EXP3_FRONTEND_URL
    foreach($pair in @(@('EXP3_EVIDENCE_DIR','core'),@('FIX_EXP3_EVIDENCE_DIR','office'),@('EXP3_FIX_EVIDENCE_DIR','history'),@('EXP3_RACE_EVIDENCE_DIR','history-race'),@('EXP3_CONFIG_EVIDENCE_DIR','configurable'),@('LOGIN_PROBE_EVIDENCE','login-probe'),@('LOGIN_START_EVIDENCE','login-start'))){[Environment]::SetEnvironmentVariable($pair[0],(Join-Path $evidence $pair[1]),'Process')}
    foreach($script in @('login-probe-browser.cjs','login-start-browser.cjs','exp3-real-browser.cjs','fix-exp3-acceptance.cjs','exp3-history-scores.cjs','exp3-startup-history-browser.cjs','exp3-configurable-browser.cjs')){
        InvokeNative 'node' @((Join-Path $projectRoot ('scripts/tests/'+$script))) (Join-Path $evidence ($script+'.log'))
        $results[$script]='PASS';$results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'results.json') -Encoding utf8
        Write-Output ('PASS '+$script)
    }
}finally{
    # Cleanup must continue even when log collection or Docker is interrupted.
    foreach($id in $containers){try{InvokeNative 'docker' @('logs',$id) (Join-Path $evidence ($id.Substring(0,12)+'.log'))}catch{}}
    foreach($process in $processes){if(-not $process.HasExited){Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue}}
    foreach($id in $containers){try{InvokeNative 'docker' @('stop',$id) | Out-Null}catch{}}
    $results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'results.json') -Encoding utf8
    foreach($key in $envNames){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}
    & python (Join-Path $projectRoot 'scripts/tests/sanitize-test-evidence.py') $evidence
}
