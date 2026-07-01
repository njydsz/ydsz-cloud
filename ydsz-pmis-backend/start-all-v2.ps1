# Pmis backend launcher (PowerShell) - hardened version
# Starts all 13 backend microservices in background, waits for health

$ErrorActionPreference = 'Continue'
$backendDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$logDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\logs"
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# Load env vars into a hashtable
$envMap = @{}
Get-Content (Join-Path $backendDir "dev.env") | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $key = $matches[1].Trim()
        $val = $matches[2].Trim()
        $envMap[$key] = $val
        [System.Environment]::SetEnvironmentVariable($key, $val, 'Process')
    }
}
Write-Host ("Loaded {0} env vars" -f $envMap.Count) -ForegroundColor DarkGray

# module -> jar / port
$services = @(
    @{ name = 'config';       jar = 'ydsz-pmis-config.jar';       port = 9018; startOrder = 1 },
    @{ name = 'file';         jar = 'ydsz-pmis-file.jar';         port = 9019; startOrder = 2 },
    @{ name = 'audit';        jar = 'ydsz-pmis-audit.jar';        port = 9020; startOrder = 3 },
    @{ name = 'user';         jar = 'ydsz-pmis-user.jar';         port = 9002; startOrder = 4 },
    @{ name = 'auth';         jar = 'ydsz-pmis-auth.jar';         port = 9001; startOrder = 5 },
    @{ name = 'workflow';     jar = 'ydsz-pmis-workflow.jar';     port = 9014; startOrder = 6 },
    @{ name = 'notification'; jar = 'ydsz-pmis-notification.jar'; port = 9013; startOrder = 7 },
    @{ name = 'message';      jar = 'ydsz-pmis-message-exec.jar'; port = 9021; startOrder = 8 },
    @{ name = 'project';      jar = 'ydsz-pmis-project-exec.jar'; port = 9015; startOrder = 9 },
    @{ name = 'execution';    jar = 'ydsz-pmis-execution.jar';    port = 9016; startOrder = 10 },
    @{ name = 'agent';        jar = 'ydsz-pmis-agent.jar';        port = 9017; startOrder = 11 },
    @{ name = 'scheduler';    jar = 'ydsz-pmis-scheduler.jar';    port = 9022; startOrder = 12 },
    @{ name = 'gateway';      jar = 'ydsz-pmis-gateway.jar';      port = 9000; startOrder = 13 }
)

# Track PIDs
$pidFile = Join-Path $logDir "pmis-pids.txt"
"" | Set-Content $pidFile

foreach ($svc in $services) {
    $moduleDir = Join-Path $backendDir "ydsz-pmis-$($svc.name)"
    $jarPath = Join-Path $moduleDir "target\$($svc.jar)"
    $logPath = Join-Path $logDir "$($svc.name).log"
    $errPath = Join-Path $logDir "$($svc.name).err.log"

    if (!(Test-Path $jarPath)) {
        Write-Host ("  MISSING JAR: {0}" -f $jarPath) -ForegroundColor Red
        continue
    }

    Write-Host ("[{0:D2}] Starting {1,-12} port={2:D4} ..." -f $svc.startOrder, $svc.name, $svc.port) -ForegroundColor Cyan
    $proc = Start-Process -FilePath "java" `
        -ArgumentList @('-jar', $jarPath) `
        -WorkingDirectory $moduleDir `
        -RedirectStandardOutput $logPath `
        -RedirectStandardError $errPath `
        -PassThru -WindowStyle Hidden
    "$($svc.name)=$($proc.Id)" | Add-Content $pidFile
    Write-Host ("      -> pid {0} log={1}" -f $proc.Id, $logPath) -ForegroundColor DarkGray
    Start-Sleep -Seconds 1
}

Write-Host ""
Write-Host "All services launched. Waiting for readiness ..." -ForegroundColor Green
Start-Sleep -Seconds 3

# Print summary
Write-Host ""
Write-Host "=== Initial port status ===" -ForegroundColor Yellow
foreach ($svc in $services) {
    $port = $svc.port
    $listening = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if ($listening) {
        Write-Host ("  {0,-12} port {1:D4}  LISTENING (pid {2})" -f $svc.name, $port, $listening[0].OwningProcess) -ForegroundColor Green
    } else {
        Write-Host ("  {0,-12} port {1:D4}  NOT YET" -f $svc.name, $port) -ForegroundColor Yellow
    }
}
