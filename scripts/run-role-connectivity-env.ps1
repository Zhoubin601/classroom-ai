param([ValidateSet('Start','Stop')][string]$Action = 'Start', [string]$EvidenceDirectory = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$evidence = if ($EvidenceDirectory) { [IO.Path]::GetFullPath($EvidenceDirectory) } else { Join-Path $root 'docs/role-connectivity-evidence-20261007-v1' }
$statePath = Join-Path $evidence 'environment.json'
if ($Action -eq 'Stop') {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $proc = Get-Process -Id $state.frontendPid -ErrorAction SilentlyContinue
    if ($proc -and $proc.ProcessName -eq 'node') { Stop-Process -Id $proc.Id }
    foreach ($name in $state.containers) {
        if ($name -notmatch '^classroom-exp3-browser-[a-f0-9]{10}(-redis|-backend|-expired)?$') { throw 'Unexpected container name' }
        & docker stop $name | Out-Null
    }
    & docker network rm $state.network | Out-Null
    Write-Output 'Isolated test services stopped; daily services preserved.'
    return
}
if (Test-Path -LiteralPath $statePath) { throw 'Evidence directory already contains a run. Specify a new -EvidenceDirectory to preserve prior evidence.' }
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
if (Get-NetTCPConnection -LocalPort 13318,18081,18082,15173 -State Listen -ErrorAction SilentlyContinue) { throw 'Test ports busy' }
$name = 'classroom-exp3-browser-' + [Guid]::NewGuid().ToString('N').Substring(0,10)
$network = $name + '-net'
$created = @()
$frontend = $null
try {
    & docker network create $network | Out-Null
    & docker run -d --rm --pull=never --name $name --network $network --network-alias mysql -p 127.0.0.1:13318:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=classroom_ai mysql:8.0.36 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start MySQL' }
    $created += $name
    $ready = $false
    for ($i=0; $i -lt 90; $i++) {
        & docker exec -e MYSQL_PWD=root $name mysql -uroot -N -e 'SELECT 1' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'MySQL startup timeout' }
    & docker cp (Join-Path $root 'initialize.sql') "${name}:/tmp/initialize.sql"
    & docker exec -e MYSQL_PWD=root $name mysql -uroot -e 'source /tmp/initialize.sql'
    if ($LASTEXITCODE -ne 0) { throw 'MySQL baseline failed' }
    & docker cp (Join-Path $root 'scripts/deploy/mysql/init/06_exp3_sprint2.sql') "${name}:/tmp/sprint2.sql"
    & docker exec -e MYSQL_PWD=root $name mysql -uroot classroom_ai -e 'source /tmp/sprint2.sql'
    if ($LASTEXITCODE -ne 0) { throw 'MySQL migration failed' }
    & docker exec -e MYSQL_PWD=root $name mysql -uroot classroom_ai -e "UPDATE t_user_account SET password='123456' WHERE username IN ('director','supervisor','guojun','liubo','jiangly');"
    & docker run -d --rm --pull=never --name ($name+'-redis') --network $network --network-alias redis redis:7.2-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Redis startup failed' }
    $created += $name+'-redis'
    $jar = Join-Path $root 'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'
    $uploads = Join-Path $evidence 'uploads'
    New-Item -ItemType Directory -Path $uploads -Force | Out-Null
    foreach ($variant in @(@{Suffix='backend';Port=18081;Minutes=5},@{Suffix='expired';Port=18082;Minutes=0})) {
        $containerName = $name+'-'+$variant.Suffix
        & docker run -d --rm --pull=never --name $containerName --network $network -p ("127.0.0.1:"+$variant.Port+':8080') --mount ("type=bind,source=$jar,target=/app/app.jar,readonly") --mount ("type=bind,source=$uploads,target=/app/runtime/uploads") -e 'SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/classroom_ai?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=root -e SPRING_DATA_REDIS_HOST=redis -e APP_SEED_DEMO=false -e TZ=Asia/Shanghai -e ("CLASSROOM_RESOURCE_PREVIEW_MINUTES="+$variant.Minutes) classroom-ai-demo-classroom-backend:latest | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed' }
        $created += $containerName
    }
    foreach ($port in @(18081,18082)) {
        $ready=$false
        for ($i=0; $i -lt 90; $i++) {
            try { $r=Invoke-WebRequest "http://127.0.0.1:$port/api/v1/auth/csrf" -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { $ready=$true; break } } catch {}
            Start-Sleep -Seconds 1
        }
        if (-not $ready) { throw "Backend $port unavailable" }
    }
    $env:CLASSROOM_API_PROXY='http://127.0.0.1:18081'
    $frontend = Start-Process node -ArgumentList @('node_modules/vite/bin/vite.js','preview','--config','vite.config.ts','--host','127.0.0.1','--port','15173','--strictPort') -WorkingDirectory (Join-Path $root 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence 'frontend.stdout.log') -RedirectStandardError (Join-Path $evidence 'frontend.stderr.log')
    @{network=$network;containers=$created;mysql=$name;frontendPid=$frontend.Id;frontend='http://127.0.0.1:15173';backend='http://127.0.0.1:18081';expired='http://127.0.0.1:18082';commit=(& git rev-parse HEAD)} | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
    Write-Output "Isolated environment ready: $statePath"
} catch {
    if ($frontend) { Stop-Process -Id $frontend.Id -ErrorAction SilentlyContinue }
    foreach ($containerName in $created) { & docker stop $containerName | Out-Null }
    & docker network rm $network | Out-Null
    throw
}
