# Pmis backend launcher (PowerShell)
# Starts all 14 backend microservices in background

$ErrorActionPreference = 'Continue'
$backendDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$logDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\logs"
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# Load env vars
Get-Content (Join-Path $backendDir "dev.env") | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}

# module -> jar / port
$services = @(
    @{ name = 'config';       jar = 'ydsz-pmis-config.jar';       port = 9018 },
    @{ name = 'file';         jar = 'ydsz-pmis-file.jar';         port = 9019 },
    @{ name = 'message';      jar = 'ydsz-pmis-message-exec.jar'; port = 9021 },
    @{ name = 'audit';        jar = 'ydsz-pmis-audit.jar';        port = 9020 },
    @{ name = 'user';         jar = 'ydsz-pmis-user.jar';         port = 9002 },
    @{ name = 'auth';         jar = 'ydsz-pmis-auth.jar';         port = 9001 },
    @{ name = 'workflow';     jar = 'ydsz-pmis-workflow.jar';     port = 9014 },
    @{ name = 'notification'; jar = 'ydsz-pmis-notification.jar'; port = 9013 },
    @{ name = 'project';      jar = 'ydsz-pmis-project-exec.jar'; port = 9015 },
    @{ name = 'execution';    jar = 'ydsz-pmis-execution.jar';    port = 9016 },
    @{ name = 'agent';        jar = 'ydsz-pmis-agent.jar';        port = 9017 },
    @{ name = 'scheduler';    jar = 'ydsz-pmis-scheduler.jar';    port = 9022 },
    @{ name = 'gateway';      jar = 'ydsz-pmis-gateway.jar';      port = 9000 }
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
        Write-Host "MISSING JAR: $jarPath"
        continue
    }

    Write-Host "Starting $($svc.name) on port $($svc.port) ..."
    $proc = Start-Process -FilePath "java" `
        -ArgumentList @('-jar', $jarPath) `
        -WorkingDirectory $moduleDir `
        -RedirectStandardOutput $logPath `
        -RedirectStandardError $errPath `
        -PassThru -WindowStyle Hidden
    "$($svc.name)=$($proc.Id)" | Add-Content $pidFile
    Write-Host "  -> pid $($proc.Id) log=$logPath"
    Start-Sleep -Milliseconds 500
}

Write-Host ""
Write-Host "All services launched. PIDs:" -ForegroundColor Green
Get-Content $pidFile
