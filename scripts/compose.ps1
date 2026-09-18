# Forward Docker Compose arguments using one stable configuration location.
$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot 'deploy/docker-compose.yml'
Push-Location (Join-Path $PSScriptRoot 'deploy')
try {
    & docker compose -f $composeFile @args
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed (exit $LASTEXITCODE)." }
} finally { Pop-Location }
