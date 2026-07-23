@echo off
REM =============================================================================
REM  YDSZ - 一键停止脚本 (Windows)
REM =============================================================================
chcp 65001 >nul
set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%..\..\..
for %%I in ("%ROOT_DIR%") do set ROOT_DIR=%%~fI
set LOG_DIR=%ROOT_DIR%\.run-logs

echo [%date% %time:~0,8%] [INFO] 停止后端服务...
if exist "%LOG_DIR%" (
  powershell -NoProfile -Command ^
    "Get-ChildItem '%LOG_DIR%\*.pid' | ForEach-Object {" ^
    "  $name = $_.BaseName; $pid = Get-Content $_.FullName -ErrorAction SilentlyContinue;" ^
    "  if ($pid) {" ^
    "    Write-Host ('  停止 {0} (PID {1})' -f $name, $pid);" ^
    "    try { Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue } catch {}" ^
    "    Get-CimInstance Win32_Process -Filter ('ParentProcessId=' + $pid) -ErrorAction SilentlyContinue | ForEach-Object { try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch {} }" ^
    "  }" ^
    "  Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue" ^
    "}"
)

if /I "%1"=="--with-infra" goto STOP_INFRA
if /I "%1"=="-a" goto STOP_INFRA
goto DONE

:STOP_INFRA
echo [%date% %time:~0,8%] [INFO] 停止基础设施容器...
cd /d "%ROOT_DIR%\deploy\docker"
docker compose -f docker-compose.dev.yml down
cd /d "%ROOT_DIR%"

:DONE
echo [%date% %time:~0,8%] [INFO] 停止前端进程...
powershell -NoProfile -Command "Get-Process -Name 'node' -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*vite*' } | Stop-Process -Force -ErrorAction SilentlyContinue"

echo.
echo ============================================================
echo  全部停止完成
echo  - 清理数据卷：  cd deploy\docker ^&^& docker compose -f docker-compose.dev.yml down -v
echo  - 重新启动：    deploy\windows\scripts\start-all.bat
echo ============================================================
