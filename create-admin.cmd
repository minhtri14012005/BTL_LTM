@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Setup-ClassQuest.ps1" -AdminOnly %*
exit /b %errorlevel%
