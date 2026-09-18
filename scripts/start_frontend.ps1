$ErrorActionPreference = 'Stop'
Push-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend')
try {
    if (-not (Test-Path 'node_modules/vite/bin/vite.js')) {
        & npm.cmd install
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed' }
    }
    & npm.cmd run dev
    if ($LASTEXITCODE -ne 0) { throw 'Frontend startup failed' }
} finally { Pop-Location }

