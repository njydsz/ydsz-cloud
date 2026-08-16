@echo off
cd /d D:\Code\open\ydsz-cloud
echo === Testing seata module compilation ===
call mvn compile -pl ydsz-common/ydsz-common-seata -Dcheckstyle.skip=true -Denforcer.skip=true 2>&1
echo.
echo === Exit code: %ERRORLEVEL% ===
