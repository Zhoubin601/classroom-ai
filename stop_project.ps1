param([switch]$StopDocker)
$ErrorActionPreference = 'Continue'
$projectRoot = $PSScriptRoot
$logDir = Join-Path $projectRoot 'runtime/logs'

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " Stopping Classroom AI services..." -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Stop backend by PID
$backendPidFile = Join-Path $logDir 'backend.pid'
if (Test-Path $backendPidFile) {
    $bPid = Get-Content $backendPidFile -ErrorAction SilentlyContinue
    if ($bPid) {
        $p = Get-Process -Id $bPid -ErrorAction SilentlyContinue
        if ($p) {
            Stop-Process -Id $bPid -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped backend process (PID $bPid)" -ForegroundColor Green
        }
    }
    Remove-Item $backendPidFile -Force -ErrorAction SilentlyContinue
}

# 2. Stop frontend by PID
$frontendPidFile = Join-Path $logDir 'frontend.pid'
if (Test-Path $frontendPidFile) {
    $fPid = Get-Content $frontendPidFile -ErrorAction SilentlyContinue
    if ($fPid) {
        $p = Get-Process -Id $fPid -ErrorAction SilentlyContinue
        if ($p) {
            Stop-Process -Id $fPid -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped frontend process (PID $fPid)" -ForegroundColor Green
        }
    }
    Remove-Item $frontendPidFile -Force -ErrorAction SilentlyContinue
}

& (Join-Path $projectRoot 'scripts/compose.ps1') stop classroom-backend

if ($StopDocker) {
    Write-Host "Stopping Docker containers (MySQL & Redis)..." -ForegroundColor Cyan
    & (Join-Path $projectRoot 'scripts/compose.ps1') stop mysql redis
}

Write-Host "All services stopped." -ForegroundColor Green
