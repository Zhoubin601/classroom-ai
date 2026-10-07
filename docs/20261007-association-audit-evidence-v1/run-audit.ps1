param([string]$JavaBin = 'C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot/bin')
$ErrorActionPreference = 'Stop'
$auditRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$auditBackend = Join-Path $auditRepoRoot 'backend'
$auditMaven = Join-Path $auditBackend '.tools/apache-maven-3.9.6/bin/mvn.cmd'
Push-Location $auditBackend
try {
    & $auditMaven -B -q '-DskipTests' test-compile dependency:build-classpath '-Dmdep.outputFile=../docs/runtime/association-audit-classpath.txt' '-DincludeScope=test'
    if ($LASTEXITCODE -ne 0) { throw 'Audit classpath preparation failed' }
} finally { Pop-Location }
$auditClasspath = (Get-Content -LiteralPath (Join-Path $auditRepoRoot 'docs/runtime/association-audit-classpath.txt') -Raw).Trim() + ';' + (Join-Path $auditBackend 'target/classes') + ';' + (Join-Path $auditBackend 'target/test-classes')
$auditClasses = Join-Path $auditRepoRoot 'docs/runtime/association-audit-classes'
New-Item -ItemType Directory -Path $auditClasses -Force | Out-Null
& (Join-Path $JavaBin 'javac.exe') -proc:none -encoding UTF-8 -cp $auditClasspath -d $auditClasses (Join-Path $PSScriptRoot 'AssociationAudit.java')
if ($LASTEXITCODE -ne 0) { throw 'Audit harness compilation failed' }
& (Join-Path $JavaBin 'java.exe') '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp ($auditClasses + ';' + $auditClasspath) AssociationAudit (Join-Path $PSScriptRoot 'diagnostics.json')
if ($LASTEXITCODE -ne 0) { throw 'Some probes were inconclusive; inspect diagnostics.json' }
& node (Join-Path $PSScriptRoot 'readonly-data-audit.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Live SELECT audit failed' }
