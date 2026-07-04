# =============================================================================
#  YDSZ PMIS - Windows 中间件一键安装脚本
# -----------------------------------------------------------------------------
#  适用:    Windows Server 2019+ / Windows 10+ (x64)
#  权限:    需要管理员 PowerShell
#  用法:    .\install-pmis-infra.ps1 [-NoStart] [-Skip postgres,redis,...] [-Uninstall]
#           -NoStart   : 只安装不启动
#           -Skip      : 跳过中间件（逗号分隔）
#           -Uninstall : 卸载
#  说明:    默认安装到 C:\pmis，数据 C:\pmis-data，日志 C:\pmis-logs
#           需要 nssm（https://nssm.cc）用于注册 Windows 服务
# =============================================================================
[CmdletBinding()]
param(
    [switch]$NoStart,
    [switch]$Uninstall,
    [string]$Skip = '',
    [string]$InstallHome = 'C:\pmis',
    [string]$DataHome = 'C:\pmis-data',
    [string]$LogHome = 'C:\pmis-logs'
)

$ErrorActionPreference = 'Stop'

# ---------- 版本 ----------
$PG_VERSION = '18.0'
$REDIS_VERSION = '7.4.1'
$NACOS_VERSION = '2.4.3'
$MINIO_VERSION = '2025-04-01T00-00-00Z'
$SEATA_VERSION = '2.5.0'
$ROCKETMQ_VERSION = '5.3.2'
$XXL_JOB_VERSION = '2.4.2'
$ES_VERSION = '8.15.3'

# ---------- 颜色 ----------
function Write-Step { param($m) Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] [INFO] $m" -ForegroundColor Cyan }
function Write-OK   { param($m) Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] [OK]   $m" -ForegroundColor Green }
function Write-Warn { param($m) Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] [WARN] $m" -ForegroundColor Yellow }
function Write-Err  { param($m) Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] [ERR]  $m" -ForegroundColor Red }

$SkipList = $Skip -split ',' | Where-Object { $_ } | ForEach-Object { $_.Trim() }
function Test-Skip { param($name) $SkipList -contains $name }

# ---------- 管理员检查 ----------
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Err "请以管理员身份运行 PowerShell"
    exit 1
}

# ---------- 下载目录 ----------
$DownloadDir = Join-Path $env:TEMP "pmis-install"
New-Item -ItemType Directory -Force -Path $DownloadDir | Out-Null

# ---------- 解压 zip/tar.gz 的辅助函数 ----------
function Expand-ArchiveEx {
    param($Path, $Destination)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($Path, $Destination)
}

# ---------- nssm 检测/安装 ----------
function Install-Nssm {
    $nssm = Get-Command nssm -ErrorAction SilentlyContinue
    if (-not $nssm) {
        Write-Step "下载 nssm（用于注册 Windows 服务）..."
        $url = "https://nssm.cc/release/nssm-2.24.zip"
        $zip = Join-Path $DownloadDir "nssm.zip"
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $DownloadDir
        $nssmExe = Get-ChildItem $DownloadDir -Recurse -Filter nssm.exe | Where-Object { $_.DirectoryName -match 'win64' } | Select-Object -First 1
        if ($nssmExe) {
            Copy-Item $nssmExe.FullName "$env:WINDIR\System32\nssm.exe" -Force
            Write-OK "nssm 已安装到 System32"
        } else {
            Write-Warn "未找到 nssm.exe，请手动下载并放到 PATH 中"
        }
    } else {
        Write-OK "nssm 已就绪"
    }
}

# =============================================================================
#  PostgreSQL 18
# =============================================================================
function Install-Postgres {
    if (Test-Skip 'postgres') { return }
    Write-Step "安装 PostgreSQL $PG_VERSION..."

    if (Get-Command psql -ErrorAction SilentlyContinue) {
        Write-OK "PostgreSQL 已安装"
    } else {
        Write-Warn "请从 https://www.enterprisedb.com/download-postgresql-binaries 下载 windows-x64 zip"
        Write-Warn "解压到 $InstallHome\postgresql-$PG_VERSION"
        Write-Warn "将 bin 目录加到 PATH"
        Write-Warn "运行 initdb / pg_ctl 注册服务后重新执行本脚本"
        return
    }

    # 复制配置
    $pgData = "C:\Program Files\PostgreSQL\$PG_VERSION\data"
    $srcConf = Join-Path $PSScriptRoot "..\infra\postgres\postgresql.conf"
    $srcHba  = Join-Path $PSScriptRoot "..\infra\postgres\pg_hba.conf"
    if (Test-Path $pgData) {
        Copy-Item "$pgData\postgresql.conf" "$pgData\postgresql.conf.bak" -Force
        Copy-Item "$pgData\pg_hba.conf" "$pgData\pg_hba.conf.bak" -Force
        Copy-Item $srcConf "$pgData\postgresql.conf" -Force
        Copy-Item $srcHba  "$pgData\pg_hba.conf" -Force
    }

    # 注册服务（如未注册）
    $svc = Get-Service -Name 'postgresql-x64-18' -ErrorAction SilentlyContinue
    if (-not $svc) {
        & "C:\Program Files\PostgreSQL\$PG_VERSION\bin\pg_ctl.exe" `
            register -N postgresql-x64-18 `
            -D "$pgData" `
            -U "NT AUTHORITY\NetworkService"
    }

    if (-not $NoStart) {
        Start-Service postgresql-x64-18
    }

    # 创建用户与数据库
    $env:PGPASSWORD = ''
    & "C:\Program Files\PostgreSQL\$PG_VERSION\bin\psql.exe" -U postgres -c "CREATE USER pmis WITH PASSWORD 'pmis123';" 2>$null
    & "C:\Program Files\PostgreSQL\$PG_VERSION\bin\psql.exe" -U postgres -c "CREATE DATABASE ydsz_pmis OWNER pmis ENCODING 'UTF8';" 2>$null
    & "C:\Program Files\PostgreSQL\$PG_VERSION\bin\psql.exe" -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE ydsz_pmis TO pmis;" 2>$null

    # 导入 SQL
    $sql = Join-Path $PSScriptRoot "..\..\..\docs\V1.0.0.sql"
    if (Test-Path $sql) {
        Write-Step "导入初始化 SQL..."
        & "C:\Program Files\PostgreSQL\$PG_VERSION\bin\psql.exe" -h 127.0.0.1 -U pmis -d ydsz_pmis -f $sql 2>&1 | Select-Object -Last 3
    }
    Write-OK "PostgreSQL 安装完成"
}

# =============================================================================
#  Redis
# =============================================================================
function Install-Redis {
    if (Test-Skip 'redis') { return }
    Write-Step "安装 Redis $REDIS_VERSION..."

    if (-not (Test-Path "$InstallHome\redis")) {
        New-Item -ItemType Directory -Force -Path "$InstallHome\redis" | Out-Null
        $zip = Join-Path $DownloadDir "redis.zip"
        $url = "https://github.com/redis/redis/archive/refs/tags/$REDIS_VERSION.zip"
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $DownloadDir

        # 编译或下载预编译
        Write-Warn "Redis for Windows 推荐使用预编译包：https://github.com/tporadowski/redis/releases"
        Write-Warn "下载解压到 $InstallHome\redis 后重新运行本脚本"
        return
    }

    # 复制配置
    $srcConf = Join-Path $PSScriptRoot "..\infra\redis\redis.conf"
    Copy-Item "$InstallHome\redis\redis.conf" "$InstallHome\redis\redis.conf.bak" -Force
    Copy-Item $srcConf "$InstallHome\redis\redis.conf" -Force

    # 注册服务
    $svc = Get-Service -Name 'Redis' -ErrorAction SilentlyContinue
    if (-not $svc) {
        nssm install Redis "$InstallHome\redis\redis-server.exe" "$InstallHome\redis\redis.conf"
        nssm set Redis AppDirectory "$InstallHome\redis"
        nssm set Redis DisplayName "Redis"
        nssm set Redis Start SERVICE_AUTO_START
    }
    if (-not $NoStart) { Start-Service Redis }

    Write-OK "Redis 安装完成"
}

# =============================================================================
#  Nacos
# =============================================================================
function Install-Nacos {
    if (Test-Skip 'nacos') { return }
    Write-Step "安装 Nacos $NACOS_VERSION..."

    if (-not (Test-Path "$InstallHome\nacos")) {
        $zip = Join-Path $DownloadDir "nacos.zip"
        $url = "https://github.com/alibaba/nacos/releases/download/$NACOS_VERSION/nacos-server-$NACOS_VERSION.zip"
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $InstallHome
        Rename-Item "$InstallHome\nacos" "$InstallHome\nacos" -Force
    }

    # 复制配置
    $srcConf = Join-Path $PSScriptRoot "..\infra\nacos\application.properties"
    Copy-Item "$InstallHome\nacos\conf\application.properties" "$InstallHome\nacos\conf\application.properties.bak" -Force
    Copy-Item $srcConf "$InstallHome\nacos\conf\application.properties" -Force

    # JDK 检测
    $java = (Get-Command java -ErrorAction SilentlyContinue).Source
    if (-not $java) {
        Write-Err "未找到 Java，请先安装 JDK 21"
        return
    }

    # 注册服务
    $svc = Get-Service -Name 'nacos' -ErrorAction SilentlyContinue
    if (-not $svc) {
        nssm install nacos "$InstallHome\nacos\bin\startup.cmd" "-m standalone"
        nssm set nacos AppDirectory "$InstallHome\nacos\bin"
        nssm set nacos DisplayName "Nacos Server"
        nssm set nacos Start SERVICE_AUTO_START
        nssm set nacos AppEnvironmentExtra "JAVA_HOME=$env:JAVA_HOME"
    }
    if (-not $NoStart) { Start-Service nacos }

    Write-OK "Nacos 安装完成（端口 8848）"
}

# =============================================================================
#  MinIO
# =============================================================================
function Install-MinIO {
    if (Test-Skip 'minio') { return }
    Write-Step "安装 MinIO $MINIO_VERSION..."

    if (-not (Test-Path "$InstallHome\minio\minio.exe")) {
        New-Item -ItemType Directory -Force -Path "$InstallHome\minio" | Out-Null
        $url = "https://dl.min.io/server/minio/release/windows-amd64/minio.exe"
        Invoke-WebRequest -Uri $url -OutFile "$InstallHome\minio\minio.exe" -UseBasicParsing
    }
    New-Item -ItemType Directory -Force -Path "$DataHome\minio" | Out-Null
    New-Item -ItemType Directory -Force -Path "$LogHome\minio" | Out-Null

    $svc = Get-Service -Name 'minio' -ErrorAction SilentlyContinue
    if (-not $svc) {
        nssm install minio "$InstallHome\minio\minio.exe" "server $DataHome\minio --console-address '":9001'""
        nssm set minio AppDirectory "$InstallHome\minio"
        nssm set minio DisplayName "MinIO Object Storage"
        nssm set minio Start SERVICE_AUTO_START
        nssm set minio AppEnvironmentExtra "MINIO_ROOT_USER=minioadmin`nMINIO_ROOT_PASSWORD=minioadmin"
    }
    if (-not $NoStart) { Start-Service minio }

    Write-OK "MinIO 安装完成（API:9100 / Console:9101）"
}

# =============================================================================
#  Seata
# =============================================================================
function Install-Seata {
    if (Test-Skip 'seata') { return }
    Write-Step "安装 Seata $SEATA_VERSION..."

    if (-not (Test-Path "$InstallHome\seata")) {
        $tgz = Join-Path $DownloadDir "seata.zip"
        $url = "https://github.com/apache/incubator-seata/releases/download/v$SEATA_VERSION/apache-seata-$SEATA_VERSION-incubating-bin.zip"
        Invoke-WebRequest -Uri $url -OutFile $tgz -UseBasicParsing
        Expand-Archive -Path $tgz -DestinationPath $InstallHome
        Rename-Item "$InstallHome\apache-seata-$SEATA_VERSION-incubating" "$InstallHome\seata"
    }

    # 复制配置
    $srcDir = Join-Path $PSScriptRoot "..\infra\seata"
    Copy-Item "$InstallHome\seata\conf\application.yml" "$InstallHome\seata\conf\application.yml.bak" -Force
    Copy-Item "$InstallHome\seata\conf\file.conf" "$InstallHome\seata\conf\file.conf.bak" -Force
    Copy-Item "$InstallHome\seata\conf\registry.conf" "$InstallHome\seata\conf\registry.conf.bak" -Force
    Copy-Item "$srcDir\application.yml" "$InstallHome\seata\conf\application.yml" -Force
    Copy-Item "$srcDir\file.conf" "$InstallHome\seata\conf\file.conf" -Force
    Copy-Item "$srcDir\registry.conf" "$InstallHome\seata\conf\registry.conf" -Force

    $svc = Get-Service -Name 'seata' -ErrorAction SilentlyContinue
    if (-not $svc) {
        nssm install seata "$env:JAVA_HOME\bin\java.exe" "-jar `"$InstallHome\seata\target\seata-server.jar`""
        nssm set seata AppDirectory "$InstallHome\seata"
        nssm set seata DisplayName "Seata Server"
        nssm set seata Start SERVICE_AUTO_START
    }
    if (-not $NoStart) { Start-Service seata }

    Write-OK "Seata 安装完成（端口 8091 / 控制台 7091）"
}

# =============================================================================
#  RocketMQ
# =============================================================================
function Install-RocketMQ {
    if (Test-Skip 'rocketmq') { return }
    Write-Step "安装 RocketMQ $ROCKETMQ_VERSION..."

    if (-not (Test-Path "$InstallHome\rocketmq")) {
        $zip = Join-Path $DownloadDir "rocketmq.zip"
        $url = "https://dist.apache.org/repos/dist/release/rocketmq/$ROCKETMQ_VERSION/rocketmq-all-$ROCKETMQ_VERSION-bin-release.zip"
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $InstallHome
        Rename-Item "$InstallHome\rocketmq-all-$ROCKETMQ_VERSION-bin-release" "$InstallHome\rocketmq"
    }

    # 复制 broker.conf
    $srcConf = Join-Path $PSScriptRoot "..\infra\rocketmq\broker.conf"
    Copy-Item "$InstallHome\rocketmq\conf\broker.conf" "$InstallHome\rocketmq\conf\broker.conf.bak" -Force
    Copy-Item $srcConf "$InstallHome\rocketmq\conf\broker.conf" -Force

    New-Item -ItemType Directory -Force -Path "$DataHome\rocketmq" | Out-Null
    New-Item -ItemType Directory -Force -Path "$LogHome\rocketmq" | Out-Null

    $rmqHome = $InstallHome\rocketmq

    # NameServer 服务
    $svc1 = Get-Service -Name 'rocketmq-namesrv' -ErrorAction SilentlyContinue
    if (-not $svc1) {
        nssm install rocketmq-namesrv "$env:JAVA_HOME\bin\java.exe" "-jar `"$rmqHome\lib\rocketmq-namesrv-$ROCKETMQ_VERSION.jar`""
        nssm set rocketmq-namesrv AppDirectory "$rmqHome"
        nssm set rocketmq-namesrv DisplayName "RocketMQ NameServer"
        nssm set rocketmq-namesrv Start SERVICE_AUTO_START
    }
    if (-not $NoStart) { Start-Service rocketmq-namesrv; Start-Sleep 5 }

    # Broker 服务
    $svc2 = Get-Service -Name 'rocketmq-broker' -ErrorAction SilentlyContinue
    if (-not $svc2) {
        nssm install rocketmq-broker "$env:JAVA_HOME\bin\java.exe" "-jar `"$rmqHome\lib\rocketmq-broker-$ROCKETMQ_VERSION.jar`" -c `"$rmqHome\conf\broker.conf`""
        nssm set rocketmq-broker AppDirectory "$rmqHome"
        nssm set rocketmq-broker DisplayName "RocketMQ Broker"
        nssm set rocketmq-broker Start SERVICE_AUTO_START
    }
    if (-not $NoStart) { Start-Service rocketmq-broker }

    Write-OK "RocketMQ 安装完成（NameServer:9876 / Broker:10911）"
}

# =============================================================================
#  XXL-Job
# =============================================================================
function Install-XXLJob {
    if (Test-Skip 'xxl-job') { return }
    Write-Step "安装 XXL-Job $XXL_JOB_VERSION..."

    if (-not (Test-Path "$InstallHome\xxl-job")) {
        New-Item -ItemType Directory -Force -Path "$InstallHome\xxl-job" | Out-Null
        $jar = "$InstallHome\xxl-job\xxl-job-admin-$XXL_JOB_VERSION.jar"
        $url = "https://github.com/xuxueli/xxl-job/releases/download/$XXL_JOB_VERSION/xxl-job-admin-$XXL_JOB_VERSION.jar"
        Invoke-WebRequest -Uri $url -OutFile $jar -UseBasicParsing
    }

    # 复制配置
    $srcConf = Join-Path $PSScriptRoot "..\infra\xxl-job\application.properties"
    Copy-Item $srcConf "$InstallHome\xxl-job\application.properties" -Force

    # 初始化数据库
    $sql = Join-Path $PSScriptRoot "..\infra\xxl-job\tables_xxl_job_pg.sql"
    $pgBin = "C:\Program Files\PostgreSQL\$PG_VERSION\bin\psql.exe"
    if (Test-Path $pgBin) {
        & $pgBin -h 127.0.0.1 -U pmis -d ydsz_pmis -f $sql 2>&1 | Select-Object -Last 5
    }

    $svc = Get-Service -Name 'xxl-job' -ErrorAction SilentlyContinue
    if (-not $svc) {
        nssm install xxl-job "$env:JAVA_HOME\bin\java.exe" "-Xms512m -Xmx512m -jar `"$InstallHome\xxl-job\xxl-job-admin-$XXL_JOB_VERSION.jar`" --spring.config.location=`"$InstallHome\xxl-job\application.properties`""
        nssm set xxl-job AppDirectory "$InstallHome\xxl-job"
        nssm set xxl-job DisplayName "XXL-Job Admin"
        nssm set xxl-job Start SERVICE_AUTO_START
    }
    if (-not $NoStart) { Start-Service xxl-job }

    Write-OK "XXL-Job 安装完成（端口 9100，admin/123456）"
}

# =============================================================================
#  Elasticsearch
# =============================================================================
function Install-Elasticsearch {
    if (Test-Skip 'elasticsearch') { return }
    Write-Step "安装 Elasticsearch $ES_VERSION..."

    if (-not (Test-Path "$InstallHome\elasticsearch")) {
        $tgz = Join-Path $DownloadDir "elasticsearch.zip"
        $url = "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-$ES_VERSION-windows-x86_64.zip"
        Invoke-WebRequest -Uri $url -OutFile $tgz -UseBasicParsing
        Expand-Archive -Path $tgz -DestinationPath $InstallHome
        Rename-Item "$InstallHome\elasticsearch-$ES_VERSION" "$InstallHome\elasticsearch"
    }

    $srcDir = Join-Path $PSScriptRoot "..\infra\elasticsearch"
    Copy-Item "$InstallHome\elasticsearch\config\elasticsearch.yml" "$InstallHome\elasticsearch\config\elasticsearch.yml.bak" -Force
    Copy-Item "$srcDir\elasticsearch.yml" "$InstallHome\elasticsearch\config\elasticsearch.yml" -Force
    New-Item -ItemType Directory -Force -Path "$InstallHome\elasticsearch\config\jvm.options.d" | Out-Null
    Copy-Item "$srcDir\jvm.options.d\heap.options" "$InstallHome\elasticsearch\config\jvm.options.d\heap.options" -Force

    $svc = Get-Service -Name 'elasticsearch' -ErrorAction SilentlyContinue
    if (-not $svc) {
        # ES 不能以 LocalSystem 启动，需创建专用用户
        $esUser = 'elasticsearch-svc'
        if (-not (Get-LocalUser -Name $esUser -ErrorAction SilentlyContinue)) {
            New-LocalUser -Name $esUser -NoPassword -Description "Elasticsearch Service Account" | Out-Null
        }
        nssm install elasticsearch "$InstallHome\elasticsearch\bin\elasticsearch.bat"
        nssm set elasticsearch AppDirectory "$InstallHome\elasticsearch"
        nssm set elasticsearch DisplayName "Elasticsearch"
        nssm set elasticsearch Start SERVICE_AUTO_START
        nssm set elasticsearch ObjectName ".\$esUser"
    }
    if (-not $NoStart) { Start-Service elasticsearch }

    Write-OK "Elasticsearch 安装完成（端口 9200）"
}

# =============================================================================
#  卸载
# =============================================================================
function Uninstall-All {
    Write-Step "卸载全部中间件..."
    foreach ($svc in @('elasticsearch','xxl-job','rocketmq-broker','rocketmq-namesrv','seata','minio','nacos','Redis')) {
        $s = Get-Service -Name $svc -ErrorAction SilentlyContinue
        if ($s) {
            Stop-Service -Name $svc -Force -ErrorAction SilentlyContinue
            nssm remove $svc confirm 2>$null
        }
    }
    if (Get-Service -Name 'postgresql-x64-18' -ErrorAction SilentlyContinue) {
        Stop-Service postgresql-x64-18 -Force -ErrorAction SilentlyContinue
        & "C:\Program Files\PostgreSQL\$PG_VERSION\bin\pg_ctl.exe" unregister -N postgresql-x64-18
    }
    Write-OK "全部服务已卸载（数据保留在 $DataHome）"
}

# =============================================================================
#  主流程
# =============================================================================
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  YDSZ PMIS · Windows 中间件一键安装" -ForegroundColor Cyan
Write-Host "  安装目录: $InstallHome" -ForegroundColor Cyan
Write-Host "  数据目录: $DataHome" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

if ($Uninstall) { Uninstall-All; exit 0 }

Install-Nssm
Install-Postgres
Install-Redis
Install-Nacos
Install-MinIO
Install-Seata
Install-RocketMQ
Install-XXLJob
Install-Elasticsearch

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-OK "全部中间件安装完成！"
Write-Host ""
Write-Host "下一步："
Write-Host "  1. 检查状态: deploy\scripts\windows\infra-manager.ps1 status"
Write-Host "  2. 导入 Nacos 共享配置: deploy\scripts\import-nacos-config.bat"
Write-Host "  3. 启动后端: deploy\scripts\start-all.bat"
Write-Host "============================================================" -ForegroundColor Cyan
