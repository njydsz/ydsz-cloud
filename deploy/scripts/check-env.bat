@echo off
REM =============================================================================
REM  YDSZ PMIS - 环境检查脚本 (Windows PowerShell)
REM =============================================================================
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check-env.ps1"
