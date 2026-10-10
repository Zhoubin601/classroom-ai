$ErrorActionPreference='Stop'
$testRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$testMaven=Join-Path $testRoot 'backend/.tools/apache-maven-3.9.6/bin/mvn.cmd'
$previousUrl=$env:EXP3_MYSQL_URL
$testContainer=$null
$runResults=@()
try {
    if (Get-NetTCPConnection -LocalPort 13317 -State Listen -ErrorAction SilentlyContinue) {throw 'Disposable test port 13317 occupied'}
    $testName='classroom-import-fix-'+[Guid]::NewGuid().ToString('N').Substring(0,10)
    $testContainer=& docker run -d --rm --pull=never --name $testName -p 127.0.0.1:13317:3306 --env MYSQL_ALLOW_EMPTY_PASSWORD=yes --env MYSQL_DATABASE=exp3_test mysql:8.0.36
    if ($LASTEXITCODE -ne 0) {$testContainer=$null;throw 'Cannot start disposable MySQL'}
    $ready=$false
    for ($i=0;$i -lt 60;$i++) {
        & docker exec $testContainer mysql --protocol=TCP -h 127.0.0.1 -uroot -N -e 'SELECT 1' *> $null
        if ($LASTEXITCODE -eq 0) {$ready=$true;break}
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {throw 'Disposable MySQL not ready'}
    $env:EXP3_MYSQL_URL='jdbc:mysql://127.0.0.1:13317/exp3_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
    Write-Output 'RUN AssociationMysqlTest against disposable MySQL'
    & $testMaven -f (Join-Path $testRoot 'backend/pom.xml') -o '-Dtest=AssociationMysqlTest' test *> (Join-Path $PSScriptRoot 'mysql-targeted.log')
    $testExit=$LASTEXITCODE
    $destination=Join-Path $PSScriptRoot 'mysql-targeted'
    New-Item -ItemType Directory -Force $destination | Out-Null
    foreach ($report in @('TEST-com.classroom.ai.modules.course.AssociationMysqlTest.xml','com.classroom.ai.modules.course.AssociationMysqlTest.txt')) {
        Copy-Item -LiteralPath (Join-Path $testRoot ('backend/target/surefire-reports/'+$report)) -Destination $destination
    }
    $runResults+=@{variant='mysql-targeted';exitCode=$testExit}
    Get-Content (Join-Path $PSScriptRoot 'mysql-targeted.log') -Tail 8
    if ($testExit -ne 0) {throw 'AssociationMysqlTest failed'}

    # Default regression explicitly excludes conditional external integration environments.
    $conditionalNames=@('EXP3_MYSQL_URL','US0102_MYSQL_URL','US0203_BROWSER')
    $savedConditional=@{}
    try {
        foreach ($name in $conditionalNames) {
            $savedConditional[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
            [Environment]::SetEnvironmentVariable($name,$null,'Process')
        }
        Write-Output 'RUN default backend regression'
        & $testMaven -f (Join-Path $testRoot 'backend/pom.xml') -o test *> (Join-Path $PSScriptRoot 'backend-regression.log')
        $regressionExit=$LASTEXITCODE
        $destination=Join-Path $PSScriptRoot 'backend-regression'
        New-Item -ItemType Directory -Force $destination | Out-Null
        Get-ChildItem -LiteralPath (Join-Path $testRoot 'backend/target/surefire-reports') -Filter 'TEST-*.xml' | Copy-Item -Destination $destination
        $runResults+=@{variant='backend-regression';exitCode=$regressionExit}
        Get-Content (Join-Path $PSScriptRoot 'backend-regression.log') -Tail 8
        if ($regressionExit -ne 0) {throw 'Backend regression failed'}
    } finally {
        foreach ($name in $conditionalNames) {[Environment]::SetEnvironmentVariable($name,$savedConditional[$name],'Process')}
    }
} finally {
    $env:EXP3_MYSQL_URL=$previousUrl
    $runResults | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'run-results.json')
    if ($testContainer) {
        & docker stop $testContainer | Out-Null
        if ($LASTEXITCODE -ne 0) {throw 'Cannot remove disposable MySQL'}
        @{temporaryMysqlRemoved=$true;at=[DateTimeOffset]::Now.ToString('o')} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'cleanup.json')
        Write-Output 'CLEANUP complete'
    }
}
