param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$logDir = Join-Path $projectRoot 'runtime/logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

function Test-ProjectUrl([string]$url) {
    try { return (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 } catch { return $false }
}

Push-Location $projectRoot
try {
    & "$PSScriptRoot/start_services.ps1"
    if (-not (Test-ProjectUrl 'http://127.0.0.1:8080/api/v1/courses')) {
        $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        $mavenPath = if ($maven) { $maven.Source } else {
            Get-ChildItem (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
                Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
        }
        if (-not $SkipBuild) {
            if (-not $mavenPath) { throw 'Maven not found. Add mvn.cmd to PATH.' }
            & $mavenPath -f "$projectRoot/backend/pom.xml" "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" package
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
        }
        $jar = Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
        if (-not (Test-Path -LiteralPath $jar)) { throw 'Backend jar missing; run without -SkipBuild.' }
        $backend = Start-Process java -ArgumentList @('-jar', ('"{0}"' -f $jar)) -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput "$logDir/backend.stdout.log" -RedirectStandardError "$logDir/backend.stderr.log"
        $backend.Id | Set-Content "$logDir/backend.pid"
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            if (Test-ProjectUrl 'http://127.0.0.1:8080/api/v1/courses') { break }
            if ($backend.HasExited) { throw "Backend exited. See $logDir/backend.stderr.log" }
            Start-Sleep -Seconds 1
        }
        if (-not (Test-ProjectUrl 'http://127.0.0.1:8080/api/v1/courses')) { throw 'Backend readiness timed out; inspect runtime/logs.' }
    }
    if (-not (Test-ProjectUrl 'http://127.0.0.1:5173')) {
        $frontendDir = Join-Path $projectRoot 'frontend'
        if (-not (Test-Path "$frontendDir/node_modules/vite/bin/vite.js")) {
            & npm.cmd --prefix $frontendDir install
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed' }
        }
        $viteBin = Join-Path $frontendDir 'node_modules/vite/bin/vite.js'
        $frontend = Start-Process node -ArgumentList @(('"{0}"' -f $viteBin), '--config', 'vite.config.ts') `
            -WorkingDirectory $frontendDir -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput "$logDir/frontend.stdout.log" -RedirectStandardError "$logDir/frontend.stderr.log"
        $frontend.Id | Set-Content "$logDir/frontend.pid"
        for ($attempt = 0; $attempt -lt 20; $attempt++) {
            if (Test-ProjectUrl 'http://127.0.0.1:5173') { break }
            if ($frontend.HasExited) { throw 'Frontend exited; inspect runtime/logs.' }
            Start-Sleep -Seconds 1
        }
        if (-not (Test-ProjectUrl 'http://127.0.0.1:5173')) { throw 'Frontend readiness timed out.' }
    }
    Write-Host 'Project ready: http://127.0.0.1:5173' -ForegroundColor Green
} finally { Pop-Location }

