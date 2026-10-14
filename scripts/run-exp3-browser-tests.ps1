param([string]$EvidenceDir,[ValidateSet('Auto','Native','Docker')][string]$BackendMode='Auto',[string]$BackendImage='classroom-ai-demo-classroom-backend:latest')
# Preserve the existing command while using the complete current Sprint 2 acceptance runner.
& (Join-Path $PSScriptRoot 'run-sprint2-acceptance.ps1') @PSBoundParameters
