@echo off
REM Wrapper for download_embedding_model.ps1 (avoids PowerShell execution policy block).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0download_embedding_model.ps1" %*
