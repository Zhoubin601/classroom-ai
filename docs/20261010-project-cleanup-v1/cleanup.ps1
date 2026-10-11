param([ValidateSet('Inspect','Apply')][string]$Mode = 'Inspect')
$ErrorActionPreference = 'Stop'
$workspace = 'D:\2026Autumn Semester File\classroom-ai-demo'
$workspace = (Resolve-Path -LiteralPath $workspace).Path.TrimEnd('\')
$recordDir = $PSScriptRoot
$planFile = Join-Path $recordDir 'plan.json'
$beforeFile = Join-Path $recordDir 'protected-before.json'
$resultFile = Join-Path $recordDir 'result.json'
$script:excludedRelative = @()

function Safe-Path([string]$relative) {
    $candidate = [IO.Path]::GetFullPath((Join-Path $workspace $relative))
    if (-not $candidate.StartsWith($workspace + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Outside workspace: $relative" }
    $cursor = $candidate
    while ($cursor -ne $workspace) {
        if (Test-Path -LiteralPath $cursor) {
            $item = Get-Item -LiteralPath $cursor -Force
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse point: $cursor" }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
    return $candidate
}
function Relative-Path([string]$absolute) { return $absolute.Substring($workspace.Length + 1).Replace('\','/') }
function Hash([string]$file) { return (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() }
function Save-Json($value, [string]$path) { $value | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $path -Encoding utf8 }
function Directory-Stats([string]$path) {
    $children = @(Get-ChildItem -LiteralPath $path -Recurse -Force)
    if (@($children | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw "Reparse child in $path" }
    $files = @($children | Where-Object { -not $_.PSIsContainer })
    return @{ files=$files.Count; bytes=[long](($files | Measure-Object Length -Sum).Sum) }
}
function Assert-No-Active-Target {
    $active = @(Get-CimInstance Win32_Process | Where-Object {
        $_.CommandLine -match 'main-startup-check-20261006|fix-exp3-local-check-20261006|temporary-module|handover-build-v1[\\/]+staging|runtime[\\/]+browser-tests'
    })
    if ($active.Count) { throw 'A cleanup target is used by a running process.' }
}
function Protected-Snapshot {
    $result = @{}
    $tracked = @(git -C $workspace -c core.quotepath=false ls-files)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot list tracked files.' }
    $extra = foreach ($folder in @('raw','output','assets','memory-bank','docs','runtime/uploads','frontend/src','backend/src','vision','scripts')) {
        Get-ChildItem -LiteralPath (Safe-Path $folder) -File -Recurse -Force | Where-Object {
            $_.FullName -notmatch '[\\/](__pycache__|node_modules|target|\.tools)[\\/]'
        } | ForEach-Object { Relative-Path $_.FullName }
    }
    foreach ($relative in @($tracked + $extra | Sort-Object -Unique)) {
        if ($relative.StartsWith('docs/20261010-project-cleanup-v1/')) { continue }
        if ($relative -match '\.(log|pid)$') { continue }
        if (@($script:excludedRelative | Where-Object { $relative -eq $_ -or $relative.StartsWith($_ + '/') }).Count) { continue }
        if ($relative.StartsWith('docs/20261010-handover-build-v1/staging/')) { continue }
        if ($relative.StartsWith('docs/20261010-attendance-test-import-check-v1/temporary-module/')) { continue }
        $path = Safe-Path $relative
        if (Test-Path -LiteralPath $path -PathType Leaf) { $result[$relative] = Hash $path }
    }
    return $result
}

if ($Mode -eq 'Inspect') {
    if ((Test-Path -LiteralPath $planFile) -or (Test-Path -LiteralPath $beforeFile)) { throw 'Inspection already exists; do not overwrite.' }
    Assert-No-Active-Target
    $actions = [Collections.Generic.List[object]]::new()
    $retainedBackups = [Collections.Generic.List[string]]::new()
    $backupBase = Safe-Path 'runtime/branch-switch-backups/20261009-143732/files'
    foreach ($file in Get-ChildItem -LiteralPath $backupBase -Recurse -File -Force) {
        $relative = $file.FullName.Substring($backupBase.Length + 1).Replace('\','/')
        $original = Safe-Path $relative
        if ((Test-Path -LiteralPath $original -PathType Leaf) -and (Get-Item -LiteralPath $original).Length -eq $file.Length -and (Hash $file.FullName) -eq (Hash $original)) {
            $actions.Add(@{type='file';path=(Relative-Path $file.FullName);keep=$relative;sha256=(Hash $original);files=1;bytes=$file.Length;reason='SHA256 identical backup; main copy retained'})
        } else { $retainedBackups.Add((Relative-Path $file.FullName)) }
    }
    $stage = Safe-Path 'docs/20261010-handover-build-v1/staging'
    foreach ($file in Get-ChildItem -LiteralPath $stage -File -Recurse) {
        $delivered = Safe-Path ('output/' + $file.FullName.Substring($stage.Length + 1))
        if (-not (Test-Path -LiteralPath $delivered -PathType Leaf) -or (Hash $file.FullName) -ne (Hash $delivered)) { throw 'Staging differs from delivered files.' }
    }
    $reports = Safe-Path 'docs/20261010-attendance-test-import-check-v1/temporary-module/target/surefire-reports'
    foreach ($name in @('com.classroom.ai.modules.course.AssociationMysqlTest.txt','TEST-com.classroom.ai.modules.course.AssociationMysqlTest.xml')) {
        if ((Hash (Join-Path $reports $name)) -ne (Hash (Safe-Path ('docs/20261010-attendance-test-import-check-v1/temporary-fixed/' + $name)))) { throw 'Test evidence differs.' }
    }
    $preserve = @(
        @{from='runtime/worktrees/fix-exp3-local-check-20261006/docs/sprint2-chrome-evidence/03-teacher-published-feedback.png';to='docs/20261010-project-cleanup-v1/recovered-evidence/fix-exp3-local-check-20261006-teacher-published-feedback.png'},
        @{from='docs/20261010-attendance-test-import-check-v1/temporary-module/target/surefire-reports/2026-10-10T13-44-07_298.dumpstream';to='docs/20261010-project-cleanup-v1/recovered-evidence/2026-10-10T13-44-07_298.dumpstream'}
    )
    foreach ($item in $preserve) { $item.sha256=Hash (Safe-Path $item.from); $item.bytes=(Get-Item -LiteralPath (Safe-Path $item.from)).Length }
    foreach ($relative in @('runtime/worktrees/fix-exp3-local-check-20261006','runtime/worktrees/main-startup-check-20261006')) {
        $path = Safe-Path $relative
        $head = (git -C $path rev-parse HEAD).Trim()
        git -C $workspace merge-base --is-ancestor $head HEAD
        if ($LASTEXITCODE -ne 0) { throw "Unmerged worktree commit: $relative" }
        $dirty = @(git -C $path status --porcelain)
        $expected = if ($relative -match 'fix-exp3-local') { @('?? docs/sprint2-chrome-evidence/03-teacher-published-feedback.png') } else { @() }
        if (($dirty -join "`n") -ne ($expected -join "`n")) { throw "Unexpected worktree changes: $relative" }
        $stats = Directory-Stats $path
        $actions.Add(@{type='worktree';path=$relative;head=$head;files=$stats.files;bytes=$stats.bytes;reason='Completed isolated test worktree; commit reachable from current HEAD'})
    }
    foreach ($relative in @('docs/20261010-handover-build-v1/staging','docs/20261010-attendance-test-import-check-v1/temporary-module','docs/20261010-handover-build-v1/__pycache__','vision/__pycache__','vision/tests/__pycache__','docs/archive/root-cleanup-20260918/root-pycache','runtime/browser-tests','docker')) {
        $path = Safe-Path $relative
        if (-not (Test-Path -LiteralPath $path)) { continue }
        $stats = Directory-Stats $path
        if ($relative -eq 'docker' -and $stats.files -ne 0) { throw 'Old docker folder is not empty.' }
        $actions.Add(@{type='directory';path=$relative;files=$stats.files;bytes=$stats.bytes;reason='Generated temporary copy/cache, expired isolated tests, or empty obsolete folder'})
    }
    foreach ($relative in @('error.log','runtime/logs/frontend.pid','docs/runtime/backend.pid','docs/runtime/frontend.pid')) {
        $path = Safe-Path $relative
        if (-not (Test-Path -LiteralPath $path)) { continue }
        if ($relative.EndsWith('.pid')) {
            $processNumber = 0
            if (-not [int]::TryParse((Get-Content -LiteralPath $path -Raw).Trim(), [ref]$processNumber)) { continue }
            if (Get-Process -Id $processNumber -ErrorAction SilentlyContinue) { continue }
        }
        $actions.Add(@{type='file';path=$relative;files=1;bytes=(Get-Item -LiteralPath $path).Length;sha256=(Hash $path);reason='Old root diagnostic log or PID for nonexistent process'})
    }
    $script:excludedRelative = @($actions | ForEach-Object path)
    $protected = Protected-Snapshot
    foreach ($relative in $retainedBackups) { $protected[$relative] = Hash (Safe-Path $relative) }
    $baseline = @{head=(git -C $workspace rev-parse HEAD).Trim();branch=(git -C $workspace branch --show-current).Trim();refs=@(git -C $workspace show-ref);stashes=@(git -C $workspace stash list --format=%H);trackedStatus=@(git -C $workspace status --porcelain -uno);files=$protected}
    Save-Json $baseline $beforeFile
    $plan = @{date='2026-10-10';root=$workspace;actions=@($actions);preserve=$preserve;retainedDifferentBackups=@($retainedBackups)}
    Save-Json $plan $planFile
    @{actions=$actions.Count;files=($actions | Measure-Object files -Sum).Sum;MiB=[math]::Round(($actions | Measure-Object bytes -Sum).Sum / 1MB,2);differentBackupsRetained=$retainedBackups.Count;protectedFiles=$baseline.files.Count} | ConvertTo-Json
    exit
}

if (Test-Path -LiteralPath $resultFile) { throw 'Cleanup already executed; do not rerun.' }
$plan = Get-Content -LiteralPath $planFile -Raw | ConvertFrom-Json
$baseline = Get-Content -LiteralPath $beforeFile -Raw | ConvertFrom-Json -AsHashtable
if ($plan.root -ne $workspace) { throw 'Workspace mismatch.' }
$script:excludedRelative = @($plan.actions | ForEach-Object path)
Assert-No-Active-Target
$current = Protected-Snapshot
foreach ($relative in $plan.retainedDifferentBackups) { $current[$relative] = Hash (Safe-Path $relative) }
foreach ($relative in $baseline.files.Keys) { if ($current[$relative] -ne $baseline.files[$relative]) { throw "Protected file changed before cleanup: $relative" } }
foreach ($action in $plan.actions) {
    $path = Safe-Path $action.path
    if (-not (Test-Path -LiteralPath $path)) { throw "Target disappeared: $($action.path)" }
    if ($action.type -eq 'file') {
        if ((Hash $path) -ne $action.sha256) { throw 'File changed since inspection.' }
        if ($action.keep -and (Hash (Safe-Path $action.keep)) -ne $action.sha256) { throw 'Retained copy changed.' }
    } else {
        $stats = Directory-Stats $path
        if ($stats.files -ne $action.files -or $stats.bytes -ne $action.bytes) { throw 'Directory changed since inspection.' }
    }
}
foreach ($item in $plan.preserve) {
    $source = Safe-Path $item.from
    $destination = Safe-Path $item.to
    if ((Hash $source) -ne $item.sha256 -or (Test-Path -LiteralPath $destination)) { throw 'Recovery copy precondition failed.' }
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($destination)) -Force | Out-Null
    Move-Item -LiteralPath $source -Destination $destination
    if ((Hash $destination) -ne $item.sha256) { throw 'Recovery hash mismatch.' }
}
$completed = [Collections.Generic.List[object]]::new()
try {
    foreach ($action in $plan.actions) {
        $path = Safe-Path $action.path
        if ($action.type -eq 'worktree') {
            if (@(git -C $path status --porcelain).Count) { throw 'Worktree still has changes.' }
            & git -C $workspace worktree remove -- $path
            if ($LASTEXITCODE -ne 0) { throw "Git refused worktree removal: $path" }
        } elseif ($action.type -eq 'directory') {
            Remove-Item -LiteralPath $path -Recurse -Force
        } else {
            Remove-Item -LiteralPath $path -Force
        }
        if (Test-Path -LiteralPath $path) { throw "Target remains: $path" }
        $completed.Add($action)
    }
    $backupRoot = Safe-Path 'runtime/branch-switch-backups/20261009-143732/files'
    Get-ChildItem -LiteralPath $backupRoot -Directory -Recurse -Force | Sort-Object { $_.FullName.Length } -Descending | ForEach-Object {
        $path = Safe-Path (Relative-Path $_.FullName)
        if (@(Get-ChildItem -LiteralPath $path -Force).Count -eq 0) { Remove-Item -LiteralPath $path -Force }
    }
    $after = Protected-Snapshot
    foreach ($relative in $plan.retainedDifferentBackups) { $after[$relative] = Hash (Safe-Path $relative) }
    $differences = @($baseline.files.Keys | Where-Object { $after[$_] -ne $baseline.files[$_] })
    if ($differences.Count) { throw ('Protected files differ: ' + ($differences -join ', ')) }
    if ((git -C $workspace rev-parse HEAD).Trim() -ne $baseline.head -or (git -C $workspace branch --show-current).Trim() -ne $baseline.branch) { throw 'Git baseline changed.' }
    if (@(Compare-Object @($baseline.refs) @(git -C $workspace show-ref)).Count) { throw 'Git refs changed.' }
    if (@(Compare-Object @($baseline.stashes) @(git -C $workspace stash list --format=%H)).Count) { throw 'Git stashes changed.' }
    if (@(Compare-Object @($baseline.trackedStatus) @(git -C $workspace status --porcelain -uno)).Count) { throw 'Tracked worktree status changed.' }
    foreach ($relative in $plan.retainedDifferentBackups) { if (-not (Test-Path -LiteralPath (Safe-Path $relative))) { throw 'Different backup missing.' } }
    $removedFiles = [long](($completed | Measure-Object files -Sum).Sum) - $plan.preserve.Count
    $freedBytes = [long](($completed | Measure-Object bytes -Sum).Sum) - [long](($plan.preserve | Measure-Object bytes -Sum).Sum)
    $result = @{passed=$true;removedFiles=$removedFiles;freedBytes=$freedBytes;freedMiB=[math]::Round($freedBytes/1MB,2);removedWorktrees=@($completed | Where-Object type -eq worktree | ForEach-Object path);duplicateBackupsRemoved=@($completed | Where-Object { $_.keep }).Count;differentBackupsRetained=$plan.retainedDifferentBackups.Count;preservedEvidence=$plan.preserve;protectedFilesUnchanged=$baseline.files.Count;head=$baseline.head;branch=$baseline.branch;gitRefsAndStashesUnchanged=$true;trackedChangesUnchanged=$true;remainingWorktrees=@(git -C $workspace worktree list --porcelain);actions=@($completed)}
    Save-Json $result $resultFile
    $result | Select-Object passed,removedFiles,freedMiB,removedWorktrees,duplicateBackupsRemoved,differentBackupsRetained,protectedFilesUnchanged | ConvertTo-Json
} catch {
    Save-Json @{passed=$false;error=$_.Exception.Message;completedActions=@($completed);preservedEvidence=$plan.preserve} $resultFile
    throw
}
