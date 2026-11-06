# Start all 10 backend services in local dev mode (without Nacos)
# Each service uses the local-dev profile and imports config from docs/nacos-config/
#
# 启动方式：顺序启动 + 端口就绪门控，未就绪的服务会被抓线程栈并回收，避免堆积。
# 配置目录同时以环境变量和 Spring 命令行属性两种形式传入：application-dev.yml 的
# spring.config.import 依赖 ${YDSZ_NACOS_CONFIG_DIR} 占位符；一旦解析不到，optional: 会静默跳过
# 整个共享配置，表现为 datasource/queue/JWT 一起回落到 fail-closed 报错。命令行属性优先级最高，
# 可保证占位符必然解析到绝对路径。
#
# 本文件必须保存为「UTF-8 with BOM」：Windows PowerShell 5.1 无 BOM 时会按本地 ANSI 码页解码中文注释，
# 并吞掉紧邻下一行的首字符（曾导致 $env:YDSZ_NACOS_CONFIG_DIR 赋值失效，10 个服务全部因配置缺失启动失败）。

$JAVA = "C:\Program Files\Amazon Corretto\jdk21.0.8_9\bin\java.exe"
$BASE = "D:\Code\open\ydsz-cloud"
$LOG_DIR = "$BASE\logs"
$PROFILE = "dev"
$READY_TIMEOUT_SEC = 120

# 各模块 application-dev.yml 通过 spring.config.import 引入共享配置，
# 从仓库根目录启动时必须显式指定该目录，否则配置静默缺失
$NACOS_CONFIG_DIR = "$BASE\docs\nacos-config"
$env:YDSZ_NACOS_CONFIG_DIR = $NACOS_CONFIG_DIR

# 本地联调：网关 IP 白名单默认只放行内网段（ydsz-gateway.yml 的 YDSZ_GATEWAY_IP_WHITELIST 占位符），
# 因此 127.0.0.1 走网关会被判 40301 IP_FORBIDDEN。这里只在本机启动脚本内放开回环，不改动入库的生产配置。
$env:YDSZ_GATEWAY_IP_WHITELIST = "127.0.0.1,10.0.0.0/8,172.16.0.0/12"

# 凭据以 OS 环境变量注入（与 docs/本地开发环境.md 等价的另一种方式）：
# 网关/签发方读取 ${YDSZ_JWT_SECRET} 等占位符，值仍保存在未入库的 ydsz-config.local.yml 中，
# 不落盘到本脚本，避免脚本入库时泄漏密钥。
$localCredFile = Join-Path $NACOS_CONFIG_DIR "ydsz-config.local.yml"
if (Test-Path $localCredFile) {
    Get-Content $localCredFile | ForEach-Object {
        if ($_ -match '^\s*([A-Z0-9_]+)\s*:\s*"?(.*?)"?\s*$') {
            $key = $matches[1]
            $val = $matches[2]
            if ($val -ne '') {
                [Environment]::SetEnvironmentVariable($key, $val, 'Process')
            }
        }
    }
    Write-Host "Loaded credential keys from $localCredFile" -ForegroundColor DarkGray
} else {
    Write-Host "WARNING: $localCredFile not found; fail-closed services will refuse to start." -ForegroundColor Yellow
}

# Ensure log directory exists
New-Item -ItemType Directory -Force -Path $LOG_DIR | Out-Null

# Service definitions: name, jar path, port, extra JVM args
$services = @(
    @{ Name = "system";     Jar = "$BASE\ydsz-system\ydsz-system-web\target\ydsz-system-web-exec.jar";       Port = 9001; Args = "" },
    @{ Name = "userinfo";   Jar = "$BASE\ydsz-userinfo\ydsz-userinfo-web\target\ydsz-userinfo-web-exec.jar"; Port = 9002; Args = "" },
    @{ Name = "nextwiki";   Jar = "$BASE\ydsz-nextwiki\ydsz-nextwiki-web\target\ydsz-nextwiki-web-exec.jar"; Port = 9003; Args = "" },
    @{ Name = "message";    Jar = "$BASE\ydsz-message\ydsz-message-web\target\ydsz-message-web-exec.jar";    Port = 9004; Args = "" },
    @{ Name = "workflow";   Jar = "$BASE\ydsz-workflow\ydsz-workflow-web\target\ydsz-workflow-web-exec.jar"; Port = 9005; Args = "" },
    @{ Name = "cronjob";    Jar = "$BASE\ydsz-cronjob\ydsz-cronjob-web\target\ydsz-cronjob-web-exec.jar"; Port = 9006; Args = "" },
    @{ Name = "literule";   Jar = "$BASE\ydsz-literule\ydsz-literule-web\target\ydsz-literule-web-exec.jar"; Port = 9007; Args = "" },
    @{ Name = "agent";      Jar = "$BASE\ydsz-agent\ydsz-agent-web\target\ydsz-agent-web-exec.jar";          Port = 9008; Args = "" },
    @{ Name = "generator";  Jar = "$BASE\ydsz-generator\ydsz-generator-web\target\ydsz-generator-web-exec.jar"; Port = 9009; Args = "" },
    @{ Name = "gateway";    Jar = "$BASE\ydsz-gateway\target\ydsz-gateway.jar";                 Port = 9010; Args = "" }
)

$results = @()

foreach ($svc in $services) {
    $name = $svc.Name
    $jar = $svc.Jar
    $port = $svc.Port
    $extraArgs = $svc.Args
    $logOut = "$LOG_DIR\${name}-stdout.log"
    $logErr = "$LOG_DIR\${name}-stderr.log"

    if (-not (Test-Path $jar)) {
        Write-Host "[$name] JAR NOT FOUND: $jar" -ForegroundColor Red
        $results += @{ Name = $name; Port = $port; Status = "JAR-NOT-FOUND" }
        continue
    }

    $jvmArgs = "-Xms256m -Xmx512m --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED"
    $springArgs = "--spring.profiles.active=$PROFILE --server.port=$port --ydsz.local-dev=true --YDSZ_NACOS_CONFIG_DIR=$NACOS_CONFIG_DIR"

    Write-Host "[$name] Starting on port $port..." -ForegroundColor Cyan

    $proc = Start-Process -FilePath $JAVA `
        -ArgumentList @("$jvmArgs -jar `"$jar`" $springArgs $extraArgs") `
        -WorkingDirectory $BASE `
        -RedirectStandardOutput $logOut `
        -RedirectStandardError $logErr `
        -WindowStyle Hidden `
        -PassThru

    # 端口就绪门控：等到监听再启动下一个服务，避免并发启动导致的配置加载失败。
    # 探测必须用 TCP 连接测试：Get-NetTCPConnection 走 CIM，负载高时会漏看已监听的端口，
    # 曾把已正常启动并监听的服务判为 TIMEOUT 后强制回收（见 logs/*-threaddump.txt）。
    $deadline = (Get-Date).AddSeconds($READY_TIMEOUT_SEC)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        if ($proc.HasExited) { break }
        $client = New-Object System.Net.Sockets.TcpClient
        try {
            $async = $client.BeginConnect('127.0.0.1', $port, $null, $null)
            if ($async.AsyncWaitHandle.WaitOne(2000) -and $client.Connected) {
                $ready = $true
            }
        } catch {
            # 端口尚未监听：连接被拒绝属正常轮询状态
        } finally {
            $client.Close()
        }
        if ($ready) { break }
        Start-Sleep -Seconds 2
    }

    if ($ready) {
        Write-Host "[$name] READY on port $port (PID $($proc.Id))" -ForegroundColor Green
        $results += @{ Name = $name; Port = $port; Status = "UP" }
    } elseif ($proc.HasExited) {
        Write-Host "[$name] FAILED (exited with code $($proc.ExitCode)) - see $logOut" -ForegroundColor Red
        $results += @{ Name = $name; Port = $port; Status = "EXITED" }
    } else {
        # 未就绪：先留一份线程栈用于定位卡点，再回收进程，避免后续服务在残留 JVM 下继续劣化
        $jstack = Join-Path (Split-Path $JAVA -Parent) "jstack.exe"
        if (Test-Path $jstack) {
            & $jstack $proc.Id > "$LOG_DIR\${name}-threaddump.txt" 2>&1
        }
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        Write-Host "[$name] TIMEOUT after ${READY_TIMEOUT_SEC}s - port $port not listening (thread dump: $LOG_DIR\${name}-threaddump.txt)" -ForegroundColor Yellow
        $results += @{ Name = $name; Port = $port; Status = "TIMEOUT" }
    }
}

Write-Host ""
Write-Host "===== Summary =====" -ForegroundColor White
$results | ForEach-Object {
    $color = if ($_.Status -eq "UP") { "Green" } else { "Red" }
    Write-Host ("  {0,-10} :{1}  {2}" -f $_.Name, $_.Port, $_.Status) -ForegroundColor $color
}
Write-Host ""
Write-Host "Monitor logs with: Get-Content $LOG_DIR\<service>-stdout.log -Wait"
