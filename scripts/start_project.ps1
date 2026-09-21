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
    if (-not (Test-ProjectUrl 'http://127.0.0.1:8080/api/v1/courses')) {
        $backendContainer = docker ps -a -q -f name=^classroom-backend$
        if ($backendContainer) {
            try { & docker stop classroom-backend *>$null } catch {}
        }
        Write-Host "[2/3] Starting Java backend (Spring Boot 3.3)..." -ForegroundColor Cyan
        $maven = Get-Command mvn -ErrorAction SilentlyContinue
        if (-not $maven) { $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue }
        $mavenPath = if ($maven) { $maven.Source } else {
            Get-ChildItem (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
                Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
        }

        $jarRel = 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
        $jar = Join-Path $projectRoot $jarRel
        $jarExists = Test-Path -LiteralPath $jar

        if ($Rebuild -or (-not $jarExists -and -not $SkipBuild)) {
            Write-Host "Building Java backend jar..." -ForegroundColor Cyan
            if (-not $mavenPath) { throw 'Maven not found. Add mvn to PATH.' }
            & $mavenPath -f "$projectRoot/backend/pom.xml" "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" -DskipTests package
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
            $jarExists = Test-Path -LiteralPath $jar
        }
        if (-not $jarExists) { throw 'Backend jar missing; please build backend first or run with -Rebuild.' }

        $backend = Start-Process java -ArgumentList @('-jar', $jarRel) -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput "$logDir/backend.stdout.log" -RedirectStandardError "$logDir/backend.stderr.log"
        $backend.Id | Set-Content "$logDir/backend.pid"
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            if (Test-ProjectUrl 'http://127.0.0.1:8080/api/v1/courses') { break }
            if ($backend.HasExited) {
                $errContent = if (Test-Path "$logDir/backend.stderr.log") { Get-Content "$logDir/backend.stderr.log" -Tail 10 } else { '' }
                throw "Backend exited unexpectedly.`n$errContent`nSee $logDir/backend.stderr.log"
            }
            Start-Sleep -Seconds 1
        }
        if (-not (Test-ProjectUrl 'http://127.0.0.1:8080/api/v1/courses')) { throw 'Backend readiness timed out; inspect runtime/logs.' }
    }
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
    Start-Process "http://127.0.0.1:5173"

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

