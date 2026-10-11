$ErrorActionPreference='Stop'
$testRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$testMaven=Join-Path $testRoot 'backend/.tools/apache-maven-3.9.6/bin/mvn.cmd'
$previousUrl=$env:EXP3_MYSQL_URL
$testContainer=$null
$runResults=@()
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'temporary-module'))) {
    & python (Join-Path $PSScriptRoot 'prepare.py')
    if ($LASTEXITCODE -ne 0) {throw 'Cannot prepare isolated comparison module'}
}
try {
    if (Get-NetTCPConnection -LocalPort 13317 -State Listen -ErrorAction SilentlyContinue) {throw 'Disposable test port 13317 occupied'}
    $testName='classroom-import-check-'+[Guid]::NewGuid().ToString('N').Substring(0,10)
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
    foreach ($variant in @(@{name='original';pom=(Join-Path $testRoot 'backend/pom.xml');reports=(Join-Path $testRoot 'backend/target/surefire-reports')},@{name='temporary-fixed';pom=(Join-Path $PSScriptRoot 'temporary-module/pom.xml');reports=(Join-Path $PSScriptRoot 'temporary-module/target/surefire-reports')})) {
        & docker exec $testContainer mysql -uroot -e 'DROP DATABASE IF EXISTS exp3_test; CREATE DATABASE exp3_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
        if ($LASTEXITCODE -ne 0) {throw 'Disposable database reset failed'}
        Write-Output ('RUN '+$variant.name)
        & $testMaven -f $variant.pom -o '-Dtest=AssociationMysqlTest' test *> (Join-Path $PSScriptRoot ($variant.name+'.log'))
        $testExit=$LASTEXITCODE
        $destination=Join-Path $PSScriptRoot $variant.name
        New-Item -ItemType Directory -Force $destination | Out-Null
        foreach ($report in @('TEST-com.classroom.ai.modules.course.AssociationMysqlTest.xml','com.classroom.ai.modules.course.AssociationMysqlTest.txt')) {
            Copy-Item -LiteralPath (Join-Path $variant.reports $report) -Destination $destination
        }
        $runResults+=@{variant=$variant.name;exitCode=$testExit}
        $runResults | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'run-results.json')
        Get-Content (Join-Path $PSScriptRoot ($variant.name+'.log')) -Tail 8
    }
} finally {
    $env:EXP3_MYSQL_URL=$previousUrl
    if ($testContainer) {
        & docker stop $testContainer | Out-Null
        if ($LASTEXITCODE -ne 0) {throw 'Cannot remove disposable MySQL'}
        @{temporaryMysqlRemoved=$true;at=[DateTimeOffset]::Now.ToString('o')} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'cleanup.json')
        Write-Output 'CLEANUP complete'
    }
}
if ($runResults.Count -ne 2 -or $runResults[0].exitCode -eq 0 -or $runResults[1].exitCode -ne 0) {exit 1}
