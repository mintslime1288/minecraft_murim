[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$RequestFile,

    [int]$PollSeconds = 8,
    [int]$TimeoutMinutes = 30
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:MESHY_API_KEY)) {
    throw 'MESHY_API_KEY 환경변수가 없습니다. 키를 채팅이나 파일에 붙여 넣지 말고 사용자 환경변수로 설정하세요.'
}

$requestDefinition = Get-Content -LiteralPath $RequestFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($requestDefinition.asset_id) -or [string]::IsNullOrWhiteSpace($requestDefinition.endpoint)) {
    throw '요청 파일에는 asset_id와 endpoint가 필요합니다.'
}
if ($requestDefinition.endpoint -notin @('image-to-3d', 'text-to-3d')) {
    throw 'endpoint는 image-to-3d 또는 text-to-3d만 사용할 수 있습니다.'
}
if ($null -eq $requestDefinition.request) {
    throw '요청 파일에는 Meshy API에 전달할 request 객체가 필요합니다.'
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$safeId = $requestDefinition.asset_id -replace '[^A-Za-z0-9_-]', '_'
if ($safeId -ne $requestDefinition.asset_id) {
    throw 'asset_id는 영문, 숫자, 밑줄, 하이픈만 사용하세요.'
}
$destination = Join-Path $repositoryRoot "assets-source\meshy\$safeId"
New-Item -ItemType Directory -Path $destination -Force | Out-Null

# Image-to-3D accepts either a public URL or a data URI.  Keep local reference
# art inside the repository and convert it only in memory so it is never uploaded
# to a third-party host just to make an API request.
$localReferenceImage = $null
if ($requestDefinition.endpoint -eq 'image-to-3d' -and -not [string]::IsNullOrWhiteSpace($requestDefinition.image_file)) {
    $candidate = [string]$requestDefinition.image_file
    if (-not [System.IO.Path]::IsPathRooted($candidate)) {
        $candidate = Join-Path $repositoryRoot $candidate
    }
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "image_file을 찾을 수 없습니다: $candidate"
    }
    $extension = [System.IO.Path]::GetExtension($candidate).ToLowerInvariant()
    $mimeType = switch ($extension) {
        '.png' { 'image/png'; break }
        '.jpg' { 'image/jpeg'; break }
        '.jpeg' { 'image/jpeg'; break }
        default { throw "image_file은 PNG 또는 JPEG여야 합니다: $candidate" }
    }
    $localReferenceImage = (Resolve-Path -LiteralPath $candidate).Path
}

$headers = @{ Authorization = "Bearer $($env:MESHY_API_KEY)"; 'Content-Type' = 'application/json' }
$baseUri = if ($requestDefinition.endpoint -eq 'text-to-3d') {
    'https://api.meshy.ai/openapi/v2/text-to-3d'
} else {
    'https://api.meshy.ai/openapi/v1/image-to-3d'
}
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)

function Start-MeshyTask([object]$Body) {
    $created = Invoke-RestMethod -Method Post -Uri $baseUri -Headers $headers -Body ($Body | ConvertTo-Json -Depth 12 -Compress)
    if ([string]::IsNullOrWhiteSpace($created.result)) { throw 'Meshy 생성 응답에 task ID(result)가 없습니다.' }
    return $created.result
}

function Wait-MeshyTask([string]$TaskId) {
    do {
        Start-Sleep -Seconds $PollSeconds
        $current = Invoke-RestMethod -Method Get -Uri "$baseUri/$TaskId" -Headers $headers
        Write-Host "[Meshy] 상태: $($current.status) / 진행률: $($current.progress)%"
        if ($current.status -eq 'FAILED') { throw "Meshy 생성 실패: $($current.task_error.message)" }
        if ((Get-Date) -gt $deadline) { throw "Meshy 작업 시간이 $TimeoutMinutes 분을 넘었습니다. task ID: $TaskId" }
    } while ($current.status -ne 'SUCCEEDED')
    return $current
}

Write-Host "[Meshy] $safeId 생성 요청을 보냅니다."
$previewRequest = $requestDefinition.request | Select-Object *
if ($requestDefinition.endpoint -eq 'text-to-3d') {
    $previewRequest | Add-Member -NotePropertyName mode -NotePropertyValue 'preview' -Force
}
if ($null -ne $localReferenceImage) {
    $base64Image = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($localReferenceImage))
    $extension = [System.IO.Path]::GetExtension($localReferenceImage).ToLowerInvariant()
    $mimeType = if ($extension -eq '.png') { 'image/png' } else { 'image/jpeg' }
    $previewRequest | Add-Member -NotePropertyName image_url -NotePropertyValue "data:$mimeType;base64,$base64Image" -Force
}
$taskId = Start-MeshyTask $previewRequest
Write-Host "[Meshy] task ID: $taskId"
$task = Wait-MeshyTask $taskId

if ($requestDefinition.endpoint -eq 'text-to-3d' -and $null -ne $requestDefinition.refine) {
    $refineRequest = [ordered]@{ mode = 'refine'; preview_task_id = $taskId }
    foreach ($property in $requestDefinition.refine.PSObject.Properties) {
        $refineRequest[$property.Name] = $property.Value
    }
    Write-Host '[Meshy] 미리보기 메시가 완료되었습니다. PBR 텍스처 정제를 시작합니다.'
    $taskId = Start-MeshyTask $refineRequest
    Write-Host "[Meshy] task ID: $taskId"
    $task = Wait-MeshyTask $taskId
}

if ([string]::IsNullOrWhiteSpace($task.model_urls.glb)) {
    throw '작업은 성공했지만 GLB 다운로드 URL이 없습니다. Meshy 작업 화면에서 task ID를 확인하세요.'
}

$glbPath = Join-Path $destination 'model.glb'
Invoke-WebRequest -Uri $task.model_urls.glb -OutFile $glbPath
if ($task.thumbnail_url) {
    Invoke-WebRequest -Uri $task.thumbnail_url -OutFile (Join-Path $destination 'preview.png')
}

$metadata = [ordered]@{
    asset_id = $safeId
    meshy_task_id = $taskId
    meshy_endpoint = $requestDefinition.endpoint
    source_tool = 'Meshy'
    downloaded_at_utc = (Get-Date).ToUniversalTime().ToString('o')
    original_request_file = (Resolve-Path $RequestFile).Path
    reference_image_file = $localReferenceImage
    reference_image_sha256 = if ($localReferenceImage) { (Get-FileHash -LiteralPath $localReferenceImage -Algorithm SHA256).Hash } else { $null }
    license_status = 'REVIEW_REQUIRED'
    model_file = 'model.glb'
    thumbnail_file = if ($task.thumbnail_url) { 'preview.png' } else { $null }
    sha256 = (Get-FileHash -LiteralPath $glbPath -Algorithm SHA256).Hash
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $destination 'asset-metadata.json') -Encoding UTF8
Write-Host "[Meshy] 완료: $glbPath"
Write-Host "[Meshy] 다음 단계: asset-metadata.json의 license_status를 실제 요금제·라이선스에 맞게 갱신하고, Blender에서 최적화한 뒤 전용 클라이언트 렌더러에 등록하세요."
