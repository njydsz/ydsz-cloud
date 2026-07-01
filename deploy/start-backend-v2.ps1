$BackendRoot = "D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$LogsRoot = "D:\Code\ydsz\ydsz-pmis\logs"
$JavaHome = "C:\Program Files\Amazon Corretto\jdk17.0.16_8"

# Create logs directory
if (!(Test-Path $LogsRoot)) {
    New-Item -ItemType Directory -Path $LogsRoot -Force | Out-Null
}

$services = @(
    @{ Module = "ydsz-pmis-gateway"; Port = 9000 },
    @{ Module = "ydsz-pmis-auth"; Port = 9001 },
    @{ Module = "ydsz-pmis-user"; Port = 9002 },
    @{ Module = "ydsz-pmis-workflow"; Port = 9007 },
    @{ Module = "ydsz-pmis-notification"; Port = 9009 },
    @{ Module = "ydsz-pmis-config"; Port = 9010 },
    @{ Module = "ydsz-pmis-file"; Port = 9011 },
    @{ Module = "ydsz-pmis-scheduler"; Port = 9012 },
    @{ Module = "ydsz-pmis-message"; Port = 9013 },
    @{ Module = "ydsz-pmis-audit"; Port = 9014 },
    @{ Module = "ydsz-pmis-project"; Port = 9015 },
    @{ Module = "ydsz-pmis-execution"; Port = 9016 },
    @{ Module = "ydsz-pmis-agent"; Port = 9017 }
)

foreach ($svc in $services) {
    $module = $svc.Module
    $port = $svc.Port
    $jarPath = "$BackendRoot\$module\target\$module.jar"
    $logFile = "$LogsRoot\$module.log"

    if (!(Test-Path $jarPath)) {
        Write-Host "SKIP $module - jar not found" -ForegroundColor Yellow
        continue
    }

    Write-Host "Starting $module on port $port ..." -ForegroundColor Green

    # Build a one-liner that sets env vars and runs the JAR
    $envBlock = "`$env:DB_NAME='ydsz-pmis';`$env:DB_USER='pmis';`$env:DB_PASSWORD='pmis';`$env:NACOS_SERVER_ADDR='127.0.0.1:8848';`$env:JAVA_HOME='$JavaHome'"
    $cmd = "cd '$BackendRoot'; $envBlock; & '$JavaHome\bin\java.exe' -Xms256m -Xmx512m -jar '$jarPath' 2>&1 | Out-File -FilePath '$logFile' -Encoding utf8"

    # Use Start-Process so the process is detached and won't die when this script ends
    $proc = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-Command", $cmd -PassThru -WindowStyle Hidden
    Write-Host "  -> spawned PID $($proc.Id)" -ForegroundColor DarkGreen

    Start-Sleep -Seconds 3
}

Write-Host ""
Write-Host "All services start commands issued. Waiting 60s ..." -ForegroundColor Cyan
Start-Sleep -Seconds 60

# Check status
Write-Host ""
Write-Host "Service status check:" -ForegroundColor Cyan
foreach ($svc in $services) {
    $port = $svc.Port
    $conn = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue
    $status = if ($conn) { "UP" } else { "DOWN" }
    $color = if ($conn) { "Green" } else { "Red" }
    Write-Host "  $($svc.Module.PadRight(28)) port $port : $status" -ForegroundColor $color
}
