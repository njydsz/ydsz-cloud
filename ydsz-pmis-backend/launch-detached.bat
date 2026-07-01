@echo off
REM Launch a single Java service in a way that survives PowerShell parent death
REM Usage: launch-detached.bat <jar-path> <workdir> <service-name>

setlocal EnableDelayedExpansion

set "JAR=%~1"
set "WORKDIR=%~2"
set "SVC=%~3"
set "LOGDIR=d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\logs"

if "%JAR%"=="" (
    echo [launch-detached] missing JAR path
    exit /b 1
)
if "%WORKDIR%"=="" set "WORKDIR=%CD%"
if "%SVC%"=="" set "SVC=service"

if not exist "%LOGDIR%" mkdir "%LOGDIR%"

set "OUTLOG=%LOGDIR%\%SVC%.out.log"
set "ERRLOG=%LOGDIR%\%SVC%.err.log"

REM Build a child batch that loads env and starts java; spawn via start /B
set "CHILD=%LOGDIR%\child-%SVC%.bat"

(
    echo @echo off
    echo setlocal
    echo cd /d "%WORKDIR%"
    REM Load env vars from dev.env
    for /f "usebackq tokens=1,2 delims==" %%A in ("d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\dev.env") do (
        if not "%%A"=="" if not "%%A:~0,1%"=="#" echo set "%%A=%%B"
    )
    echo java -jar "%JAR%" ^> "%OUTLOG%" 2^> "%ERRLOG%"
) > "%CHILD%"

REM Use cmd /c start /B to spawn a new process tree that won't be killed when we exit
start "pmis-%SVC%" /B cmd /c "%CHILD%"

REM Wait a moment then output the spawned PID by looking for the jar process
ping -n 2 127.0.0.1 > nul
for /f "tokens=1" %%P in ('wmic process where "name='java.exe' and commandline like '%%%SVC%%.jar%%'" get processid 2^>nul ^| findstr [0-9]') do (
    echo [launch-detached] %SVC% pid=%%P
    goto :done
)
:done
endlocal
exit /b 0
