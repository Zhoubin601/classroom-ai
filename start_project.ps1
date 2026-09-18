param([switch]$SkipBuild)
# Compatibility entry; implementation lives in scripts/.
& (Join-Path $PSScriptRoot 'scripts/start_project.ps1') -SkipBuild:$SkipBuild
