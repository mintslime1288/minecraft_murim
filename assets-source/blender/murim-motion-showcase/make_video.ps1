param(
    [Parameter(Mandatory=$true)][string]$Blender,
    [Parameter(Mandatory=$true)][string]$Ffmpeg,
    [Parameter(Mandatory=$true)][string]$Output,
    [string]$WorkDir = (Join-Path $env:TEMP 'MurimRPG-motion-showcase')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$source = Join-Path $PSScriptRoot '..\murim-player-rig-v1\murim_player_rig_v1_editable.blend'
$renderer = Join-Path $PSScriptRoot 'render_showcase.py'
$frames = Join-Path $WorkDir 'frames'
$overlays = Join-Path $WorkDir 'overlays'
New-Item -ItemType Directory -Force -Path $frames,$overlays | Out-Null

& $Blender -b $source --python $renderer -- $frames *> (Join-Path $WorkDir 'render.log')
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath (Join-Path $WorkDir 'render.log') -Tail 30
    throw 'Blender render failed'
}

$names = @('대기 자세','걷기','달리기','점프','웅크리기','맨손 타격')
$descriptions = @(
    '호흡과 중심 이동을 절제한 무림 기본 자세',
    '발놀림에 맞춘 자연스러운 체중 이동',
    '상체를 낮추고 박자를 살린 질주',
    '준비 · 도약 · 체공 · 착지',
    '두 발의 접지를 유지하는 낮은 자세',
    '기세를 모아 앞으로 내지르는 주먹'
)
$dark = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(220,14,25,38))
$white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255,242,236,222))
$muted = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255,185,199,205))
$gold = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255,199,157,79))
$fontMain = [System.Drawing.Font]::new('Malgun Gothic',24,[System.Drawing.FontStyle]::Bold)
$fontSmall = [System.Drawing.Font]::new('Malgun Gothic',12,[System.Drawing.FontStyle]::Regular)
$fontTop = [System.Drawing.Font]::new('Malgun Gothic',15,[System.Drawing.FontStyle]::Bold)
try {
    for ($i=0; $i -lt 6; $i++) {
        $bitmap = [System.Drawing.Bitmap]::new(960,540,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
            $graphics.FillRectangle($dark, 24,18,360,52)
            $graphics.FillRectangle($gold, 24,18,5,52)
            $graphics.DrawString('강호이문록  |  무협 동작 연구',$fontTop,$white,42,31)
            $graphics.FillRectangle($dark, 24,431,590,83)
            $graphics.FillRectangle($gold, 24,431,5,83)
            $num = '{0:00}' -f ($i+1)
            $graphics.DrawString($num,$fontMain,$gold,42,444)
            $graphics.DrawString($names[$i],$fontMain,$white,101,444)
            $graphics.DrawString($descriptions[$i],$fontSmall,$muted,45,484)
            $graphics.FillRectangle($dark, 762,19,174,40)
            $graphics.DrawString(('MOTION  '+$num+' / 06'),$fontSmall,$gold,776,29)
            $dest = Join-Path $overlays ($num+'.png')
            $bitmap.Save($dest,[System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $graphics.Dispose()
            $bitmap.Dispose()
        }
    }
} finally {
    foreach ($item in @($dark,$white,$muted,$gold,$fontMain,$fontSmall,$fontTop)) { $item.Dispose() }
}

$inputs = @('-hide_banner','-loglevel','error','-y','-framerate','24','-start_number','1','-i',(Join-Path $frames '%04d.png'))
for ($i=1; $i -le 6; $i++) {
    $inputs += @('-loop','1','-i',(Join-Path $overlays (('{0:00}' -f $i)+'.png')))
}
$parts = @()
$prev = '[0:v]'
for ($i=1; $i -le 6; $i++) {
    $start = ($i-1)*3
    $end = $i*3
    $next = '[v'+$i+']'
    $parts += ($prev+'['+$i+':v]overlay=0:0:enable=''gte(t,'+$start+')*lt(t,'+$end+')'''+$next)
    $prev = $next
}
$filter = $parts -join ';'
& $Ffmpeg @inputs -filter_complex $filter -map $prev -frames:v 432 -r 24 -c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p -movflags +faststart $Output
if ($LASTEXITCODE -ne 0) { throw 'FFmpeg encode failed' }
Get-Item -LiteralPath $Output | Select-Object FullName,Length
