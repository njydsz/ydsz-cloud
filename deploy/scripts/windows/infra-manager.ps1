# =============================================================================
#  YDSZ PMIS - Windows 中间件统一管理脚本
# -----------------------------------------------------------------------------
#  用法:   .\infra-manager.ps1 {start|stop|status|restart} [middleware]
#  示例:   .\infra-manager.ps1 start all
#          .\infra-manager.ps1 status
#          .\infra-manager.ps1 stop postgres
#  middleware: postgres | redis | nacos | minio | seata |
#              rocketmq | xxl-job | elasticsearch | all
# =============================================================================
[CmdletBinding()]
param(
    [Parameter(Position=0)][ValidateSet('start','stop','status','restart','')][string]$Action = 'status',
    [Parameter(Position=1)][string]$Target = 'all'
)

$ErrorActionPreference = 'Continue'

# 服务名映射
$Services = @{
    'postgres'      = 'postgresql-x64-18'        # 或 'PostgreSQL'
    'redis'         = 'Redis'                    # 或 'redis'
    'nacos'         = 'nacos'
    'minio'         = 'minio'
    'seata'         = 'seata'
    'rocketmq'      = @('rocketmq-namesrv','rocketmq-broker')
    'xxl-job'       = 'xxl-job'
    'elasticsearch' = 'elasticsearch'
}

function Write-Step  { param($m) Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] [INFO] $m" -ForegroundColor Cyan }
function Write-OK    { param($m) Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] [OK]   $m" -ForegroundColor Green }
function Write-Warn  { param($m) Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] [WARN] $m" -ForegroundColor Yellow }
function Write-Err   { param($m) Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] [ERR]  $m" -ForegroundColor Red }

function Get-SvcState {
    param($name)
    try {
        $svc = Get-Service -Name $name -ErrorAction Stop
        return $svc.Status.ToString()
    } catch {
        return 'NotInstalled'
    }
}

function Start-Svc {
    param($name, $label)
    $state = Get-SvcState $name
    if ($state -eq 'Running') {
        Write-Step "$label 已在运行"
        return
    }
    if ($state -eq 'NotInstalled') {
        Write-Err "$label 服务未安装（$name）"
        return
    }
    Write-Step "启动 $label..."
    try {
        Start-Service -Name $name -ErrorAction Stop
        Start-Sleep -Seconds 2
        Write-OK "$label 已启动"
    } catch {
        Write-Err "启动失败：$_"
    }
}

function Stop-Svc {
    param($name, $label)
    $state = Get-SvcState $name
    if ($state -eq 'Stopped' -or $state -eq 'NotInstalled') {
        Write-Step "$label 已停止或未安装"
        return
    }
    Write-Step "停止 $label..."
    try {
        Stop-Service -Name $name -Force -ErrorAction Stop
        Write-OK "$label 已停止"
    } catch {
        Write-Err "停止失败：$_"
    }
}

function Show-Status {
    param($name, $label)
    $state = Get-SvcState $name
    $color = if ($state -eq 'Running') { 'Green' } elseif ($state -eq 'Stopped') { 'Yellow' } else { 'Red' }
    Write-Host "  {0,-15} {1}" -f $label, $state -ForegroundColor $color
}

# =============================================================================
#  主逻辑
# =============================================================================
switch ($Action) {
    'start' {
        switch ($Target) {
            'all' {
                Start-Svc $Services['postgres'] 'PostgreSQL'
                Start-Svc $Services['redis']    'Redis'
                Start-Svc $Services['nacos']    'Nacos'
                Start-Svc $Services['minio']    'MinIO'
                Start-Svc $Services['seata']    'Seata'
                foreach ($rmq in $Services['rocketmq']) { Start-Svc $rmq "RocketMQ-$rmq" }
                Start-Svc $Services['xxl-job']  'XXL-Job'
                Start-Svc $Services['elasticsearch'] 'Elasticsearch'
            }
            default {
                if ($Services.ContainsKey($Target)) {
                    if ($Target -eq 'rocketmq') {
                        foreach ($rmq in $Services[$Target]) { Start-Svc $rmq "RocketMQ-$rmq" }
                    } else {
                        Start-Svc $Services[$Target] $Target
                    }
                } else { Write-Err "未知中间件: $Target" }
            }
        }
    }
    'stop' {
        switch ($Target) {
            'all' {
                Stop-Svc $Services['elasticsearch'] 'Elasticsearch'
                Stop-Svc $Services['xxl-job']  'XXL-Job'
                foreach ($rmq in $Services['rocketmq']) { Stop-Svc $rmq "RocketMQ-$rmq" }
                Stop-Svc $Services['seata']    'Seata'
                Stop-Svc $Services['minio']    'MinIO'
                Stop-Svc $Services['nacos']    'Nacos'
                Stop-Svc $Services['redis']    'Redis'
                Stop-Svc $Services['postgres'] 'PostgreSQL'
            }
            default {
                if ($Services.ContainsKey($Target)) {
                    if ($Target -eq 'rocketmq') {
                        foreach ($rmq in $Services[$Target]) { Stop-Svc $rmq "RocketMQ-$rmq" }
                    } else {
                        Stop-Svc $Services[$Target] $Target
                    }
                } else { Write-Err "未知中间件: $Target" }
            }
        }
    }
    'status' {
        Write-Host "================== 中间件状态 ==================" -ForegroundColor Cyan
        Show-Status $Services['postgres'] 'PostgreSQL'
        Show-Status $Services['redis']    'Redis'
        Show-Status $Services['nacos']    'Nacos'
        Show-Status $Services['minio']    'MinIO'
        Show-Status $Services['seata']    'Seata'
        foreach ($rmq in $Services['rocketmq']) { Show-Status $rmq "RocketMQ-$rmq" }
        Show-Status $Services['xxl-job']  'XXL-Job'
        Show-Status $Services['elasticsearch'] 'Elasticsearch'
        Write-Host "=================================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "端口连通性：" -ForegroundColor Cyan
        $ports = @(5432, 6379, 8848, 9100, 9101, 8091, 9876, 10911, 9100, 9200)
        foreach ($p in $ports) {
            $c = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
            $text = if ($c) { 'OK ' } else { 'X  ' }
            $color = if ($c) { 'Green' } else { 'Red' }
            Write-Host ("  端口 {0,-5} {1}" -f $p, $text) -ForegroundColor $color
        }
    }
    'restart' {
        & "$PSCommandPath" stop $Target
        Start-Sleep -Seconds 3
        & "$PSCommandPath" start $Target
    }
    default {
        Write-Host "用法: .\infra-manager.ps1 {start|stop|status|restart} [middleware]"
        Write-Host "      middleware: postgres|redis|nacos|minio|seata|rocketmq|xxl-job|elasticsearch|all"
    }
}
