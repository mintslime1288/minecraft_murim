@echo off
set "MURIMRPG_LAUNCHER_ROOT=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression (Get-Content -LiteralPath '%~dp0MurimRpgLauncher.ps1' -Raw -Encoding UTF8)"
