@echo off
REM Launch a single Java service as a detached process
setlocal

set "JAR=%~1"
set "WORKDIR=%~2"
set "SVC=%~3"
set "OUTLOG=%~4"
set "ERRLOG=%~5"

if "%JAR%"=="" exit /b 1
if "%WORKDIR%"=="" set "WORKDIR=%CD%"
if "%SVC%"=="" set "SVC=service"
if "%OUTLOG%"=="" set "OUTLOG=%WORKDIR%\..\logs\%SVC%.out.log"
if "%ERRLOG%"=="" set "ERRLOG=%WORKDIR%\..\logs\%SVC%.err.log"

if not exist "%OUTLOG%" type nul > "%OUTLOG%"
if not exist "%ERRLOG%" type nul > "%ERRLOG%"

REM Load env vars from dev.env
for /f "usebackq tokens=1,2 delims==" %%A in ("d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\dev.env") do (
    if not "%%A"=="" if not "%%A:~0,1%"=="#" set "%%A=%%B"
)

cd /d "%WORKDIR%"
start "pmis-%SVC%" /B java -jar "%JAR%" > "%OUTLOG%" 2> "%ERRLOG%"
exit /b 0
