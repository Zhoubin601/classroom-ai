param([switch]$SkipBuild, [switch]$Rebuild, [switch]$NonInteractive)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$logDir = Join-Path $projectRoot 'runtime/logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

function Test-ProjectUrl([string]$url) {
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
        return $resp.StatusCode -eq 200
    } catch {
        if ($_.Exception.Response -and ($_.Exception.Response.StatusCode.value__ -in @(200, 401, 403))) {
            return $true
        }
        return $false
    }
}

Push-Location $projectRoot
try {
    Write-Host "[1/3] Checking and starting base services (MySQL 8.0 & Redis 7.2)..." -ForegroundColor Cyan
    & "$PSScriptRoot/start_services.ps1"

    # Fresh Docker volumes are initialized by docker-entrypoint-initdb.d once.
    # Existing volumes receive only the additive, repeatable Sprint 2 migration.
    $tableCount = & docker exec classroom-mysql mysql -uroot -proot classroom_ai -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='classroom_ai' AND table_name='t_user_account';" 2>$null
    if ($LASTEXITCODE -ne 0 -or [int]$tableCount -ne 1) { throw 'MySQL schema is unavailable; initialization was not repeated to protect existing data.' }
    $migration = Join-Path $projectRoot 'scripts/deploy/mysql/init/06_exp3_sprint2.sql'
    & docker cp $migration 'classroom-mysql:/tmp/06_exp3_sprint2.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy Sprint 2 migration' }
    & docker exec classroom-mysql mysql -uroot -proot --default-character-set=utf8mb4 classroom_ai -e 'source /tmp/06_exp3_sprint2.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Sprint 2 migration failed; see MySQL output' }
    & docker exec classroom-mysql rm -f /tmp/06_exp3_sprint2.sql | Out-Null

    $pidFile = Join-Path $logDir 'backend.pid'
    if (Test-Path $pidFile) {
        $oldPid = [int](Get-Content $pidFile -ErrorAction SilentlyContinue)
        $oldProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$oldPid" -ErrorAction SilentlyContinue
        if ($oldProcess -and $oldProcess.CommandLine -match 'classroom-backend-.*\.jar') {
            Stop-Process -Id $oldPid -Force -ErrorAction Stop
        }
        Remove-Item $pidFile -Force
    }
    $jar = Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
    if (-not $SkipBuild) {
        $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        $bundledMaven = Join-Path $projectRoot 'backend/.tools/apache-maven-3.9.6/bin/mvn.cmd'
        $mavenPath = if ($maven) { $maven.Source } elseif (Test-Path $bundledMaven) { $bundledMaven } else {
            Get-ChildItem (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
                Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
        }
        if (-not $mavenPath) { throw 'Maven not found' }
        & $mavenPath -f (Join-Path $projectRoot 'backend/pom.xml') '-DskipTests' package
        if ($LASTEXITCODE -ne 0) { throw 'Backend JAR build failed' }
    }
    if (-not (Test-Path $jar)) { throw 'Backend JAR missing; run without -SkipBuild' }
    Write-Host "[2/3] Starting backend container with LibreOffice..." -ForegroundColor Cyan
    $composeArgs = @('up', '-d', '--wait', '--wait-timeout', '180')
    if (-not $SkipBuild) { $composeArgs += '--build' }
    if ($SkipBuild) { $composeArgs += '--no-build' }
    $composeArgs += 'classroom-backend'
    & "$PSScriptRoot/compose.ps1" @composeArgs
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if (Test-ProjectUrl 'http://127.0.0.1:8080/api/v1/courses') { break }
        Start-Sleep -Seconds 1
    }
    if (-not (Test-ProjectUrl 'http://127.0.0.1:8080/api/v1/courses')) { throw 'Backend readiness timed out; inspect docker logs classroom-backend.' }
    if (-not (Test-ProjectUrl 'http://127.0.0.1:5173')) {
        Write-Host "[3/3] Starting frontend (Vite / Vue 3)..." -ForegroundColor Cyan
        $frontendDir = Join-Path $projectRoot 'frontend'
        if (-not (Test-Path "$frontendDir/node_modules/vite/bin/vite.js")) {
            & npm.cmd --prefix $frontendDir install
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed' }
        }
        $frontend = Start-Process npm.cmd -ArgumentList @('run', 'dev') `
            -WorkingDirectory $frontendDir -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput "$logDir/frontend.stdout.log" -RedirectStandardError "$logDir/frontend.stderr.log"
        $frontend.Id | Set-Content "$logDir/frontend.pid"
        for ($attempt = 0; $attempt -lt 25; $attempt++) {
            if (Test-ProjectUrl 'http://127.0.0.1:5173') { break }
            if ($frontend.HasExited) {
                $errContent = if (Test-Path "$logDir/frontend.stderr.log") { Get-Content "$logDir/frontend.stderr.log" -Tail 10 } else { '' }
                throw "Frontend exited unexpectedly.`n$errContent`nSee $logDir/frontend.stderr.log"
            }
            Start-Sleep -Seconds 1
        }
        if (-not (Test-ProjectUrl 'http://127.0.0.1:5173')) { throw 'Frontend readiness timed out.' }
    }
    Write-Host "==================================================" -ForegroundColor Green
    Write-Host " [SUCCESS] All services ready!" -ForegroundColor Green
    Write-Host " URL: http://127.0.0.1:5173" -ForegroundColor Green
    Write-Host " Opening browser..." -ForegroundColor Green
    Write-Host "==================================================" -ForegroundColor Green
    if (-not $NonInteractive) { Start-Process "http://127.0.0.1:5173" }

    if (-not $NonInteractive) {
        Write-Host ""
        Write-Host "Services are running in background. Press Enter to close this window..." -ForegroundColor Gray
        Read-Host
    }
} catch {
    Write-Host ""
    Write-Host "==================================================" -ForegroundColor Red
    Write-Host " [ERROR] Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host " Log files are located in: runtime/logs/" -ForegroundColor Yellow
    Write-Host "==================================================" -ForegroundColor Red
    Write-Host ""
    if (-not $NonInteractive) {
        Read-Host "Press Enter to exit..."
    }
    exit 1
} finally {
    Pop-Location
}

