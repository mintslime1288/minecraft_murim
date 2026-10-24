param(
    [ValidateSet('Snapshot', 'Build', 'Test', 'Client', 'Prepare', 'Info')]
    [string]$Action = 'Snapshot'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$localRoot = Join-Path ([Environment]::GetFolderPath('UserProfile')) 'MurimRPG-Tools'
$jdkPointer = Join-Path $localRoot 'toolchains\active-jdk25.txt'
if (-not (Test-Path -LiteralPath $jdkPointer)) {
    throw "JDK 25 configuration missing: $jdkPointer. See docs/HOME_PC_READY.md."
}
$jdkPath = (Get-Content -LiteralPath $jdkPointer -Raw).Trim()
if (-not (Test-Path -LiteralPath (Join-Path $jdkPath 'bin\javac.exe'))) {
    throw "JDK is unavailable: $jdkPath"
}
$previousJava = $env:JAVA_HOME
$previousPath = $env:PATH
$previousGradle = $env:GRADLE_USER_HOME
$result = 0
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $jdkPath
    $env:PATH = "$jdkPath\bin;$previousPath"
    $env:GRADLE_USER_HOME = Join-Path $localRoot 'gradle'
    $buildDirectory = Join-Path $localRoot 'workspace\source-snapshot-20260929\build'
    $projectCache = Join-Path $localRoot 'workspace\source-snapshot-20260929\project-cache'
    $gradleOptions = @('--console=plain', '--project-cache-dir', $projectCache, "-PmurimBuildDir=$buildDirectory")
    Write-Host "Project: $projectRoot"
    Write-Host "JAVA_HOME: $jdkPath"
    Write-Host "Build output: $buildDirectory"
    Write-Host "Development gameDir: $(Join-Path $projectRoot 'run')"
    switch ($Action) {
        'Snapshot' { & .\gradlew.bat sourceSnapshotBuild @gradleOptions }
        'Info' { & .\gradlew.bat --version }
        'Build' { & .\gradlew.bat test build @gradleOptions }
        'Test' { & .\gradlew.bat test @gradleOptions }
        'Prepare' { & .\gradlew.bat configureClientLaunch @gradleOptions }
        # Preserve the established local development identity and its saved server data.
        'Client' { & .\gradlew.bat runClient '--args=--username Player716' @gradleOptions }
    }
    $result = $LASTEXITCODE
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJava
    $env:PATH = $previousPath
    $env:GRADLE_USER_HOME = $previousGradle
}
exit $result
