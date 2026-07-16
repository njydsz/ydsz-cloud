@echo off
REM =============================================================================
REM  YDSZ PMIS - 一键启动脚本 (Windows PowerShell)
REM -----------------------------------------------------------------------------
REM  启动顺序：
REM    1. 环境检查（docker / java / maven / node）
REM    2. 加载 deploy/.env
REM    3. 启动基础设施容器（Nacos / PG / Redis / MinIO）
REM    4. 等待基础设施健康
REM    5. 编译并后台启动 11 个后端微服务
REM    6. 启动前端开发服务器
REM
REM  用法：
REM    deploy\windows\scripts\start-all.bat       REM  # 全量启动
REM    deploy\windows\scripts\start-all.bat backend    REM  # 只启动后端
REM    deploy\windows\scripts\start-all.bat frontend   REM  # 只启动前端
REM    deploy\windows\scripts\start-all.bat infra      REM  # 只启动基础设施
REM =============================================================================
chcp 65001 >nul
setlocal EnableDelayedExpansion

REM 颜色支持（PowerShell）
for /F "tokens=*" %%i in ('powershell -NoProfile -Command "Get-Date -Format 'HH:mm:ss'"') do set TIMESTAMP=%%i

echo [%TIMESTAMP%] [INFO] 脚本启动...

REM 路径
set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%..\..\..
for %%I in ("%ROOT_DIR%") do set ROOT_DIR=%%~fI
set BACKEND_DIR=%ROOT_DIR%\ydsz-backend
set FRONTEND_DIR=%ROOT_DIR%\ydsz-frontend
set LOG_DIR=%ROOT_DIR%\.run-logs
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

cd /d "%ROOT_DIR%"

REM ---------- 凭据默认值（可通过环境变量覆盖，仅用于开发提示） ----------
if not defined NACOS_USERNAME set NACOS_USERNAME=nacos
if not defined NACOS_PASSWORD set NACOS_PASSWORD=nacos
if not defined MINIO_ROOT_USER set MINIO_ROOT_USER=minioadmin
if not defined MINIO_ROOT_PASSWORD set MINIO_ROOT_PASSWORD=minioadmin

REM -----------------------------------------------------------------------------
REM  1. 环境检查
REM -----------------------------------------------------------------------------
echo [%TIMESTAMP%] [INFO] 步骤 1/6 - 环境检查

where docker >nul 2>&1 || (echo [ERROR] 未找到 docker，请先安装 & exit /b 1)
where java   >nul 2>&1 || (echo [ERROR] 未找到 java，请先安装 JDK 21 & exit /b 1)
where mvn    >nul 2>&1 || (echo [ERROR] 未找到 mvn，请先安装 Maven 3.9+ & exit /b 1)
java -version 2>&1 | findstr /R "21\." >nul || (echo [ERROR] 需要 JDK 21+ & exit /b 1)

if /I not "%1"=="backend" if /I not "%1"=="infra" (
  where node >nul 2>&1 || (echo [ERROR] 未找到 node，请先安装 Node.js 20+ & exit /b 1)
  where pnpm >nul 2>&1 || (echo [ERROR] 未找到 pnpm，运行 npm i -g pnpm & exit /b 1)
)

echo [%TIMESTAMP%] [OK] 环境检查通过

REM -----------------------------------------------------------------------------
REM  2. 加载环境变量
REM -----------------------------------------------------------------------------
echo [%TIMESTAMP%] [INFO] 步骤 2/6 - 加载环境变量
if not exist "%ROOT_DIR%\deploy\.env" (
  echo [%TIMESTAMP%] [WARN] deploy\.env 不存在，从 .env.example 复制
  copy "%ROOT_DIR%\deploy\.env.example" "%ROOT_DIR%\deploy\.env" >nul
)
REM Windows 下用 PowerShell 加载 .env
powershell -NoProfile -Command "Get-Content '%ROOT_DIR%\deploy\.env' | ForEach-Object { if ($_ -and $_ -notmatch '^\s*#' -and $_ -match '^\s*([^=]+)=(.*)$') { [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process') } }"
echo [%TIMESTAMP%] [OK] 环境变量已加载

REM -----------------------------------------------------------------------------
REM  3. 启动基础设施
REM -----------------------------------------------------------------------------
if /I not "%1"=="backend" if /I not "%1"=="frontend" (
  echo [%TIMESTAMP%] [INFO] 步骤 3/6 - 启动基础设施
  cd /d "%ROOT_DIR%\deploy\docker"
  docker compose -f docker-compose.dev.yml up -d
  cd /d "%ROOT_DIR%"

  echo [%TIMESTAMP%] [INFO] 等待基础设施健康（最长 120s）...
  set /a WAITED=0
  :WAIT_INFRA
  if !WAITED! GEQ 120 goto INFRA_READY
  timeout /t 5 /nobreak >nul
  set /a WAITED+=5
  powershell -NoProfile -Command "$h=(docker compose -f '%ROOT_DIR%\deploy\docker\docker-compose.dev.yml' ps --format json 2>$null | ConvertFrom-Json | Where-Object { $_.Health -eq 'healthy' } | Measure-Object).Count; Write-Host '[%TIMESTAMP%] [INFO] 等待中... !WAITED!s 健康: '$h'/4'"
  if !WAITED! LSS 120 goto WAIT_INFRA

  :INFRA_READY
  echo [%TIMESTAMP%] [OK] 基础设施启动完成
)

REM -----------------------------------------------------------------------------
REM  4. 编译公共模块
REM -----------------------------------------------------------------------------
if /I not "%1"=="frontend" if /I not "%1"=="infra" (
  echo [%TIMESTAMP%] [INFO] 步骤 4/6 - 编译公共模块（首次 3-5 分钟）
  cd /d "%BACKEND_DIR%"
  call mvn -q -pl ydsz-common,ydsz-literule -am install -DskipTests
  if errorlevel 1 (echo [ERROR] 公共模块编译失败 & exit /b 1)
  echo [%TIMESTAMP%] [OK] 公共模块编译完成
)

REM -----------------------------------------------------------------------------
REM  5. 启动 11 个后端服务
REM -----------------------------------------------------------------------------
if /I not "%1"=="frontend" if /I not "%1"=="infra" (
  echo [%TIMESTAMP%] [INFO] 步骤 5/6 - 启动 11 个后端微服务

  REM 用 PowerShell 启动（命令行参数最稳的方式）
  REM 启动顺序：gateway(9000) -> userinfo(9001) / system(9002) / project(9003) / message(9004)
  REM            -> cronjob(9005) / workflow(9006) / agent(9007) / nextwiki(8800)
  REM            -> sales(9010) / finance(9011)
  powershell -NoProfile -Command ^
    "$ErrorActionPreference = 'SilentlyContinue';" ^
    "$modules = @('ydsz-gateway','ydsz-userinfo','ydsz-system','ydsz-project','ydsz-message','ydsz-cronjob','ydsz-workflow','ydsz-agent','ydsz-nextwiki','ydsz-sales','ydsz-finance');" ^
    "$ports = @(9000,9001,9002,9003,9004,9005,9006,9007,8800,9010,9011);" ^
    "$logDir = '%LOG_DIR%';" ^
    "$backendDir = '%BACKEND_DIR%';" ^
    "foreach ($m in $modules) {" ^
    "  $logFile = Join-Path $logDir ($m + '.log');" ^
    "  $pidFile = Join-Path $logDir ($m + '.pid');" ^
    "  $errFile = $logFile + '.err';" ^
    "  Write-Host ('[{0}] [INFO]   - 启动 {1}' -f (Get-Date -Format 'HH:mm:ss'), $m);" ^
    "  $proc = Start-Process -FilePath 'mvn' -ArgumentList @('-pl', $m, 'spring-boot:run') -WorkingDirectory $backendDir -RedirectStandardOutput $logFile -RedirectStandardError $errFile -PassThru -WindowStyle Hidden;" ^
    "  $proc.Id | Out-File -FilePath $pidFile -Encoding ASCII" ^
    "}"

  echo [%TIMESTAMP%] [INFO] 等待服务健康（60-120s）...
  timeout /t 90 /nobreak >nul
  echo [%TIMESTAMP%] [OK] 11 个后端服务已在后台启动
)

REM -----------------------------------------------------------------------------
REM  6. 启动前端
REM -----------------------------------------------------------------------------
if /I not "%1"=="backend" if /I not "%1"=="infra" (
  echo [%TIMESTAMP%] [INFO] 步骤 6/6 - 启动前端
  cd /d "%FRONTEND_DIR%"
  if not exist "node_modules" (
    echo [%TIMESTAMP%] [INFO] 首次安装依赖（1-2 分钟）...
    call pnpm install
  )
  start "pmis-frontend" /B cmd /c "pnpm dev > %LOG_DIR%\frontend.log 2>&1"
  echo [%TIMESTAMP%] [OK] 前端已启动
)

echo.
echo ============================================================
echo  PMIS 启动完成！
echo.
echo   前端地址:        http://localhost:5173
echo   API 网关:        http://localhost:9000
echo   Nacos 控制台:    http://127.0.0.1:8848/nacos  (%NACOS_USERNAME%/%NACOS_PASSWORD%)
echo   MinIO 控制台:    http://127.0.0.1:9101  (%MINIO_ROOT_USER%/%MINIO_ROOT_PASSWORD%)
echo.
echo   日志目录:        %LOG_DIR%
echo   停止命令:        deploy\windows\scripts\stop-all.bat
echo ============================================================
endlocal
