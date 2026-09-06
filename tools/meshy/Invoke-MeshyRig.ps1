[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$InputTaskId,
    [Parameter(Mandatory = $true)][string]$AssetId,
    [int]$PollSeconds = 8,
    [int]$TimeoutMinutes = 20
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:MESHY_API_KEY)) { throw 'MESHY_API_KEY 환경변수가 없습니다.' }
if ($AssetId -notmatch '^[A-Za-z0-9_-]+$') { throw 'AssetId 형식이 올바르지 않습니다.' }
$headers = @{ Authorization = "Bearer $($env:MESHY_API_KEY)"; 'Content-Type' = 'application/json' }
$baseUri = 'https://api.meshy.ai/openapi/v1/rigging'
$body = @{ input_task_id = $InputTaskId; height_meters = 1.8 } | ConvertTo-Json -Compress
$created = Invoke-RestMethod -Method Post -Uri $baseUri -Headers $headers -Body $body
if ([string]::IsNullOrWhiteSpace($created.result)) { throw 'Rigging task ID가 없습니다.' }
$taskId = [string]$created.result
Write-Host "[Meshy] rigging task ID: $taskId"
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
do {
    Start-Sleep -Seconds $PollSeconds
    $task = Invoke-RestMethod -Method Get -Uri "$baseUri/$taskId" -Headers $headers
    Write-Host "[Meshy] rigging: $($task.status) / $($task.progress)%"
    if ($task.status -eq 'FAILED') { throw "Meshy rigging 실패: $($task.task_error.message)" }
    if ((Get-Date) -gt $deadline) { throw "Meshy rigging 시간 초과: $taskId" }
} while ($task.status -ne 'SUCCEEDED')
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$destination = Join-Path $root "assets-source\meshy\$AssetId\rigged"
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$downloads = [ordered]@{
    'character.glb' = $task.result.rigged_character_glb_url
    'walking.glb' = $task.result.basic_animations.walking_glb_url
    'walking-armature.glb' = $task.result.basic_animations.walking_armature_glb_url
    'running.glb' = $task.result.basic_animations.running_glb_url
}
foreach ($entry in $downloads.GetEnumerator()) {
    if (-not [string]::IsNullOrWhiteSpace($entry.Value)) {
        Invoke-WebRequest -Uri $entry.Value -OutFile (Join-Path $destination $entry.Key)
    }
}
$files = foreach ($entry in $downloads.GetEnumerator()) {
    $path = Join-Path $destination $entry.Key
    if (Test-Path -LiteralPath $path) {
        [ordered]@{ name = $entry.Key; size_bytes = (Get-Item -LiteralPath $path).Length; sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
    }
}
[ordered]@{
    asset_id = $AssetId
    source_tool = 'Meshy Rigging API'
    input_task_id = $InputTaskId
    rigging_task_id = $taskId
    consumed_credits = $task.consumed_credits
    downloaded_at_utc = (Get-Date).ToUniversalTime().ToString('o')
    license_status = 'REVIEW_REQUIRED'
    files = @($files)
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $destination 'rig-metadata.json') -Encoding UTF8
Write-Host "[Meshy] rigging complete: $destination"
