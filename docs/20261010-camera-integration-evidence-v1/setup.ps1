$ErrorActionPreference='Stop'
$testRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$testName='classroom-exp3-browser-'+[Guid]::NewGuid().ToString('N').Substring(0,10)
$testNetwork=$testName+'-net'
$testMysqlPassword=$env:ROLE_TEST_MYSQL_PASSWORD
if(-not $testMysqlPassword){throw 'Set ROLE_TEST_MYSQL_PASSWORD in process memory before setup'}
if(Get-NetTCPConnection -LocalPort 18081,18082,15173 -State Listen -ErrorAction SilentlyContinue) { throw 'Isolated test ports occupied' }
& docker network create $testNetwork | Out-Null
if($LASTEXITCODE -ne 0){ throw 'Cannot create test network' }
$testMysql=& docker run -d --rm --pull=never --name $testName --network $testNetwork --env "MYSQL_ROOT_PASSWORD=$testMysqlPassword" --env MYSQL_DATABASE=classroom_ai mysql:8.0.36 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
if($LASTEXITCODE -ne 0){ throw 'Cannot create isolated MySQL' }
$testState=@{network=$testNetwork;mysql=$testName;redis=$testName+'-redis';backend=$testName+'-backend';expiredBackend=$testName+'-expired';frontendPid=$null}
$testState | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'environment.json')
$testReady=$false
for($testAttempt=0;$testAttempt -lt 60;$testAttempt++){
  & docker exec -e "MYSQL_PWD=$testMysqlPassword" $testMysql mysql --protocol=TCP -h 127.0.0.1 -uroot -N -e 'SELECT 1' *> $null
  if($LASTEXITCODE -eq 0){$testReady=$true;break}
  Start-Sleep -Seconds 1
}
if(-not $testReady){throw 'MySQL not ready'}
& docker cp (Join-Path $testRoot 'initialize.sql') "${testMysql}:/tmp/initialize.sql"
& docker exec -e "MYSQL_PWD=$testMysqlPassword" $testMysql mysql -uroot --default-character-set=utf8mb4 -e 'source /tmp/initialize.sql'
if($LASTEXITCODE -ne 0){throw 'Seed SQL failed'}
& docker cp (Join-Path $testRoot 'scripts/deploy/mysql/init/06_exp3_sprint2.sql') "${testMysql}:/tmp/migration.sql"
& docker exec -e "MYSQL_PWD=$testMysqlPassword" $testMysql mysql -uroot --default-character-set=utf8mb4 classroom_ai -e 'source /tmp/migration.sql'
if($LASTEXITCODE -ne 0){throw 'Migration failed'}
$testPasswordHash=& python -c "import os,bcrypt; print(bcrypt.hashpw(os.environ['ROLE_TEST_PASSWORD'].encode(),bcrypt.gensalt()).decode())"
if($LASTEXITCODE -ne 0){throw 'Cannot hash disposable demo password'}
& docker exec -e "MYSQL_PWD=$testMysqlPassword" $testMysql mysql -uroot classroom_ai -e "UPDATE t_user_account SET password='$testPasswordHash' WHERE username IN ('director','guojun','supervisor','liubo','jiangly','director_base','director_arch')"
if($LASTEXITCODE -ne 0){throw 'Disposable demo password normalization failed'}
& docker run -d --rm --pull=never --name $testState.redis --network $testNetwork redis:7.2-alpine | Out-Null
$testJar=Join-Path $testRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
foreach($testVariant in @(@{name=$testState.backend;port=18081;minutes=5},@{name=$testState.expiredBackend;port=18082;minutes=0})){
  & docker run -d --rm --pull=never --name $testVariant.name --network $testNetwork -p "127.0.0.1:$($testVariant.port):8080" --mount "type=bind,source=$testJar,target=/app/app.jar,readonly" --env "SPRING_DATASOURCE_URL=jdbc:mysql://${testName}:3306/classroom_ai?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" --env SPRING_DATASOURCE_USERNAME=root --env "SPRING_DATASOURCE_PASSWORD=$testMysqlPassword" --env "SPRING_DATA_REDIS_HOST=$($testState.redis)" --env APP_SEED_DEMO=false --env TZ=Asia/Shanghai --env "CLASSROOM_RESOURCE_PREVIEW_MINUTES=$($testVariant.minutes)" classroom-ai-demo-classroom-backend | Out-Null
  if($LASTEXITCODE -ne 0){throw 'Backend container failed'}
  # Avoid concurrent Hibernate DDL against a freshly seeded shared test database.
  $testReady=$false
  for($testAttempt=0;$testAttempt -lt 90;$testAttempt++){
    try{if((Invoke-WebRequest "http://127.0.0.1:$($testVariant.port)/api/v1/auth/csrf" -TimeoutSec 2).StatusCode -eq 200){$testReady=$true;break}}catch{}
    Start-Sleep -Seconds 1
  }
  if(-not $testReady){throw 'Backend not ready'}
}
foreach($testPort in @(18081,18082)){
  $testReady=$false
  for($testAttempt=0;$testAttempt -lt 90;$testAttempt++){
    try{if((Invoke-WebRequest "http://127.0.0.1:$testPort/api/v1/auth/csrf" -TimeoutSec 2).StatusCode -eq 200){$testReady=$true;break}}catch{}
    Start-Sleep -Seconds 1
  }
  if(-not $testReady){throw 'Backend not ready'}
}
$env:CLASSROOM_API_PROXY='http://127.0.0.1:18081'
$env:CLASSROOM_MONITOR_PORT='18088'
$env:CLASSROOM_PYTHON=Join-Path $testRoot '.venv1/Scripts/python.exe'
$testFrontend=Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js','preview','--config','vite.config.ts','--host','127.0.0.1','--port','15173','--strictPort') -WorkingDirectory (Join-Path $testRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $PSScriptRoot 'frontend.stdout.log') -RedirectStandardError (Join-Path $PSScriptRoot 'frontend.stderr.log')
$testState.frontendPid=$testFrontend.Id
$testState | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'environment.json')
'Isolated real MySQL/Redis/current JAR/LibreOffice/Vue preview ready'
