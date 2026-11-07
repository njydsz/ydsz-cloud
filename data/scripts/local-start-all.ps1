# =====================================================================
#  本地一键启动脚本：Gateway + 9 个业务服务
#  - 使用 -exec.jar（Spring Boot fat jar）
#  - 每个服务日志输出到 logs/{service}.log
#  - 设置 YDSZ_NACOS_CONFIG_DIR 绝对路径，避免工作目录依赖
# =====================================================================
$ErrorActionPreference = "Stop"

$root     = "D:\Code\open\ydsz-cloud"
$cfgDir   = "$root\docs\nacos-config"
$logDir   = "$root\logs"
$javaExe  = "C:\Program Files\Amazon Corretto\jdk21.0.8_9\bin\java.exe"

if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# 服务清单：名称 | jar 路径 | 端口
$serviceDefs = @(
    @("gateway",   "$root\ydsz-gateway\target\ydsz-gateway.jar",                                  9000),
    @("system",    "$root\ydsz-system\ydsz-system-web\target\ydsz-system-web-exec.jar",           9001),
    @("userinfo",  "$root\ydsz-userinfo\ydsz-userinfo-web\target\ydsz-userinfo-web-exec.jar",     9002),
    @("nextwiki",  "$root\ydsz-nextwiki\ydsz-nextwiki-web\target\ydsz-nextwiki-web-exec.jar",     9003),
    @("message",   "$root\ydsz-message\ydsz-message-web\target\ydsz-message-web-exec.jar",        9004),
    @("workflow",  "$root\ydsz-workflow\ydsz-workflow-web\target\ydsz-workflow-web-exec.jar",     9005),
    @("cronjob",   "$root\ydsz-cronjob\ydsz-cronjob-web\target\ydsz-cronjob-web-exec.jar",        9006),
    @("literule",  "$root\ydsz-literule\ydsz-literule-web\target\ydsz-literule-web-exec.jar",     9007),
    @("agent",     "$root\ydsz-agent\ydsz-agent-web\target\ydsz-agent-web-exec.jar",              9008),
    @("generator", "$root\ydsz-generator\ydsz-generator-web\target\ydsz-generator-web-exec.jar",  9009)
)

$started = @()
foreach ($def in $serviceDefs) {
    $name = $def[0]
    $jar  = $def[1]
    $port = $def[2]
    $logFile = "$logDir\$name.log"

    if (-not (Test-Path $jar)) {
        Write-Host "[$name] SKIP - jar not found: $jar" -ForegroundColor Yellow
        continue
    }

    $listening = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue
    if ($listening) {
        Write-Host "[$name] Port $port already listening, skip start." -ForegroundColor Cyan
        $started += [pscustomobject]@{ name = $name; port = $port; status = "already-running" }
        continue
    }

    Write-Host "[$name] Starting on port $port, log -> $logFile" -ForegroundColor Green
    $env:YDSZ_NACOS_CONFIG_DIR = $cfgDir
    $proc = Start-Process -FilePath $javaExe `
        -ArgumentList "-jar", "`"$jar`"" `
        -WorkingDirectory (Split-Path $jar -Parent) `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError "$logFile.err" `
        -PassThru -WindowStyle Hidden
    $started += [pscustomobject]@{ name = $name; port = $port; status = "started"; pid = $proc.Id }
}

$started | ConvertTo-Json | Set-Content "$logDir\started.json"
Write-Host ""
Write-Host "=== Started services ===" -ForegroundColor Cyan
$started | ForEach-Object { Write-Host "$($_.name) : port $($_.port) : $($_.status)" }
