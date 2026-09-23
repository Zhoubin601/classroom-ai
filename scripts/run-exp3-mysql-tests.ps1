param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$previousUrl = $env:EXP3_MYSQL_URL
$containerId = $null
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$bundledMaven = Join-Path $projectRoot 'backend/.tools/apache-maven-3.9.6/bin/mvn.cmd'
$mavenPath = if ($maven) { $maven.Source } elseif (Test-Path $bundledMaven) { $bundledMaven } else {
    Get-ChildItem (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mavenPath) { throw 'Maven not found' }
try {
    $name = 'classroom-exp3-test-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $containerId = & docker run --detach --rm --pull=never --name $name --publish 127.0.0.1:13317:3306 `
        --env MYSQL_ALLOW_EMPTY_PASSWORD=yes --env MYSQL_DATABASE=exp3_test mysql:8.0.36
    if ($LASTEXITCODE -ne 0) { $containerId = $null; throw 'Cannot start disposable MySQL on port 13317' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker exec $containerId mysqladmin ping -h 127.0.0.1 -uroot *>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Disposable MySQL startup timed out' }
    & docker cp (Join-Path $projectRoot 'initialize.sql') "${containerId}:/tmp/initialize.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy baseline schema' }
    & docker exec $containerId mysql -uroot -e 'source /tmp/initialize.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Baseline schema failed' }
    & docker cp (Join-Path $projectRoot 'scripts/deploy/mysql/init/06_exp3_sprint2.sql') "${containerId}:/tmp/06_exp3_sprint2.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy Sprint 2 migration' }
    1..2 | ForEach-Object {
        & docker exec $containerId mysql -uroot classroom_ai -e 'source /tmp/06_exp3_sprint2.sql'
        if ($LASTEXITCODE -ne 0) { throw 'Sprint 2 migration is not idempotent' }
    }
    $pendingLegacy = & docker exec $containerId mysql -uroot classroom_ai -N -e "SELECT COUNT(*) FROM t_supervision_evaluation WHERE status='PENDING_DESENSITIZE';"
    if ($LASTEXITCODE -ne 0 -or [int]$pendingLegacy -ne 0) { throw 'Legacy pending evaluations were not migrated to review' }
    $unreviewedPublished = & docker exec $containerId mysql -uroot classroom_ai -N -e "SELECT COUNT(*) FROM t_supervision_evaluation WHERE status='PUBLISHED' AND reviewed_by IS NULL;"
    if ($LASTEXITCODE -ne 0 -or [int]$unreviewedPublished -ne 0) { throw 'Unreviewed legacy evaluations remain visible to teachers' }
    $env:EXP3_MYSQL_URL = 'jdbc:mysql://127.0.0.1:13317/exp3_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
    & $mavenPath -f (Join-Path $projectRoot 'backend/pom.xml') -o '-Dtest=Sprint2MysqlTest' test
    if ($LASTEXITCODE -ne 0) { throw 'Sprint 2 MySQL integration tests failed' }
} finally {
    $env:EXP3_MYSQL_URL = $previousUrl
    if ($containerId) { & docker stop $containerId | Out-Null }
}
