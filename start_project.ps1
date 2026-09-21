param([switch]$SkipBuild, [switch]$Rebuild, [switch]$NonInteractive)
# Compatibility entry; implementation lives in scripts/.
& (Join-Path $PSScriptRoot 'scripts/start_project.ps1') -SkipBuild:$SkipBuild -Rebuild:$Rebuild -NonInteractive:$NonInteractive
