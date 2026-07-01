@echo off
setlocal
set DB_NAME=ydsz-pmis
set DB_USER=pmis
set DB_PASSWORD=pmis
set NACOS_SERVER_ADDR=127.0.0.1:8848
set JAVA_HOME=C:\Program Files\Amazon Corretto\jdk17.0.16_8

set BACKEND_ROOT=D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend
set LOGS_ROOT=D:\Code\ydsz\ydsz-pmis\logs

if not exist "%LOGS_ROOT%" mkdir "%LOGS_ROOT%"

call :start_service ydsz-pmis-gateway 9000
call :start_service ydsz-pmis-auth 9001
call :start_service ydsz-pmis-user 9002
call :start_service ydsz-pmis-workflow 9007
call :start_service ydsz-pmis-notification 9009
call :start_service ydsz-pmis-config 9010
call :start_service ydsz-pmis-file 9011
call :start_service ydsz-pmis-scheduler 9012
call :start_service ydsz-pmis-message 9013
call :start_service ydsz-pmis-audit 9014
call :start_service ydsz-pmis-project 9015
call :start_service ydsz-pmis-execution 9016
call :start_service ydsz-pmis-agent 9017

echo.
echo All services start commands issued.
echo Waiting 90s for services to come up...
timeout /t 90 /nobreak >nul

echo.
echo Service status check:
powershell -Command "$ports = @(9000,9001,9002,9007,9009,9010,9011,9012,9013,9014,9015,9016,9017); foreach ($p in $ports) { $c = Test-NetConnection -ComputerName 127.0.0.1 -Port $p -InformationLevel Quiet -WarningAction SilentlyContinue; Write-Host ('  port ' + $p + ': ' + $(if($c){'UP'}else{'DOWN'})) -ForegroundColor $(if($c){'Green'}else{'Red'}) }"
goto :eof

:start_service
set MODULE=%~1
set PORT=%~2
set JAR=%BACKEND_ROOT%\%MODULE%\target\%MODULE%.jar
set LOG=%LOGS_ROOT%\%MODULE%.log
echo Starting %MODULE% on port %PORT% (log: %LOG%)
if not exist "%JAR%" (
    echo SKIP %MODULE% - jar not found
    goto :eof
)
start "pmis-%MODULE%" /B cmd /c "set DB_NAME=ydsz-pmis&& set DB_USER=pmis&& set DB_PASSWORD=pmis&& set NACOS_SERVER_ADDR=127.0.0.1:8848&& set JAVA_HOME=C:\Program Files\Amazon Corretto\jdk17.0.16_8&& cd /d %BACKEND_ROOT%&& "%JAVA_HOME%\bin\java.exe" -Xms256m -Xmx512m -jar %JAR% > %LOG% 2>&1"
timeout /t 5 /nobreak >nul
goto :eof
