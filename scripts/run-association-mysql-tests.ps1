param([string]$Tests='AssociationMysqlTest,Sprint2MysqlTest,MajorDepartmentMysqlTest')
$ErrorActionPreference='Stop'
$associationRepoRoot=Split-Path $PSScriptRoot -Parent
$associationPreviousUrl=$env:EXP3_MYSQL_URL
$associationContainer=$null
try {
    $associationName='classroom-association-test-'+[Guid]::NewGuid().ToString('N').Substring(0,10)
    $associationContainer=& docker run --detach --rm --pull=never --name $associationName --publish 127.0.0.1:13317:3306 --env MYSQL_ALLOW_EMPTY_PASSWORD=yes --env MYSQL_DATABASE=exp3_test mysql:8.0.36
    if($LASTEXITCODE -ne 0) { $associationContainer=$null;throw 'Cannot start isolated MySQL' }
    $associationReady=$false
    for($associationAttempt=0;$associationAttempt -lt 70;$associationAttempt++) {
        $associationReady=& node -e 'const cp=require("node:child_process"); const r=cp.spawnSync("docker",["exec",process.argv[1],"mysql","--protocol=TCP","--host=127.0.0.1","-uroot","exp3_test","-e","SELECT 1;"]); console.log(r.status===0?"yes":"no");' $associationContainer
        if($associationReady -eq 'yes') { break }
        Start-Sleep -Seconds 1
    }
    if($associationReady -ne 'yes') { throw 'Isolated MySQL startup timed out' }
    $env:EXP3_MYSQL_URL='jdbc:mysql://127.0.0.1:13317/exp3_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
    & (Join-Path $associationRepoRoot 'backend/.tools/apache-maven-3.9.6/bin/mvn.cmd') -f (Join-Path $associationRepoRoot 'backend/pom.xml') -B -q "-Dtest=$Tests" test
    if($LASTEXITCODE -ne 0) { throw 'Association MySQL integration tests failed' }
} finally {
    $env:EXP3_MYSQL_URL=$associationPreviousUrl
    if($associationContainer) { & docker stop $associationContainer | Out-Null }
}
