# Downloads the Minecraft files used to check the Polar Client ports for older versions against the real game code:
# the game jar of 1.21.11, 1.21.10, 1.16.5 and 1.8.9, Mojang's name mappings for the first three, and Legacy Fabric's
# names for 1.8.9. Everything goes into tools\mcref next to this script. Nothing is installed or changed anywhere else.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # the progress bar makes downloads very slow in Windows PowerShell
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$out = Join-Path $PSScriptRoot 'mcref'
New-Item -ItemType Directory -Force -Path $out | Out-Null

function Get-File($url, $name) {
  $dest = Join-Path $out $name
  if (Test-Path $dest) { Write-Host "  have $name"; return }
  Write-Host "  downloading $name"
  Invoke-WebRequest -Uri $url -OutFile "$dest.part" -UseBasicParsing
  Move-Item -Force "$dest.part" $dest
}

Write-Host 'Mojang version list...'
$manifest = Invoke-RestMethod -Uri 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' -UseBasicParsing
foreach ($v in @('1.21.11', '1.21.10', '1.16.5', '1.8.9')) {
  Write-Host "Minecraft $v"
  $entry = $manifest.versions | Where-Object { $_.id -eq $v } | Select-Object -First 1
  if (-not $entry) { throw "Minecraft $v is not in Mojang's version list" }
  Get-File $entry.url "$v.json"
  $json = Get-Content (Join-Path $out "$v.json") -Raw | ConvertFrom-Json
  Get-File $json.downloads.client.url "$v-client.jar"
  if ($json.downloads.client_mappings) { Get-File $json.downloads.client_mappings.url "$v-client.txt" }
}

Write-Host 'Game libraries (for checking the code that touches them)'
foreach ($v in @('1.21.11', '1.21.10', '1.16.5', '1.8.9')) {
  $json = Get-Content (Join-Path $out "$v.json") -Raw | ConvertFrom-Json
  $dir = Join-Path $out "libs\$v"
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  foreach ($lib in $json.libraries) {
    $art = $lib.downloads.artifact
    if (-not $art) { continue }
    $name = Split-Path $art.path -Leaf
    if ($name -match 'natives') { continue }
    $dest = Join-Path $dir $name
    if (Test-Path $dest) { continue }
    Write-Host "  $v  $name"
    Invoke-WebRequest -Uri $art.url -OutFile "$dest.part" -UseBasicParsing
    Move-Item -Force "$dest.part" $dest
  }
}

Write-Host 'Legacy Fabric names for 1.8.9'
Get-File 'https://maven.legacyfabric.net/net/legacyfabric/intermediary/1.8.9/intermediary-1.8.9-v2.jar' 'intermediary-1.8.9-v2.jar'
Get-File 'https://maven.legacyfabric.net/net/legacyfabric/yarn/1.8.9+build.604/yarn-1.8.9+build.604-v2.jar' 'yarn-1.8.9+build.604-v2.jar'

Write-Host ''
Write-Host "Done. The files are in $out" -ForegroundColor Green
