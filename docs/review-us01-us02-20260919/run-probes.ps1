$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$reportPath = Join-Path $projectRoot 'backend/target/surefire-reports/TEST-com.classroom.ai.modules.course.CourseContentRevisionTest.xml'
if (-not (Test-Path -LiteralPath $reportPath)) { throw 'Run scripts/run-tests.ps1 -Offline first.' }
$report = [xml](Get-Content -LiteralPath $reportPath -Raw)
$reviewClasspath = ($report.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
& javac -proc:none -encoding UTF-8 -cp $reviewClasspath -d $PSScriptRoot (Join-Path $PSScriptRoot 'ReviewProbes.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java '-Dfile.encoding=UTF-8' -cp "$PSScriptRoot;$reviewClasspath" ReviewProbes
exit $LASTEXITCODE
