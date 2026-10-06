param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$containerId = $null
$redisContainerId = $null
$backendProcess = $null
$expiredBackendProcess = $null
$frontendProcess = $null
$previousFrontendUrl = $env:EXP3_FRONTEND_URL
$previousApiProxy = $env:CLASSROOM_API_PROXY
$previousDatasource = $env:SPRING_DATASOURCE_URL
$previousRedis = $env:SPRING_DATA_REDIS_HOST
$previousRedisPort = $env:SPRING_DATA_REDIS_PORT
$previousRedisPassword = $env:SPRING_DATA_REDIS_PASSWORD
$previousDatasourceUsername = $env:SPRING_DATASOURCE_USERNAME
$previousDatasourcePassword = $env:SPRING_DATASOURCE_PASSWORD
$previousSeed = $env:APP_SEED_DEMO
$previousBackend = $env:EXP3_BACKEND_URL
$previousExpiredBackend = $env:EXP3_EXPIRED_BACKEND_URL
$previousModule = $env:PLAYWRIGHT_MODULE
$previousChromium = $env:EXP3_CHROMIUM_PATH
$previousTestMysql = $env:EXP3_TEST_MYSQL_CONTAINER
try {
    if (Get-NetTCPConnection -LocalPort 13318,18081,18082,15173 -State Listen -ErrorAction SilentlyContinue) {
        throw 'Temporary ports 13318, 18081, 18082 and 15173 must be free'
    }
    $name = 'classroom-exp3-browser-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $containerId = & docker run --detach --rm --pull=never --name $name --publish 127.0.0.1:13318:3306 `
        --env MYSQL_ROOT_PASSWORD=root --env MYSQL_DATABASE=classroom_ai mysql:8.0.36
    if ($LASTEXITCODE -ne 0) { $containerId = $null; throw 'Cannot start isolated browser-test MySQL' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker exec -e MYSQL_PWD=root $containerId mysql --protocol=TCP -h 127.0.0.1 -uroot -N -e 'SELECT 1' *>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Isolated MySQL startup timed out' }
    & docker cp (Join-Path $projectRoot 'initialize.sql') "${containerId}:/tmp/initialize.sql"
    & docker exec -e MYSQL_PWD=root $containerId mysql -uroot -e 'source /tmp/initialize.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot initialize isolated MySQL' }
    & docker cp (Join-Path $projectRoot 'scripts/deploy/mysql/init/06_exp3_sprint2.sql') "${containerId}:/tmp/06_exp3_sprint2.sql"
    & docker exec -e MYSQL_PWD=root $containerId mysql -uroot classroom_ai -e 'source /tmp/06_exp3_sprint2.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot migrate isolated MySQL' }
    & docker exec -e MYSQL_PWD=root $containerId mysql -uroot classroom_ai -e "UPDATE t_user_account SET password='123456' WHERE username IN ('director','supervisor','guojun','liubo');"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare isolated test accounts' }

    $redisContainerId = & docker run --detach --rm --pull=never --name ($name + '-redis') --publish '127.0.0.1::6379' redis:7.2-alpine
    if ($LASTEXITCODE -ne 0) { $redisContainerId = $null; throw 'Cannot start isolated browser-test Redis' }
    $redisBinding = & docker port $redisContainerId 6379/tcp
    if ($LASTEXITCODE -ne 0 -or $redisBinding -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Cannot resolve isolated Redis port' }
    $env:SPRING_DATA_REDIS_PORT = $Matches[1]
    $env:SPRING_DATA_REDIS_PASSWORD = ''

    $jar = Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path $jar)) { throw 'Build the backend JAR first' }
    $runDir = Join-Path $projectRoot ('runtime/browser-tests/' + $name)
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    $env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:13318/classroom_ai?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
    $env:SPRING_DATASOURCE_USERNAME = 'root'
    $env:SPRING_DATASOURCE_PASSWORD = 'root'
    $env:SPRING_DATA_REDIS_HOST = '127.0.0.1'
    $env:APP_SEED_DEMO = 'false'
    $backendProcess = Start-Process java -ArgumentList @('-jar', ('"' + $jar + '"'), '--server.port=18081') `
        -WorkingDirectory $runDir -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runDir 'backend.stdout.log') `
        -RedirectStandardError (Join-Path $runDir 'backend.stderr.log')
    $backendReady = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri 'http://127.0.0.1:18081/api/v1/auth/csrf' -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { $backendReady = $true; break }
        } catch { }
        if ($backendProcess.HasExited) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $backendReady) { throw "Isolated backend did not start; inspect $runDir" }
    $expiredBackendProcess = Start-Process java -ArgumentList @('-jar', ('"' + $jar + '"'), '--server.port=18082', '--classroom.resource.preview-minutes=0') `
        -WorkingDirectory $runDir -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runDir 'expired-backend.stdout.log') `
        -RedirectStandardError (Join-Path $runDir 'expired-backend.stderr.log')
    $expiredReady = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri 'http://127.0.0.1:18082/api/v1/auth/csrf' -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { $expiredReady = $true; break }
        } catch { }
        if ($expiredBackendProcess.HasExited) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $expiredReady) { throw "Expired-ticket backend did not start; inspect $runDir" }
    if (-not $env:PLAYWRIGHT_MODULE) {
        $candidate = Get-ChildItem (Join-Path $env:USERPROFILE '.vscode/extensions') -Directory -Filter 'vscjava.migrate-java-to-azure-*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'node_modules/playwright' } |
            Where-Object { Test-Path $_ } | Select-Object -First 1
        if ($candidate) { $env:PLAYWRIGHT_MODULE = $candidate }
    }
    if (-not $env:EXP3_CHROMIUM_PATH) {
        $candidate = Join-Path $env:LOCALAPPDATA 'ms-playwright/chromium-1228/chrome-win64/chrome.exe'
        if (Test-Path $candidate) { $env:EXP3_CHROMIUM_PATH = $candidate }
    }
    $frontendDir = Join-Path $projectRoot 'frontend'
    if (-not (Test-Path (Join-Path $frontendDir 'dist/index.html'))) { throw 'Build the frontend first (npm run build)' }
    $env:CLASSROOM_API_PROXY = 'http://127.0.0.1:18081'
    $frontendProcess = Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js', 'preview', '--config', 'vite.config.ts', '--host', '127.0.0.1', '--port', '15173', '--strictPort') `
        -WorkingDirectory $frontendDir -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runDir 'frontend.stdout.log') `
        -RedirectStandardError (Join-Path $runDir 'frontend.stderr.log')
    $frontendReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri 'http://127.0.0.1:15173' -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { $frontendReady = $true; break }
        } catch { }
        if ($frontendProcess.HasExited) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $frontendReady) { throw "Isolated frontend did not start; inspect $runDir" }
    $env:EXP3_FRONTEND_URL = 'http://127.0.0.1:15173'
    $env:EXP3_BACKEND_URL = 'http://127.0.0.1:18081'
    $env:EXP3_EXPIRED_BACKEND_URL = 'http://127.0.0.1:18082'
    $env:EXP3_TEST_MYSQL_CONTAINER = $containerId
    & node (Join-Path $projectRoot 'scripts/tests/exp3-real-browser.cjs')
    if ($LASTEXITCODE -ne 0) { throw 'Sprint 2 real-browser acceptance failed' }
    & node (Join-Path $projectRoot 'scripts/tests/fix-exp3-acceptance.cjs')
    if ($LASTEXITCODE -ne 0) { throw 'Sprint 2 strict acceptance additions failed' }
} finally {
    $env:EXP3_FRONTEND_URL = $previousFrontendUrl
    $env:CLASSROOM_API_PROXY = $previousApiProxy
    if ($frontendProcess -and -not $frontendProcess.HasExited) { Stop-Process -Id $frontendProcess.Id -Force }
    $env:SPRING_DATASOURCE_URL = $previousDatasource
    $env:SPRING_DATASOURCE_USERNAME = $previousDatasourceUsername
    $env:SPRING_DATASOURCE_PASSWORD = $previousDatasourcePassword
    $env:SPRING_DATA_REDIS_HOST = $previousRedis
    $env:SPRING_DATA_REDIS_PORT = $previousRedisPort
    $env:SPRING_DATA_REDIS_PASSWORD = $previousRedisPassword
    $env:APP_SEED_DEMO = $previousSeed
    $env:EXP3_BACKEND_URL = $previousBackend
    $env:EXP3_EXPIRED_BACKEND_URL = $previousExpiredBackend
    $env:PLAYWRIGHT_MODULE = $previousModule
    $env:EXP3_CHROMIUM_PATH = $previousChromium
    $env:EXP3_TEST_MYSQL_CONTAINER = $previousTestMysql
    if ($backendProcess -and -not $backendProcess.HasExited) { Stop-Process -Id $backendProcess.Id -Force }
    if ($expiredBackendProcess -and -not $expiredBackendProcess.HasExited) { Stop-Process -Id $expiredBackendProcess.Id -Force }
    if ($containerId) { & docker stop $containerId | Out-Null }
    if ($redisContainerId) { & docker stop $redisContainerId | Out-Null }
}
