$ErrorActionPreference='Stop'
$testState=Get-Content (Join-Path $PSScriptRoot 'environment.json') | ConvertFrom-Json
if($testState.mysql -notmatch '^classroom-exp3-browser-[a-f0-9]{10}$'){throw 'Unexpected test owner'}
$testOwner=$testState.mysql
foreach($testKey in @('backend','expiredBackend','redis','mysql')){
  $testContainer=$testState.$testKey
  if($testContainer -ne $testOwner -and -not $testContainer.StartsWith($testOwner+'-')){throw 'Unowned container'}
  & docker inspect $testContainer *> $null
  if($LASTEXITCODE -eq 0){& docker stop $testContainer | Out-Null}
  & docker inspect $testContainer *> $null
  if($LASTEXITCODE -eq 0){& docker rm --volumes $testContainer | Out-Null}
}
if($testState.network -ne $testOwner+'-net'){throw 'Unowned network'}
& docker network rm $testState.network | Out-Null
if($testState.frontendPid){
  $testProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$($testState.frontendPid)" -ErrorAction SilentlyContinue
  if($testProcess){
    if($testProcess.Name -ne 'node.exe' -or $testProcess.CommandLine -notmatch 'vite.*preview.*15173'){throw 'Frontend PID no longer belongs to this run'}
    Stop-Process -Id $testState.frontendPid
  }
}
$testRemaining=& docker ps -a --format '{{.Names}}'
if($testRemaining | Where-Object {$_ -eq $testOwner -or $_.StartsWith($testOwner+'-')}){throw 'Test container remains'}
@{cleanedAt=[DateTimeOffset]::Now.ToString('o');testContainersRemoved=$true;testNetworkRemoved=$true;testFrontendStopped=$true;originalProjectContainers=$testRemaining | Where-Object {$_ -in @('classroom-backend','classroom-mysql','classroom-redis')}} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'cleanup.json')
'Temporary test containers, volumes, network and frontend removed; original services retained'
