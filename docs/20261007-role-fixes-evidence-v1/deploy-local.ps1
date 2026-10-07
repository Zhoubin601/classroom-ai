$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskOld=(& docker inspect classroom-backend | ConvertFrom-Json)[0]
if($taskOld.Config.Image -ne 'classroom-ai-demo-classroom-backend'){throw 'Unexpected local backend image'}
$taskMysql=(& docker inspect classroom-mysql | ConvertFrom-Json)[0]
$taskPassword=($taskMysql.Config.Env | Where-Object {$_ -like 'MYSQL_ROOT_PASSWORD=*'}).Substring(20)
function Read-BusinessChecksums {
  $taskTables=& docker exec -e "MYSQL_PWD=$taskPassword" classroom-mysql mysql -uroot -N -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='classroom_ai' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME"
  if($LASTEXITCODE -ne 0){throw 'Cannot read business table names'}
  foreach($taskTable in $taskTables){
    if($taskTable -notmatch '^[a-zA-Z0-9_]+$'){throw 'Invalid table name'}
    $taskRow=& docker exec -e "MYSQL_PWD=$taskPassword" classroom-mysql mysql -uroot -N classroom_ai -e "CHECKSUM TABLE $taskTable; SELECT COUNT(*) FROM $taskTable"
    if($LASTEXITCODE -ne 0){throw 'Cannot checksum business table'}
    [pscustomobject]@{table=$taskTable;checksum=($taskRow[0] -split '\s+')[-1];count=[long]$taskRow[1]}
  }
}
$taskBefore=@(Read-BusinessChecksums)
$taskBefore | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'daily-business-before.json')
$taskBackup='classroom-ai-demo-classroom-backend:before-role-fixes-'+(Get-Date -Format 'yyyyMMdd-HHmmss')
& docker image tag $taskOld.Image $taskBackup
if($LASTEXITCODE -ne 0){throw 'Cannot preserve rollback image'}
$taskMeta=@{startedAt=[DateTimeOffset]::Now.ToString('o');oldImageId=$taskOld.Image;rollbackImage=$taskBackup;jarHash=(Get-FileHash (Join-Path $taskRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar') -Algorithm SHA256).Hash}
$taskMeta | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'deployment.json')
Push-Location $taskRoot
try {
  & docker compose -f scripts/deploy/docker-compose.yml build classroom-backend
  if($LASTEXITCODE -ne 0){throw 'Local image build failed; original backend remains running'}
  & docker compose -f scripts/deploy/docker-compose.yml up -d --no-deps --force-recreate classroom-backend
  if($LASTEXITCODE -ne 0){throw 'Local backend restart failed'}
  $taskReady=$false
  for($taskAttempt=0;$taskAttempt -lt 60;$taskAttempt++){
    try{if((Invoke-WebRequest 'http://127.0.0.1:8080/api/v1/auth/csrf' -TimeoutSec 2).StatusCode -eq 200){$taskReady=$true;break}}catch{}
    Start-Sleep -Seconds 1
  }
  if(-not $taskReady){throw 'Local updated backend not ready'}
  $taskAfter=@(Read-BusinessChecksums)
  $taskAfter | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'daily-business-after.json')
  $taskDiff=Compare-Object ($taskBefore | ConvertTo-Json -Compress) ($taskAfter | ConvertTo-Json -Compress)
  if($taskDiff){throw 'Business table checksums changed; inspect recorded snapshots'}
  $taskMeta.newImageId=(& docker inspect classroom-backend | ConvertFrom-Json)[0].Image
  $taskMeta.businessTablesUnchanged=$true
  $taskMeta.businessTableCount=$taskAfter.Count
  $taskMeta.readyAt=[DateTimeOffset]::Now.ToString('o')
  $taskMeta | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'deployment.json')
  'Local backend updated; business table checksums unchanged; rollback image retained'
} catch {
  # Roll back only the image/owned backend service; never restore or erase database data.
  & docker image tag $taskBackup classroom-ai-demo-classroom-backend
  & docker compose -f scripts/deploy/docker-compose.yml up -d --no-deps --force-recreate classroom-backend
  throw
} finally {Pop-Location}
