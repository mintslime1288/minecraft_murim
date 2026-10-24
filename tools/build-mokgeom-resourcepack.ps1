param()

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$output = Join-Path $root 'resourcepacks\MurimRPG-Mokgeom-26.2.zip'
$items = @(
    @{ Source = 'resourcepacks\mokgeom-pack.mcmeta'; Entry = 'pack.mcmeta' },
    @{ Source = 'src\main\resources\assets\minecraft\items\wooden_sword.json'; Entry = 'assets/minecraft/items/wooden_sword.json' },
    @{ Source = 'src\main\resources\assets\murimrpg\models\item\mokgeom_reference_v1.json'; Entry = 'assets/murimrpg/models/item/mokgeom_reference_v1.json' },
    @{ Source = 'src\main\resources\assets\murimrpg\textures\item\mokgeom_reference.png'; Entry = 'assets/murimrpg/textures/item/mokgeom_reference.png' }
)

$meta = Get-Content -LiteralPath (Join-Path $root $items[0].Source) -Raw | ConvertFrom-Json
if ($meta.pack.min_format -ne 88 -or $meta.pack.max_format -ne 88) {
    throw 'Minecraft Java 26.2 resource pack format must be 88.'
}
$dispatch = Get-Content -LiteralPath (Join-Path $root $items[1].Source) -Raw | ConvertFrom-Json
if ($dispatch.model.type -ne 'minecraft:range_dispatch' -or
    $dispatch.model.property -ne 'minecraft:custom_model_data' -or
    $dispatch.model.entries[0].threshold -ne 32001 -or
    $dispatch.model.entries[0].model.model -ne 'murimrpg:item/mokgeom_reference_v1' -or
    $dispatch.model.fallback.model -ne 'minecraft:item/wooden_sword') {
    throw 'The wooden sword dispatch no longer selects only MurimRPG model 32001.'
}
$model = Get-Content -LiteralPath (Join-Path $root $items[2].Source) -Raw | ConvertFrom-Json
if ($model.elements.Count -ne 3536 -or $model.textures.'0' -ne 'murimrpg:item/mokgeom_reference') {
    throw 'The approved 3,536-cube wooden sword model or texture link has changed.'
}

Add-Type -AssemblyName System.IO.Compression
$stream = [System.IO.File]::Open($output, [System.IO.FileMode]::Create)
try {
    $archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create, $true)
    try {
        foreach ($item in $items) {
            $source = Join-Path $root $item.Source
            if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing pack input: $source" }
            $entry = $archive.CreateEntry($item.Entry, [System.IO.Compression.CompressionLevel]::Optimal)
            $input = [System.IO.File]::OpenRead($source)
            try {
                $destination = $entry.Open()
                try { $input.CopyTo($destination) } finally { $destination.Dispose() }
            } finally { $input.Dispose() }
        }
    } finally { $archive.Dispose() }
} finally { $stream.Dispose() }

$zip = [System.IO.Compression.ZipFile]::OpenRead($output)
try {
    $expected = @($items | ForEach-Object { $_.Entry } | Sort-Object)
    $actual = @($zip.Entries | ForEach-Object { $_.FullName } | Sort-Object)
    if (($expected -join '|') -ne ($actual -join '|')) { throw 'Resource pack archive entries do not match the source list.' }
    foreach ($item in $items) {
        $entry = $zip.GetEntry($item.Entry)
        $source = Join-Path $root $item.Source
        if ($entry.Length -ne (Get-Item -LiteralPath $source).Length) { throw "Archive length mismatch: $($item.Entry)" }
        $sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
        $digest = [System.Security.Cryptography.SHA256]::Create()
        try {
            $entryStream = $entry.Open()
            try { $packedHash = [Convert]::ToHexString($digest.ComputeHash($entryStream)) }
            finally { $entryStream.Dispose() }
        } finally { $digest.Dispose() }
        if ($packedHash -ne $sourceHash) { throw "Archive contents differ from source: $($item.Entry)" }
    }
} finally { $zip.Dispose() }

$hash = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash
Write-Output "RESOURCE_PACK=$output"
Write-Output "SHA256=$hash"
