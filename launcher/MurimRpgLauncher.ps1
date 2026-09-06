<#
  MurimRPG Launcher development helper. It never asks for or stores a Minecraft account password.
  A signed EXE launcher is a separate release task; do not publish this development helper as one.
#>
param([ValidateSet(2048,4096,6144)][int]$RamMiB = 4096)

$minecraftDirectory = Join-Path $env:APPDATA '.minecraft'
$profilePath = Join-Path $minecraftDirectory 'launcher_profiles.json'
if (-not (Test-Path -LiteralPath $profilePath)) { throw "Minecraft Java Edition Launcher 설정을 찾지 못했습니다: $profilePath" }
$profiles = Get-Content -Raw -LiteralPath $profilePath | ConvertFrom-Json
$profile = $profiles.profiles.'fabric-loader-26.2'
if ($null -eq $profile -or $profile.lastVersionId -ne 'fabric-loader-0.19.3-26.2') {
    throw 'Fabric 프로필 fabric-loader-26.2가 없습니다. Fabric Installer로 Minecraft 26.2 / Loader 0.19.3를 먼저 설치하세요.'
}
$mod = Join-Path $minecraftDirectory 'mods\MurimRPG-Fabric-0.2.0.jar'
if (-not (Test-Path -LiteralPath $mod)) { throw "MurimRPG 클라이언트 모드가 없습니다: $mod" }
Write-Host "Minecraft Java Edition Launcher를 엽니다. Java Edition에서 fabric-loader-26.2 프로필을 선택하고 플레이하세요. (권장 RAM: $RamMiB MiB)"
Start-Process 'shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft'
