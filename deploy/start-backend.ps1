# Start all 13 backend microservices
# Set DB_NAME=ydsz-pmis since the application default is "pmis" but the actual database is "ydsz-pmis"

$ErrorActionPreference = "Stop"
$BackendRoot = "D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$LogsRoot = "D:\Code\ydsz\ydsz-pmis\logs"
$JavaHome = "C:\Program Files\Amazon Corretto\jdk17.0.16_8"

# Create logs directory
if (!(Test-Path $LogsRoot)) {
    New-Item -ItemType Directory -Path $LogsRoot -Force | Out-Null
}

# Common environment variables
$env:DB_NAME = "ydsz-pmis"
$env:DB_USER = "pmis"
$env:DB_PASSWORD = "pmis"
$env:JAVA_HOME = $JavaHome
$env:NACOS_SERVER_ADDR = "127.0.0.1:8848"

# Service list: (module, jar name, port)
$services = @(
    @{ Module = "ydsz-pmis-gateway"; Port = 9000; Order = 1 },
    @{ Module = "ydsz-pmis-auth"; Port = 9001; Order = 2 },
    @{ Module = "ydsz-pmis-user"; Port = 9002; Order = 3 },
    @{ Module = "ydsz-pmis-workflow"; Port = 9007; Order = 4 },
    @{ Module = "ydsz-pmis-notification"; Port = 9009; Order = 5 },
    @{ Module = "ydsz-pmis-config"; Port = 9010; Order = 6 },
    @{ Module = "ydsz-pmis-file"; Port = 9011; Order = 7 },
    @{ Module = "ydsz-pmis-scheduler"; Port = 9012; Order = 8 },
    @{ Module = "ydsz-pmis-message"; Port = 9013; Order = 9 },
    @{ Module = "ydsz-pmis-audit"; Port = 9014; Order = 10 },
    @{ Module = "ydsz-pmis-project"; Port = 9015; Order = 11 },
    @{ Module = "ydsz-pmis-execution"; Port = 9016; Order = 12 },
    @{ Module = "ydsz-pmis-agent"; Port = 9017; Order = 13 }
)

foreach ($svc in $services) {
    $module = $svc.Module
    $port = $svc.Port
    $order = $svc.Order
    $jarPath = "$BackendRoot\$module\target\$module.jar"
    $logFile = "$LogsRoot\$module.log"

    if (!(Test-Path $jarPath)) {
        Write-Host "[$order] SKIP $module - jar not found at $jarPath" -ForegroundColor Yellow
        continue
    }

    Write-Host "[$order] Starting $module on port $port (log: $logFile)" -ForegroundColor Green

    # Start the service in a new PowerShell process
    $arguments = @(
        "-NoProfile",
        "-Command",
        "cd '$BackendRoot'; `$env:DB_NAME='ydsz-pmis'; `$env:DB_USER='pmis'; `$env:DB_PASSWORD='pmis'; `$env:JAVA_HOME='$JavaHome'; `$env:NACOS_SERVER_ADDR='127.0.0.1:8848'; & '$JavaHome\bin\java.exe' -Xms256m -Xmx512m -jar '$jarPath' 2>&1 | Tee-Object -FilePath '$logFile'"
    )

    Start-Process -FilePath "powershell.exe" -ArgumentList $arguments -WindowStyle Hidden
    Start-Sleep -Seconds 5
}

Write-Host ""
Write-Host "All services start commands issued. Check logs in $LogsRoot" -ForegroundColor Cyan
Write-Host "Waiting 60s for services to come up..." -ForegroundColor Cyan
Start-Sleep -Seconds 60

# Check which services are listening
Write-Host ""
Write-Host "Service status check:" -ForegroundColor Cyan
foreach ($svc in $services) {
    $port = $svc.Port
    $conn = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue
    $status = if ($conn) { "UP" } else { "DOWN" }
    $color = if ($conn) { "Green" } else { "Red" }
    Write-Host "  $($svc.Module.PadRight(28)) port $port`: $status" -ForegroundColor $color
}
