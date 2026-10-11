$ErrorActionPreference='Stop'
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$baseline=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'protected-before.json') -Raw | ConvertFrom-Json -AsHashtable
$result=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'result.json') -Raw | ConvertFrom-Json
$plan=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'plan.json') -Raw | ConvertFrom-Json
$intentionalNotes=@('docs/plan.md','docs/analysis-notes.md','docs/source-notes.md')
$failures=@()
foreach ($relative in $baseline.files.Keys) {
    if ($relative -in $intentionalNotes) { continue }
    $path=Join-Path $workspace $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -ne $baseline.files[$relative]) { $failures+=$relative }
}
foreach ($action in $plan.actions) { if (Test-Path -LiteralPath (Join-Path $workspace $action.path)) { $failures+='Target remains: '+$action.path } }
foreach ($item in $plan.preserve) {
    if ((Get-FileHash -LiteralPath (Join-Path $workspace $item.to)).Hash.ToLowerInvariant() -ne $item.sha256) { $failures+='Recovery differs: '+$item.to }
}
if ((git -C $workspace rev-parse HEAD).Trim() -ne $baseline.head) { $failures+='HEAD changed' }
if ((git -C $workspace branch --show-current).Trim() -ne $baseline.branch) { $failures+='Branch changed' }
if (@(Compare-Object @($baseline.refs) @(git -C $workspace show-ref)).Count) { $failures+='Refs changed' }
if (@(Compare-Object @($baseline.stashes) @(git -C $workspace stash list --format=%H)).Count) { $failures+='Stash changed' }
if (@(Compare-Object @($baseline.trackedStatus) @(git -C $workspace status --porcelain -uno)).Count) { $failures+='Tracked status changed' }
if ($failures.Count) { throw ($failures -join ', ') }
$verification=@{passed=$true;protectedFilesRechecked=$baseline.files.Count-$intentionalNotes.Count;intentionallyAppendedProcessNotes=$intentionalNotes;reportExists=(Test-Path -LiteralPath (Join-Path $workspace 'docs/20261010-project-cleanup-v1.md'));resultPassed=$result.passed;allCleanupTargetsAbsent=$true;recoveredEvidenceHashesMatch=$true;gitBaselineUnchanged=$true;rawFiles=@(Get-ChildItem -LiteralPath (Join-Path $workspace 'raw') -File -Recurse).Count;outputFiles=@(Get-ChildItem -LiteralPath (Join-Path $workspace 'output') -File -Recurse).Count}
$verification | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'final-verification.json') -Encoding utf8
$verification | ConvertTo-Json -Depth 4
