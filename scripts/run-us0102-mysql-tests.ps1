param([string]$Tests = "CourseWorkflowMysqlTest,CourseSchedulingMysqlTest")
# 仅使用专用临时容器，不读取/修改现有业务库；镜像与 Maven 依赖均离线使用。
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$previousUrl = $env:US0102_MYSQL_URL
$containerId = $null
$maven = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
if (-not $maven) {
    $maven = Get-ChildItem (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $maven) { throw 'Maven not found' }
try {
    $containerName = 'classroom-us0102-test-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $containerId = & docker run --detach --rm --pull=never --name $containerName --publish 127.0.0.1:13316:3306 --env MYSQL_ALLOW_EMPTY_PASSWORD=yes --env MYSQL_DATABASE=us0102_test mysql:8.0.36
    if ($LASTEXITCODE -ne 0) { $containerId = $null; throw 'Cannot start isolated MySQL; ensure Docker is running and port 13316 is unused.' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        'SELECT 1;' | & docker exec -i $containerId mysql '--protocol=TCP' '--host=127.0.0.1' '--user=root' '--database=us0102_test' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Isolated MySQL startup timed out' }
    # 验证新迁移可重复执行；操作目标仍仅为本次临时容器。
    1..2 | ForEach-Object {
        Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'scripts/deploy/mysql/init/04_us03_term_lock.sql') | & docker exec -i $containerId mysql --default-character-set=utf8mb4 -uroot us0102_test
        if ($LASTEXITCODE -ne 0) { throw 'US-03 lock migration failed' }
    }
    $env:US0102_MYSQL_URL = 'jdbc:mysql://127.0.0.1:13316/us0102_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
    & $maven -f (Join-Path $projectRoot 'backend/pom.xml') -o "-Dtest=$Tests" test
    if ($LASTEXITCODE -ne 0) { throw 'US-01/US-02 MySQL regression failed' }
} finally {
    $env:US0102_MYSQL_URL = $previousUrl
    # 仅停止本脚本刚创建并记录 ID 的容器，--rm 自动清理临时数据。
    if ($containerId) { & docker stop $containerId | Out-Null }
}
