$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationFramework

$sourceRoot = if ($env:MURIMRPG_LAUNCHER_ROOT) { $env:MURIMRPG_LAUNCHER_ROOT } else { $PSScriptRoot }
$appData = Join-Path $env:APPDATA 'MurimRPGLauncher'
New-Item -ItemType Directory -Path $appData -Force | Out-Null
$logPath = Join-Path $appData 'launcher.log'
$savedConfigPath = Join-Path $appData 'launcher-config.json'
if (-not (Test-Path -LiteralPath $savedConfigPath)) { Copy-Item (Join-Path $sourceRoot 'launcher-config.json') $savedConfigPath }
$config = Get-Content -Raw -Encoding UTF8 -LiteralPath $savedConfigPath | ConvertFrom-Json
$minecraftDirectory = [Environment]::ExpandEnvironmentVariables([string]$config.minecraftDirectory)
$manifestPath = Join-Path $sourceRoot ([string]$config.manifestFile)

function Write-LauncherLog([string]$message) {
    Add-Content -LiteralPath $logPath -Encoding UTF8 -Value ("{0:yyyy-MM-dd HH:mm:ss} {1}" -f (Get-Date), $message)
}
function Get-Sha256([string]$path) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([System.IO.File]::ReadAllBytes($path)))).Replace('-', '') }
    finally { $sha.Dispose() }
}
function Save-LauncherConfig { $config | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $savedConfigPath -Encoding UTF8 }
function Set-Status([string]$message) { $status.Text = $message; Write-LauncherLog $message }
function Test-LauncherEnvironment {
    $problems = [System.Collections.Generic.List[string]]::new()
    if (-not (Test-Path -LiteralPath $minecraftDirectory)) { $problems.Add('Minecraft Java Edition 게임 폴더를 찾지 못했습니다.') }
    $profilesPath = Join-Path $minecraftDirectory 'launcher_profiles.json'
    if (-not (Test-Path -LiteralPath $profilesPath)) { $problems.Add('공식 Minecraft Launcher 설정 파일을 찾지 못했습니다.') }
    else {
        $profiles = Get-Content -Raw -LiteralPath $profilesPath | ConvertFrom-Json
        $profile = $profiles.profiles.($config.fabricProfileId)
        if ($null -eq $profile -or $profile.lastVersionId -ne $config.fabricVersionId) { $problems.Add('Fabric 프로필 fabric-loader-26.2가 없거나 버전이 다릅니다.') }
    }
    if (-not (Test-Path -LiteralPath $manifestPath)) { $problems.Add('런처 manifest.json을 찾지 못했습니다.') }
    else {
        $manifest = Get-Content -Raw -Encoding UTF8 -LiteralPath $manifestPath | ConvertFrom-Json
        foreach ($file in $manifest.files) {
            $target = Join-Path $minecraftDirectory $file.path
            if (-not (Test-Path -LiteralPath $target)) { $problems.Add(('[필수 파일 없음] ' + $file.displayName + ': ' + $target)); continue }
            if ($file.sha256 -notlike 'REPLACE_*') {
                $actual = Get-Sha256 $target
                if ($actual -ne $file.sha256) { $problems.Add(('[파일 검증 실패] ' + $file.displayName)) }
            }
        }
    }
    if ($problems.Count -eq 0) { Set-Status '검사 완료: Java Edition Fabric 실행 준비가 되었습니다.' }
    else { Set-Status ('검사 필요: ' + ($problems -join ' / ')) }
}

[xml]$xaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" Title="MurimRPG 런처" Height="470" Width="630" WindowStartupLocation="CenterScreen" ResizeMode="CanMinimize" Background="Black">
  <Grid Margin="24">
    <Grid.RowDefinitions><RowDefinition Height="Auto"/><RowDefinition Height="Auto"/><RowDefinition Height="*"/><RowDefinition Height="Auto"/></Grid.RowDefinitions>
    <StackPanel Grid.Row="0"><TextBlock Text="MurimRPG" Foreground="Goldenrod" FontSize="32" FontWeight="Bold"/><TextBlock Text="마인크래프트 Java Edition · Fabric 전용 런처" Foreground="White" FontSize="14" Margin="0,4,0,20"/></StackPanel>
    <Border Grid.Row="1" Background="DimGray" CornerRadius="8" Padding="16"><StackPanel><TextBlock x:Name="Server" Foreground="White" FontSize="16"/><TextBlock x:Name="Status" Foreground="WhiteSmoke" TextWrapping="Wrap" Margin="0,8,0,0"/></StackPanel></Border>
    <StackPanel Grid.Row="2" Margin="0,18,0,0"><TextBlock Text="메모리 할당량" Foreground="White"/><ComboBox x:Name="Ram" Width="150" HorizontalAlignment="Left" Margin="0,6,0,14"><ComboBoxItem Content="2 GB (저사양)"/><ComboBoxItem Content="4 GB (보통)"/><ComboBoxItem Content="6 GB (고사양)"/></ComboBox><WrapPanel><Button x:Name="Check" Content="파일 검사" Width="130" Height="36" Margin="0,0,10,10"/><Button x:Name="Copy" Content="서버 주소 복사" Width="130" Height="36" Margin="0,0,10,10"/><Button x:Name="Logs" Content="로그 열기" Width="110" Height="36" Margin="0,0,10,10"/><Button x:Name="Start" Content="Java Edition 시작" Width="165" Height="36" Margin="0,0,10,10"/></WrapPanel><TextBlock Text="공식 런처를 엽니다. Java Edition에서 fabric-loader-26.2 프로필을 선택하세요." Foreground="LightGray" TextWrapping="Wrap" Margin="0,12,0,0"/></StackPanel>
    <TextBlock Grid.Row="3" Text="계정 비밀번호를 받거나 저장하지 않습니다." Foreground="LightGray" HorizontalAlignment="Right"/>
  </Grid>
</Window>
'@
$reader = [System.Xml.XmlNodeReader]::new($xaml)
$window = [Windows.Markup.XamlReader]::Load($reader)
$status = $window.FindName('Status'); $server = $window.FindName('Server'); $ram = $window.FindName('Ram')
$server.Text = ('서버 주소: ' + $config.serverAddress + '  |  런처 설정: ' + $savedConfigPath)
switch ([int]$config.defaultRamMiB) { 2048 { $ram.SelectedIndex = 0 } 6144 { $ram.SelectedIndex = 2 } default { $ram.SelectedIndex = 1 } }
$window.FindName('Check').Add_Click({ Test-LauncherEnvironment })
$window.FindName('Copy').Add_Click({ [Windows.Clipboard]::SetText([string]$config.serverAddress); Set-Status '서버 주소를 클립보드에 복사했습니다.' })
$window.FindName('Logs').Add_Click({ if (-not (Test-Path -LiteralPath $logPath)) { New-Item -ItemType File -Path $logPath -Force | Out-Null }; Start-Process notepad.exe $logPath })
$ram.Add_SelectionChanged({ $config.defaultRamMiB = switch ($ram.SelectedIndex) { 0 { 2048 } 2 { 6144 } default { 4096 } }; Save-LauncherConfig; Set-Status ('권장 메모리를 ' + $config.defaultRamMiB + ' MiB로 저장했습니다.') })
$window.FindName('Start').Add_Click({
    Test-LauncherEnvironment
    if ($status.Text -notlike '검사 완료*') { return }
    Start-Process 'shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft'
    Set-Status '공식 Minecraft Launcher를 열었습니다. Java Edition에서 fabric-loader-26.2 프로필을 선택하세요.'
})
Test-LauncherEnvironment
[void]$window.ShowDialog()
