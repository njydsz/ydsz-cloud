# Pmis backend launcher (PowerShell) - SCHTASKS based, fully detached from parent shell
# Each service is started via a Windows scheduled task that runs in a separate session.

$ErrorActionPreference = 'Continue'
$backendDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$logDir = Join-Path $backendDir "logs"
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# Load env vars
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

# Build a single batch file that exports env then runs java -jar
function Build-BootstrapBatch {
    param(
        [string]$JarPath,
        [string]$ServiceName
    )
    $childBat = Join-Path $logDir ("child-{0}.bat" -f $ServiceName)
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("@echo off")
    $lines.Add("setlocal")
    foreach ($k in $envMap.Keys) {
        $v = $envMap[$k]
        # Escape special chars
        $vEsc = $v -replace '%', '%%'
        $lines.Add(("set ""{0}={1}""" -f $k, $vEsc))
    }
    $lines.Add(('java -jar "{0}" > "{1}\{2}.out.log" 2> "{1}\{2}.err.log"' -f $JarPath, $logDir, $ServiceName))
    Set-Content -Path $childBat -Value $lines -Encoding ASCII
    return $childBat
}

# module -> jar / port
$services = @(
    @{ name = 'config';       jar = 'ydsz-pmis-config.jar';       port = 9010; startOrder = 1 },
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

$pidFile = Join-Path $logDir "pmis-pids.txt"
"" | Set-Content $pidFile

foreach ($svc in $services) {
    $moduleDir = Join-Path $backendDir ("ydsz-pmis-{0}" -f $svc.name)
    $jarPath = Join-Path $moduleDir ("target\{0}" -f $svc.jar)

    if (!(Test-Path $jarPath)) {
        Write-Host ("  MISSING JAR: {0}" -f $jarPath) -ForegroundColor Red
        continue
    }

    Write-Host ("[{0:D2}] Starting {1,-12} port={2:D4} ..." -f $svc.startOrder, $svc.name, $svc.port) -ForegroundColor Cyan

    $childBat = Build-BootstrapBatch -JarPath $jarPath -ServiceName $svc.name
    $taskName = "pmis-$($svc.name)"

    # Register a one-shot scheduled task that runs in session 0 (background) — fully detached
    $arg = "cmd.exe /c `"$childBat`""
    schtasks /Create /TN $taskName /TR $arg /SC ONCE /ST "00:00" /F /RL HIGHEST | Out-Null
    schtasks /Run /TN $taskName | Out-Null
    Start-Sleep -Milliseconds 800
    schtasks /Delete /TN $taskName /F | Out-Null

    Start-Sleep -Seconds 1
}

Write-Host ""
Write-Host "All services launched. Waiting 20s for readiness ..." -ForegroundColor Green
Start-Sleep -Seconds 20

# Print summary
Write-Host ""
Write-Host "=== Port status ===" -ForegroundColor Yellow
foreach ($svc in $services) {
    $port = $svc.port
    $listening = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if ($listening) {
        Write-Host ("  {0,-12} port {1:D4}  LISTENING (pid {2})" -f $svc.name, $port, $listening[0].OwningProcess) -ForegroundColor Green
    } else {
        Write-Host ("  {0,-12} port {1:D4}  NOT YET" -f $svc.name, $port) -ForegroundColor Yellow
    }
}
