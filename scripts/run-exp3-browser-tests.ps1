param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$containerId = $null
$backendProcess = $null
$expiredBackendProcess = $null
$previousDatasource = $env:SPRING_DATASOURCE_URL
$previousRedis = $env:SPRING_DATA_REDIS_HOST
$previousSeed = $env:APP_SEED_DEMO
$previousBackend = $env:EXP3_BACKEND_URL
$previousExpiredBackend = $env:EXP3_EXPIRED_BACKEND_URL
$previousModule = $env:PLAYWRIGHT_MODULE
$previousChromium = $env:EXP3_CHROMIUM_PATH
try {
    if (Get-NetTCPConnection -LocalPort 13318,18081,18082 -State Listen -ErrorAction SilentlyContinue) {
        throw 'Temporary ports 13318, 18081 and 18082 must be free'
    }
    $frontend = Invoke-WebRequest -Uri 'http://127.0.0.1:5173' -UseBasicParsing -TimeoutSec 5
    if ($frontend.StatusCode -ne 200) { throw 'Start the project frontend first' }
    $name = 'classroom-exp3-browser-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $containerId = & docker run --detach --rm --pull=never --name $name --publish 127.0.0.1:13318:3306 `
        --env MYSQL_ROOT_PASSWORD=root --env MYSQL_DATABASE=classroom_ai mysql:8.0.36
    if ($LASTEXITCODE -ne 0) { $containerId = $null; throw 'Cannot start isolated browser-test MySQL' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker exec $containerId mysqladmin ping -h 127.0.0.1 -uroot -proot *>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Isolated MySQL startup timed out' }
    & docker cp (Join-Path $projectRoot 'initialize.sql') "${containerId}:/tmp/initialize.sql"
    & docker exec $containerId mysql -uroot -proot -e 'source /tmp/initialize.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot initialize isolated MySQL' }
    & docker cp (Join-Path $projectRoot 'scripts/deploy/mysql/init/06_exp3_sprint2.sql') "${containerId}:/tmp/06_exp3_sprint2.sql"
    & docker exec $containerId mysql -uroot -proot classroom_ai -e 'source /tmp/06_exp3_sprint2.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot migrate isolated MySQL' }
    & docker exec $containerId mysql -uroot -proot classroom_ai -e "UPDATE t_user_account SET password='123456' WHERE username IN ('director','supervisor','guojun','liubo');"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare isolated test accounts' }

    $jar = Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path $jar)) { throw 'Build the backend JAR first' }
    $runDir = Join-Path $projectRoot ('runtime/browser-tests/' + $name)
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    $env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:13318/classroom_ai?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
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
    $env:EXP3_BACKEND_URL = 'http://127.0.0.1:18081'
    $env:EXP3_EXPIRED_BACKEND_URL = 'http://127.0.0.1:18082'
    & node (Join-Path $projectRoot 'scripts/tests/exp3-real-browser.cjs')
    if ($LASTEXITCODE -ne 0) { throw 'Sprint 2 real-browser acceptance failed' }
} finally {
    $env:SPRING_DATASOURCE_URL = $previousDatasource
    $env:SPRING_DATA_REDIS_HOST = $previousRedis
    $env:APP_SEED_DEMO = $previousSeed
    $env:EXP3_BACKEND_URL = $previousBackend
    $env:EXP3_EXPIRED_BACKEND_URL = $previousExpiredBackend
    $env:PLAYWRIGHT_MODULE = $previousModule
    $env:EXP3_CHROMIUM_PATH = $previousChromium
    if ($backendProcess -and -not $backendProcess.HasExited) { Stop-Process -Id $backendProcess.Id -Force }
    if ($expiredBackendProcess -and -not $expiredBackendProcess.HasExited) { Stop-Process -Id $expiredBackendProcess.Id -Force }
    if ($containerId) { & docker stop $containerId | Out-Null }
}
