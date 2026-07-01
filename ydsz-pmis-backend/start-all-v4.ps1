# Pmis backend launcher (PowerShell) - background job based, fully detached
# Each Java service is launched as a background job, so it survives parent shell exit.

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

# Build a scriptblock for each service that sets env vars and exec java
function Get-SvcLauncher {
    param(
        [string]$JarPath,
        [hashtable]$EnvMap,
        [string]$ServiceName,
        [string]$LogDir
    )
    $setLines = $EnvMap.Keys | ForEach-Object { "[System.Environment]::SetEnvironmentVariable('$_', `"$($EnvMap[$_])`", 'Process')" }
    $outLog = Join-Path $LogDir ("{0}.out.log" -f $ServiceName)
    $errLog = Join-Path $LogDir ("{0}.err.log" -f $ServiceName)
    $script = @"
`$setLines = @(
$(($setLines | ForEach-Object { "    '$_'" }) -join "`n")
)
foreach (`$l in `$setLines) { Invoke-Expression `$l }
`$proc = Start-Process -FilePath 'java' -ArgumentList @('-jar', '$JarPath') -RedirectStandardOutput '$outLog' -RedirectStandardError '$errLog' -WorkingDirectory (Split-Path '$JarPath' -Parent) -PassThru -WindowStyle Hidden
Write-Output "Launched `$($proc.Id) '$ServiceName'"
"@
    return [scriptblock]::Create($script)
}

# First, clean up any existing java processes (except Nacos + IDE)
Write-Host "Cleaning up old Java processes ..." -ForegroundColor Yellow
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
    if ($cmd -notmatch 'nacos-server\.jar' -and $cmd -notmatch 'spring-boot-language-server' -and $cmd -notmatch 'redhat.java' -and $cmd -notmatch 'vscode-spring-boot') {
        Write-Host "  Killing pid=$($_.Id)" -ForegroundColor DarkYellow
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    }
}
Start-Sleep -Seconds 2

# Launch each service as a background job
$jobs = @()
foreach ($svc in $services) {
    $moduleDir = Join-Path $backendDir ("ydsz-pmis-{0}" -f $svc.name)
    $jarPath = Join-Path $moduleDir ("target\{0}" -f $svc.jar)

    if (!(Test-Path $jarPath)) {
        Write-Host ("  MISSING JAR: {0}" -f $jarPath) -ForegroundColor Red
        continue
    }

    Write-Host ("[{0:D2}] Launching {1,-12} port={2:D4} ..." -f $svc.startOrder, $svc.name, $svc.port) -ForegroundColor Cyan
    $launcher = Get-SvcLauncher -JarPath $jarPath -EnvMap $envMap -ServiceName $svc.name -LogDir $logDir
    $job = Start-Job -ScriptBlock $launcher -Name ("pmis-{0}" -f $svc.name)
    $jobs += @{ Name = $svc.name; Job = $job }
    Start-Sleep -Milliseconds 500
}

# Wait for launchers to complete (they don't wait for java to start, just spawn it)
foreach ($j in $jobs) {
    $null = Wait-Job -Job $j.Job -Timeout 30
    Receive-Job -Job $j.Job -Keep | ForEach-Object { Write-Host ("  [launch] " + $_) }
    Remove-Job -Job $j.Job -Force
}

# Wait for services to come up
Write-Host ""
Write-Host "Waiting up to 90s for services to listen on their ports ..." -ForegroundColor Green
$maxWait = 90
$waited = 0
$allUp = $false
while (-not $allUp -and $waited -lt $maxWait) {
    Start-Sleep -Seconds 5
    $waited += 5
    $upCount = 0
    $totalCount = 0
    foreach ($svc in $services) {
        $totalCount++
        $port = $svc.port
        $listening = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
        if ($listening) { $upCount++ }
    }
    Write-Host ("  [{0}s] {1}/{2} ports listening" -f $waited, $upCount, $totalCount) -ForegroundColor Cyan
    if ($upCount -eq $totalCount) { $allUp = $true }
}

# Final status
Write-Host ""
Write-Host "=== Final port status ===" -ForegroundColor Yellow
foreach ($svc in $services) {
    $port = $svc.port
    $listening = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if ($listening) {
        Write-Host ("  {0,-12} port {1:D4}  LISTENING (pid {2})" -f $svc.name, $port, $listening[0].OwningProcess) -ForegroundColor Green
    } else {
        Write-Host ("  {0,-12} port {1:D4}  NOT LISTENING" -f $svc.name, $port) -ForegroundColor Red
    }
}
