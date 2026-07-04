@echo off
REM =============================================================================
REM  YDSZ PMIS - Nacos 共享配置导入脚本 (Windows)
REM =============================================================================
chcp 65001 >nul
set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%..\..
for %%I in ("%ROOT_DIR%") do set ROOT_DIR=%%~fI
set CONFIG_FILE=%ROOT_DIR%\deploy\nacos\ydsz-pmis-common.yaml
set NACOS_ADDR=%NACOS_SERVER_ADDR%
if "%NACOS_ADDR%"=="" set NACOS_ADDR=127.0.0.1:8848
set NAMESPACE=%~1
if "%NAMESPACE%"=="" set NAMESPACE=pmis
set GROUP=%~2
if "%GROUP%"=="" set GROUP=dev

echo [INFO] 等待 Nacos %NACOS_ADDR% 就绪...
:WAIT_NACOS
powershell -NoProfile -Command "try { $null = Invoke-RestMethod -Uri 'http://%NACOS_ADDR%/nacos/actuator/health' -TimeoutSec 2; exit 0 } catch { exit 1 }" >nul 2>&1
if errorlevel 1 (
  timeout /t 2 /nobreak >nul
  goto WAIT_NACOS
)
echo [OK] Nacos 已就绪

echo [INFO] 导入配置: ydsz-pmis-common.yaml ^(namespace=%NAMESPACE%, group=%GROUP%^)
powershell -NoProfile -Command ^
  "$content = Get-Content '%CONFIG_FILE%' -Raw -Encoding UTF8;" ^
  "$body = @{ dataId='ydsz-pmis-common.yaml'; group='%GROUP%'; namespaceId='%NAMESPACE%'; content=$content; type='yaml' };" ^
  "try {" ^
  "  $r = Invoke-RestMethod -Uri 'http://%NACOS_ADDR%/v1/cs/configs' -Method Post -Body $body -ContentType 'application/x-www-form-urlencoded';" ^
  "  Write-Host '[OK] 导入成功'" ^
  "} catch {" ^
  "  Write-Host '[ERROR] 导入失败:' $_.Exception.Message -ForegroundColor Red;" ^
  "  Write-Host '[HINT] 可手动在 Nacos 控制台导入' -ForegroundColor Yellow;" ^
  "  exit 1" ^
  "}"

echo.
echo ============================================================
echo   共享配置导入完成
echo ============================================================
