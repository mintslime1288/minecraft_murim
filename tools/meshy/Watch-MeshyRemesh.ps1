[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string]$TaskId,
    [Parameter(Mandatory = $true)] [string]$Destination,
    [int]$TimeoutMinutes = 30
)

$ErrorActionPreference = 'Stop'
$key = [Environment]::GetEnvironmentVariable('MESHY_API_KEY', 'User')
if ([string]::IsNullOrWhiteSpace($key)) { throw 'MESHY_API_KEY 사용자 환경변수가 없습니다.' }
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$headers = @{ Authorization = "Bearer $key" }
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)

do {
    $task = Invoke-RestMethod -Method Get -Uri "https://api.meshy.ai/openapi/v1/remesh/$TaskId" -Headers $headers
    "$(Get-Date -Format o) $($task.status) $($task.progress)%" | Add-Content -LiteralPath (Join-Path $Destination 'meshy-remesh-watch.log') -Encoding utf8
    if ($task.status -eq 'SUCCEEDED') {
        Invoke-WebRequest -Uri $task.model_urls.glb -OutFile (Join-Path $Destination 'model-optimized.glb')
        if ($task.thumbnail_url) { Invoke-WebRequest -Uri $task.thumbnail_url -OutFile (Join-Path $Destination 'preview-optimized.png') }
        if ($task.alpha_thumbnail_url) { Invoke-WebRequest -Uri $task.alpha_thumbnail_url -OutFile (Join-Path $Destination 'preview-optimized-alpha.png') }
        exit 0
    }
    if ($task.status -eq 'FAILED' -or (Get-Date) -gt $deadline) { exit 1 }
    Start-Sleep -Seconds 15
} while ($true)
