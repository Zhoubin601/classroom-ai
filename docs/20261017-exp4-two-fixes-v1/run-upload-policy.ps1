param([string]$EvidenceDir)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (-not $EvidenceDir) { $EvidenceDir = Join-Path $PSScriptRoot 'policy-browser' }
$evidence = [IO.Path]::GetFullPath($EvidenceDir)
if ((Test-Path -LiteralPath $evidence) -and (Get-ChildItem -LiteralPath $evidence -Force)) { throw 'Evidence directory must be new or empty; preserve earlier test attempts' }
if (-not $env:RESOURCE_POLICY_TEST_PASSWORD -or -not $env:RESOURCE_POLICY_DB_PASSWORD) { throw 'Set disposable test and database passwords in process environment first' }
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$name = 'classroom-policy-fix-' + [guid]::NewGuid().ToString('N').Substring(0,10)
$runtimeRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'runtime/resource-policy-browser'))
$runDir = Join-Path $runtimeRoot $name
if (Test-Path -LiteralPath $runDir) { throw 'Probe runtime directory must be new' }
New-Item -ItemType Directory -Path (Join-Path $runDir 'uploads') -Force | Out-Null
$containers = New-Object System.Collections.Generic.List[string]
$processes = New-Object System.Collections.Generic.List[object]
$metadata = [ordered]@{ startedAt=(Get-Date).ToString('o'); ports=@(13320,16320,18090,18091,15180,15181); seedDemo=$false; databaseInitialization=@('initialize.sql','scripts/deploy/mysql/init/06_exp3_sprint2.sql'); backendCommands=@{} }
$saved = @{}
$envNames = @('PLAYWRIGHT_MODULE','EXP3_CHROMIUM_PATH','CLASSROOM_API_PROXY','RESOURCE_POLICY_RUNTIME','RESOURCE_POLICY_EVIDENCE_DIR','RESOURCE_POLICY_HIGH_FRONTEND','RESOURCE_POLICY_LOW_FRONTEND','RESOURCE_POLICY_HIGH_BACKEND','RESOURCE_POLICY_LOW_BACKEND')
foreach ($key in $envNames) { $saved[$key] = [Environment]::GetEnvironmentVariable($key,'Process') }
function Native([string[]]$Arguments) {
  $prior = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  try { $output = & docker @Arguments 2>&1; $code = $LASTEXITCODE } finally { $ErrorActionPreference = $prior }
  if ($code -ne 0) { throw ('Docker operation failed: ' + $Arguments[0] + ', code=' + $code) }
  return $output
}
function WaitBackend([int]$Port) {
  for ($i=0; $i -lt 120; $i++) { try { Invoke-WebRequest ('http://127.0.0.1:'+$Port+'/api/v1/auth/csrf') -UseBasicParsing -TimeoutSec 2 | Out-Null; return } catch { Start-Sleep -Seconds 1 } }
  throw ('Backend startup timed out: '+$Port)
}
function StartBackend([int]$Port,[string]$Label,[string[]]$Flags) {
  $id = (Native (@('run','-d','--rm','--pull=never','--name',($name+'-'+$Label),'-p',('127.0.0.1:'+$Port+':8080'),'--mount',('type=bind,source='+$script:jar+',target=/audit.jar,readonly'),'--mount',('type=bind,source='+(Join-Path $runDir 'uploads')+',target=/audit/uploads'),'-e','SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:13320/classroom_ai?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai','-e','SPRING_DATASOURCE_USERNAME=root','-e',('SPRING_DATASOURCE_PASSWORD='+$env:RESOURCE_POLICY_DB_PASSWORD),'-e','SPRING_DATA_REDIS_HOST=host.docker.internal','-e','SPRING_DATA_REDIS_PORT=16320','-e','APP_SEED_DEMO=false','-e','TZ=Asia/Shanghai','-e','CLASSROOM_UPLOAD_DIR=/audit/uploads/faces','-e','CLASSROOM_FFPROBE=/usr/bin/ffprobe','--entrypoint','java','classroom-ai-demo-classroom-backend:latest','-Duser.timezone=Asia/Shanghai','-jar','/audit.jar','--classroom.supervision.feedback-delay-hours=0')+$Flags) | Out-String).Trim()
  $containers.Add($id)
  $metadata.backendCommands[$Label] = (Native @('inspect','--format','{{json .Config.Cmd}}',$id) | Out-String).Trim()
  $metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'runtime-metadata.json') -Encoding utf8
  WaitBackend $Port
}
function StartFrontend([int]$Port,[int]$BackendPort,[string]$Label) {
  $env:CLASSROOM_API_PROXY = 'http://127.0.0.1:'+$BackendPort
  $process = Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js','preview','--host','127.0.0.1','--port',("$Port"),'--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence ($Label+'.stdout.log')) -RedirectStandardError (Join-Path $evidence ($Label+'.stderr.log')); $processes.Add($process)
  for ($i=0; $i -lt 30; $i++) { try { Invoke-WebRequest ('http://127.0.0.1:'+$Port) -UseBasicParsing -TimeoutSec 2 | Out-Null; return } catch { Start-Sleep -Seconds 1 } }
  throw ('Frontend startup timed out: '+$Port)
}
try {
  if (Get-NetTCPConnection -LocalPort 13320,16320,18090,18091,15180,15181 -State Listen -ErrorAction SilentlyContinue) { throw 'Dedicated policy test ports are occupied' }
  $script:jar = Join-Path $projectRoot 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
  if (-not (Test-Path -LiteralPath $script:jar) -or -not (Test-Path -LiteralPath (Join-Path $projectRoot 'frontend/dist/index.html'))) { throw 'Build current backend and frontend before running' }
  if (-not $env:PLAYWRIGHT_MODULE) { $env:PLAYWRIGHT_MODULE = Get-ChildItem (Join-Path $env:USERPROFILE '.vscode/extensions') -Directory -Filter 'vscjava.migrate-java-to-azure-*' | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'node_modules/playwright' } | Where-Object { Test-Path $_ } | Select-Object -First 1 }
  if (-not $env:EXP3_CHROMIUM_PATH) { $env:EXP3_CHROMIUM_PATH = Join-Path $env:ProgramFiles 'Google/Chrome/Application/chrome.exe' }
  if (-not (Test-Path -LiteralPath $env:EXP3_CHROMIUM_PATH)) { throw 'Installed real Google Chrome is required' }
  $metadata.jarSha256 = (Get-FileHash -LiteralPath $script:jar -Algorithm SHA256).Hash
  $metadata.frontendIndexSha256 = (Get-FileHash -LiteralPath (Join-Path $projectRoot 'frontend/dist/index.html') -Algorithm SHA256).Hash
  $metadata.gitHead = (& git -C $projectRoot rev-parse HEAD | Out-String).Trim()
  $metadata.backendRuntimeImage = (Native @('image','inspect','classroom-ai-demo-classroom-backend:latest','--format','{{.Id}}') | Out-String).Trim()
  $mysql = (Native @('run','-d','--rm','--pull=never','--name',$name,'-p','127.0.0.1:13320:3306','-e',('MYSQL_ROOT_PASSWORD='+$env:RESOURCE_POLICY_DB_PASSWORD),'-e','TZ=Asia/Shanghai','mysql:8.0.36','--default-time-zone=+08:00') | Out-String).Trim(); $containers.Add($mysql)
  $ready = $false
  for ($i=0; $i -lt 90; $i++) { try { Native @('exec','-e',('MYSQL_PWD='+$env:RESOURCE_POLICY_DB_PASSWORD),$mysql,'mysql','--protocol=TCP','-h','127.0.0.1','-uroot','-N','-e','SELECT 1') | Out-Null; $ready=$true; break } catch { Start-Sleep -Seconds 1 } }
  if (-not $ready) { throw 'Isolated policy MySQL startup timed out' }
  foreach ($item in @(@('initialize.sql','/tmp/initialize.sql'),@('scripts/deploy/mysql/init/06_exp3_sprint2.sql','/tmp/sprint2.sql'))) { Native @('cp',(Join-Path $projectRoot $item[0]),($mysql+':'+$item[1])) | Out-Null }
  Native @('exec','-e',('MYSQL_PWD='+$env:RESOURCE_POLICY_DB_PASSWORD),$mysql,'mysql','-uroot','-e','source /tmp/initialize.sql') | Out-File -LiteralPath (Join-Path $evidence 'initialize.log') -Encoding utf8
  Native @('exec','-e',('MYSQL_PWD='+$env:RESOURCE_POLICY_DB_PASSWORD),$mysql,'mysql','-uroot','classroom_ai','-e','source /tmp/sprint2.sql') | Out-File -LiteralPath (Join-Path $evidence 'migration.log') -Encoding utf8
  Native @('exec','-e',('MYSQL_PWD='+$env:RESOURCE_POLICY_DB_PASSWORD),$mysql,'mysql','-uroot','classroom_ai','-e',("UPDATE t_user_account SET password='"+$env:RESOURCE_POLICY_TEST_PASSWORD+"' WHERE username IN ('guojun','liubo','supervisor','director');")) | Out-Null
  $redis = (Native @('run','-d','--rm','--pull=never','--name',($name+'-redis'),'-p','127.0.0.1:16320:6379','redis:7.2-alpine') | Out-String).Trim(); $containers.Add($redis)
  StartBackend 18090 'high' @('--classroom.resource.max-file-bytes=157286400')
  $ready = $false
  for ($i=0; $i -lt 60; $i++) { try { $count=Native @('exec','-e',('MYSQL_PWD='+$env:RESOURCE_POLICY_DB_PASSWORD),$mysql,'mysql','-uroot','classroom_ai','-N','-e','SELECT COUNT(*) FROM s3_template_version'); if ([int]($count | Out-String).Trim() -gt 0) { $ready=$true; break } } catch {}; Start-Sleep -Seconds 1 }
  if (-not $ready) { throw 'Template migration did not complete' }
  StartBackend 18091 'low' @('--classroom.resource.max-file-bytes=1024','--classroom.resource.allowed-extensions=pdf')
  StartFrontend 15180 18090 'frontend-high'
  StartFrontend 15181 18091 'frontend-low'
  $env:RESOURCE_POLICY_RUNTIME = $runDir
  $env:RESOURCE_POLICY_EVIDENCE_DIR = $evidence
  $env:RESOURCE_POLICY_HIGH_FRONTEND = 'http://127.0.0.1:15180'; $env:RESOURCE_POLICY_LOW_FRONTEND = 'http://127.0.0.1:15181'
  $env:RESOURCE_POLICY_HIGH_BACKEND = 'http://127.0.0.1:18090'; $env:RESOURCE_POLICY_LOW_BACKEND = 'http://127.0.0.1:18091'
  $prior = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  try { $testOutput = & node (Join-Path $projectRoot 'scripts/tests/resource-upload-policy-browser.cjs') 2>&1; $testExit = $LASTEXITCODE } finally { $ErrorActionPreference = $prior }
  $testOutput | Out-File -LiteralPath (Join-Path $evidence 'browser.log') -Encoding utf8
  $testOutput | ForEach-Object { Write-Output $_ }
  if ($testExit -ne 0) { throw 'Policy browser regression failed; preserve evidence and use a new directory for any retry' }
} finally {
  foreach ($id in $containers) { try { Native @('logs',$id) | Out-File -LiteralPath (Join-Path $evidence ($id.Substring(0,12)+'.log')) -Encoding utf8 } catch {} }
  foreach ($process in $processes) { if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue } }
  foreach ($id in $containers) { try { Native @('stop',$id) | Out-Null } catch {} }
  $resolvedRunDir = [IO.Path]::GetFullPath($runDir)
  if (-not $resolvedRunDir.StartsWith($runtimeRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Cleanup target outside dedicated policy runtime directory' }
  if (Test-Path -LiteralPath $resolvedRunDir) { Remove-Item -LiteralPath $resolvedRunDir -Recurse -Force }
  foreach ($file in Get-ChildItem -LiteralPath $evidence -Recurse -File | Where-Object Extension -in '.log','.json','.txt','.html') {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    if ($content) {
      $clean = [regex]::Replace($content,'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[REDACTED_TEST_TOKEN]')
      $clean = [regex]::Replace($clean,'(?im)(x-csrf-token:\s*)[^\r\n<]+','$1[REDACTED_TEST_CSRF]')
      $clean = [regex]::Replace($clean,'(?im)(Using generated security password:\s*)[^\r\n]+','$1[REDACTED_TEST_SECURITY_PASSWORD]')
      if ($clean -ne $content) { Set-Content -LiteralPath $file.FullName -Value $clean -Encoding utf8 }
    }
  }
  $listeners = @(Get-NetTCPConnection -LocalPort 13320,16320,18090,18091,15180,15181 -State Listen -ErrorAction SilentlyContinue)
  @{ completedAt=(Get-Date).ToString('o'); containerCount=$containers.Count; stoppedDedicatedContainers=$true; stoppedFrontends=$true; removedGeneratedRuntime=( -not (Test-Path -LiteralPath $runDir)); dedicatedListeningPortCount=$listeners.Count } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'cleanup.json') -Encoding utf8
  foreach ($key in $envNames) { [Environment]::SetEnvironmentVariable($key,$saved[$key],'Process') }
}
