@echo off
REM ===================================================================
REM PMIS Backend Launcher (Windows Batch)
REM 启动所有 13 个后端微服务
REM 使用方法: 双击运行,或 cmd /k "start-all.bat"
REM ===================================================================

setlocal EnableDelayedExpansion
set "BACKEND_DIR=%~dp0"
set "LOGS_DIR=%BACKEND_DIR%logs"
if not exist "%LOGS_DIR%" mkdir "%LOGS_DIR%"

REM 加载 dev.env
for /f "usebackq tokens=1,2 delims==" %%a in ("%BACKEND_DIR%dev.env") do (
    if not "%%a"=="" if not "%%a:~0,1%"=="#" set "%%a=%%b"
)

echo === Loaded env vars ===
echo NACOS_SERVER_ADDR=%NACOS_SERVER_ADDR%
echo DB_HOST=%DB_HOST% DB_NAME=%DB_NAME%
echo REDIS_HOST=%REDIS_HOST%
echo.

REM 模块 -> jar / port
set "SERVICES=config file audit user auth workflow notification message project execution agent scheduler gateway"

set "COUNT=0"
for %%S in (%SERVICES%) do set /a COUNT+=1
echo === Will launch %COUNT% services ===
echo.

set "ORDER=0"
for %%S in (%SERVICES%) do (
    set /a ORDER+=1
    set "MOD=ydsz-pmis-%%S"
    if /i "%%S"=="message" set "JAR=ydsz-pmis-message-exec.jar"
    if /i "%%S"=="project" set "JAR=ydsz-pmis-project-exec.jar"
    if /i "%%S"=="config" set "JAR=ydsz-pmis-config.jar"
    if /i "%%S"=="file" set "JAR=ydsz-pmis-file.jar"
    if /i "%%S"=="audit" set "JAR=ydsz-pmis-audit.jar"
    if /i "%%S"=="user" set "JAR=ydsz-pmis-user.jar"
    if /i "%%S"=="auth" set "JAR=ydsz-pmis-auth.jar"
    if /i "%%S"=="workflow" set "JAR=ydsz-pmis-workflow.jar"
    if /i "%%S"=="notification" set "JAR=ydsz-pmis-notification.jar"
    if /i "%%S"=="execution" set "JAR=ydsz-pmis-execution.jar"
    if /i "%%S"=="agent" set "JAR=ydsz-pmis-agent.jar"
    if /i "%%S"=="scheduler" set "JAR=ydsz-pmis-scheduler.jar"
    if /i "%%S"=="gateway" set "JAR=ydsz-pmis-gateway.jar"

    set "JAR_PATH=%BACKEND_DIR%!MOD!\target\!JAR!"
    if not exist "!JAR_PATH!" (
        echo [!ORDER!/%COUNT%] MISSING: !JAR_PATH!
    ) else (
        echo [!ORDER!/%COUNT%] Starting %%S  ^<!MOD!^>
        REM Use PowerShell to launch detached
        powershell -NoProfile -Command "Start-Process -FilePath 'java' -ArgumentList '-jar','!JAR_PATH!' -WorkingDirectory '%BACKEND_DIR%!MOD!' -RedirectStandardOutput '%LOGS_DIR%\%%S.log' -RedirectStandardError '%LOGS_DIR%\%%S.err.log' -WindowStyle Hidden"
    )
    timeout /t 1 /nobreak >nul
)

echo.
echo === All services launched. PIDs: ===
powershell -NoProfile -Command "Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.StartTime -gt (Get-Date).AddMinutes(-2) } | Format-Table Id, StartTime -AutoSize"

echo.
echo === Waiting 60s for Spring Boot to start ===
timeout /t 60 /nobreak

echo.
echo === Port status ===
powershell -NoProfile -Command "foreach ($p in 9000,9001,9002,9013,9014,9015,9016,9017,9018,9019,9020,9021,9022) { $c = Get-NetTCPConnection -State Listen -LocalPort $p -ErrorAction SilentlyContinue; if ($c) { Write-Host ('  {0:D4} LISTENING' -f $p) -ForegroundColor Green } else { Write-Host ('  {0:D4} NOT YET' -f $p) -ForegroundColor Yellow } }"

echo.
echo === Done. Logs at %LOGS_DIR% ===
endlocal
pause
