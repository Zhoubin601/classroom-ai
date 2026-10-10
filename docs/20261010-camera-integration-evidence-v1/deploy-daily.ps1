$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskResults=Get-Content (Join-Path $PSScriptRoot 'camera-run-results.json') | ConvertFrom-Json
if($taskResults | Where-Object {$_.exitCode -ne 0}){throw 'Final browser tests have not passed'}
$cameraResults=Get-Content (Join-Path $PSScriptRoot 'camera-micro/results.json') | ConvertFrom-Json
if(-not $cameraResults.results -or ($cameraResults.results | Where-Object {$_.status -ne 'PASS'})){throw 'Camera/micro proof incomplete'}
$taskEnv=& docker inspect classroom-backend --format '{{json .Config.Env}}' | ConvertFrom-Json
$taskDatabaseCredential=($taskEnv | Where-Object { $_.StartsWith('SPRING_DATASOURCE_PASSWORD=') }) -replace '^SPRING_DATASOURCE_PASSWORD=',''
if(-not $taskDatabaseCredential){throw 'Cannot read current database connection setting'}
$env:MYSQL_PWD=$taskDatabaseCredential
$taskSql="SELECT 't_course',COUNT(*) FROM t_course UNION ALL SELECT 't_course_offering',COUNT(*) FROM t_course_offering UNION ALL SELECT 'student',COUNT(*) FROM student UNION ALL SELECT 'face_feature',COUNT(*) FROM face_feature UNION ALL SELECT 't_attendance_session',COUNT(*) FROM t_attendance_session UNION ALL SELECT 't_micro_teaching_slice',COUNT(*) FROM t_micro_teaching_slice"
function DatabaseCounts { & docker exec -e MYSQL_PWD classroom-mysql mysql -uroot -N classroom_ai -e $taskSql; if($LASTEXITCODE -ne 0){throw 'Cannot verify database counts'} }
$taskBefore=@{at=[DateTimeOffset]::Now.ToString('o');backendImage=(& docker inspect classroom-backend --format '{{.Image}}');mysqlId=(& docker inspect classroom-mysql --format '{{.Id}}');redisId=(& docker inspect classroom-redis --format '{{.Id}}');counts=@(DatabaseCounts);jarSHA256=(Get-FileHash (Join-Path $taskRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar')).Hash}
$taskBefore | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'daily-before.json')
& (Join-Path $taskRoot 'scripts/compose.ps1') build classroom-backend *> (Join-Path $PSScriptRoot 'daily-build.log')
if($LASTEXITCODE -ne 0){throw 'Daily backend image build failed'}
& (Join-Path $taskRoot 'scripts/compose.ps1') up -d --no-deps classroom-backend *> (Join-Path $PSScriptRoot 'daily-restart.log')
if($LASTEXITCODE -ne 0){throw 'Daily backend restart failed'}
$taskReady=$false
for($taskAttempt=0;$taskAttempt -lt 90;$taskAttempt++){
  try{if((Invoke-WebRequest 'http://127.0.0.1:8080/api/v1/auth/csrf' -TimeoutSec 2).StatusCode -eq 200){$taskReady=$true;break}}catch{}
  Start-Sleep -Seconds 1
}
if(-not $taskReady){throw 'Daily backend did not become healthy'}
$taskPorts=Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
foreach($taskPid in ($taskPorts.OwningProcess | Select-Object -Unique)){
  $taskProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$taskPid"
  if($taskProcess.Name -ne 'node.exe' -or $taskProcess.CommandLine -notmatch 'vite' -or $taskProcess.CommandLine -notmatch '5173'){throw 'Port 5173 belongs to another process'}
  Stop-Process -Id $taskPid
}
$env:CLASSROOM_API_PROXY='http://127.0.0.1:8080'
$env:CLASSROOM_MONITOR_PORT='8088'
$env:CLASSROOM_PYTHON=Join-Path $taskRoot '.venv1/Scripts/python.exe'
Remove-Item Env:ROLE_TEST_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:ROLE_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue
$taskFrontend=Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js','--config','vite.config.ts','--host','127.0.0.1','--port','5173','--strictPort') -WorkingDirectory (Join-Path $taskRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $PSScriptRoot 'daily-frontend.stdout.log') -RedirectStandardError (Join-Path $PSScriptRoot 'daily-frontend.stderr.log')
$taskReady=$false
for($taskAttempt=0;$taskAttempt -lt 30;$taskAttempt++){
  try{if((Invoke-WebRequest 'http://127.0.0.1:5173' -TimeoutSec 2).StatusCode -eq 200){$taskReady=$true;break}}catch{}
  Start-Sleep -Seconds 1
}
if(-not $taskReady){throw 'Daily frontend did not become healthy'}
$taskAfter=@{at=[DateTimeOffset]::Now.ToString('o');backendImage=(& docker inspect classroom-backend --format '{{.Image}}');mysqlId=(& docker inspect classroom-mysql --format '{{.Id}}');redisId=(& docker inspect classroom-redis --format '{{.Id}}');counts=@(DatabaseCounts);containerJarSHA256=(& docker exec classroom-backend sha256sum /app/app.jar);frontendPid=$taskFrontend.Id;frontendUrl='http://127.0.0.1:5173';backendUrl='http://127.0.0.1:8080'}
if($taskBefore.mysqlId -ne $taskAfter.mysqlId -or $taskBefore.redisId -ne $taskAfter.redisId){throw 'Business data services changed unexpectedly'}
if(($taskBefore.counts -join '|') -ne ($taskAfter.counts -join '|')){throw 'Business table counts changed unexpectedly'}
if(-not $taskAfter.containerJarSHA256.ToLower().StartsWith($taskBefore.jarSHA256.ToLower())){throw 'Deployed JAR differs from tested JAR'}
$taskAfter | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'daily-after.json')
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
'Updated daily frontend/backend; original MySQL/Redis retained; business table counts unchanged; tested JAR fingerprint matched'
