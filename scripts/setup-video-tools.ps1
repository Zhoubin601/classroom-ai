param([string]$ArchiveUrl = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot 'runtime/tools/ffmpeg'
$probe = Join-Path $toolRoot 'ffprobe.exe'
if (Test-Path -LiteralPath $probe) { Write-Output $probe; exit 0 }
New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null
$archive = Join-Path $toolRoot 'download.zip'
& curl.exe -f -L --retry 2 --max-time 900 --output $archive $ArchiveUrl
if ($LASTEXITCODE -ne 0) { throw 'Portable media tool download failed' }
Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $toolRoot 'extracted') -Force
$found = Get-ChildItem -LiteralPath (Join-Path $toolRoot 'extracted') -Filter ffprobe.exe -Recurse | Select-Object -First 1
if (-not $found) { throw 'ffprobe was not present in the archive' }
Copy-Item -LiteralPath $found.FullName -Destination $probe
$encoder = Join-Path $found.DirectoryName 'ffmpeg.exe'
if (Test-Path -LiteralPath $encoder) { Copy-Item -LiteralPath $encoder -Destination (Join-Path $toolRoot 'ffmpeg.exe') }
Get-FileHash -LiteralPath $probe -Algorithm SHA256 | Format-List
Write-Output $probe
