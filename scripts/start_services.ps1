# Start the database and cache, fail clearly if Docker is unavailable.
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & docker info --format '{{.ServerVersion}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is unavailable. Resolve Docker Desktop startup errors before continuing.' }
    & "$PSScriptRoot/compose.ps1" up -d --wait --wait-timeout 90 mysql redis
    & "$PSScriptRoot/compose.ps1" ps
} finally { Pop-Location }

