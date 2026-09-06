param(
    [string]$DocumentId = "1VdOpBZzkzl1_TM9IR1YbZ72j2BZ8pqTNR6fXGUPxrzg"
)

$ErrorActionPreference = "Stop"
$exportUrl = "https://docs.google.com/document/d/$DocumentId/export?format=txt"
$stateDirectory = Join-Path $env:LOCALAPPDATA "MurimRPG\codex"
$snapshotPath = Join-Path $stateDirectory "yoonjaeyoung-idea-board.txt"
$hashPath = Join-Path $stateDirectory "yoonjaeyoung-idea-board.sha256"

try {
    $response = Invoke-WebRequest -Uri $exportUrl -MaximumRedirection 5
    $current = $response.Content.TrimStart([char]0xFEFF) -replace "`r`n", "`n"
} catch {
    Write-Output "IDEA_BOARD_STATUS=ACCESS_FAILED"
    Write-Output ("IDEA_BOARD_ERROR=" + $_.Exception.Message)
    exit 2
}

$currentBytes = [Text.Encoding]::UTF8.GetBytes($current)
$currentHash = [Convert]::ToHexString(
    [Security.Cryptography.SHA256]::HashData($currentBytes)
).ToLowerInvariant()

New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null

if (-not (Test-Path -LiteralPath $snapshotPath)) {
    [IO.File]::WriteAllText($snapshotPath, $current, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($hashPath, $currentHash, [Text.UTF8Encoding]::new($false))
    Write-Output "IDEA_BOARD_STATUS=BASELINE_CREATED"
    Write-Output ("IDEA_BOARD_SHA256=" + $currentHash)
    Write-Output ("IDEA_BOARD_SNAPSHOT=" + $snapshotPath)
    exit 0
}

$previous = [IO.File]::ReadAllText($snapshotPath, [Text.Encoding]::UTF8) -replace "`r`n", "`n"
$previousHash = if (Test-Path -LiteralPath $hashPath) {
    [IO.File]::ReadAllText($hashPath, [Text.Encoding]::UTF8).Trim()
} else {
    [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($previous))
    ).ToLowerInvariant()
}

if ($previousHash -eq $currentHash) {
    Write-Output "IDEA_BOARD_STATUS=UNCHANGED"
    Write-Output ("IDEA_BOARD_SHA256=" + $currentHash)
    exit 0
}

$difference = Compare-Object ($previous -split "`n") ($current -split "`n")
$added = @($difference | Where-Object SideIndicator -eq "=>")
$removed = @($difference | Where-Object SideIndicator -eq "<=")

Write-Output "IDEA_BOARD_STATUS=CHANGED"
Write-Output ("IDEA_BOARD_PREVIOUS_SHA256=" + $previousHash)
Write-Output ("IDEA_BOARD_SHA256=" + $currentHash)
Write-Output ("IDEA_BOARD_ADDED_LINES=" + $added.Count)
Write-Output ("IDEA_BOARD_REMOVED_LINES=" + $removed.Count)

$removed | Select-Object -First 100 | ForEach-Object {
    Write-Output ("IDEA_BOARD_REMOVED=" + $_.InputObject)
}
$added | Select-Object -First 100 | ForEach-Object {
    Write-Output ("IDEA_BOARD_ADDED=" + $_.InputObject)
}

if (($added.Count + $removed.Count) -gt 200) {
    Write-Output "IDEA_BOARD_DIFF_TRUNCATED=true"
}

[IO.File]::WriteAllText($snapshotPath, $current, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText($hashPath, $currentHash, [Text.UTF8Encoding]::new($false))
