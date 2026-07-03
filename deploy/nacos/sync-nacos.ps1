# ============================================================
# PMIS Nacos 配置统一同步脚本
# 合并自: push-configs / push-to-nacos / fix-and-repush / fix-application-yml
# ------------------------------------------------------------
# 统一规范:
#   - Nacos v2 API (发布/删除/查询)
#   - accessToken 认证 (不使用 Basic Auth)
#   - Group = PMIS_GROUP_{ENV} (大写)
#   - Namespace = pmis
#   - 密码从环境变量 NACOS_PASSWORD 读取, 不硬编码
#   - 路径相对脚本所在目录, 不硬编码绝对路径
# ------------------------------------------------------------
# 用法:
#   # 1. 先设置密码环境变量
#   $env:NACOS_PASSWORD = "your-password"
#
#   # 2. 执行 (默认 sync dev)
#   .\sync-nacos.ps1
#   .\sync-nacos.ps1 -Action push   -Env dev
#   .\sync-nacos.ps1 -Action sync   -Env prod
#   .\sync-nacos.ps1 -Action rebuild -Env sit
#
# Action 说明:
#   push    : 仅推送新增/变更的服务配置 (按 MD5 对比, 不含 common)
#   sync    : 推送所有服务配置 + 生成并推送 common 共享配置
#   rebuild : 删除目标环境下所有旧配置后, 重新推送全部 (含 common)
# ============================================================

param(
    [ValidateSet("dev", "sit", "uat", "staging", "prod")]
    [string]$Env = "dev",

    [ValidateSet("push", "sync", "rebuild")]
    [string]$Action = "sync",

    [string]$NacosUrl = "http://127.0.0.1:8848",
    [string]$Username = "nacos",
    [string]$Namespace = "pmis"
)

$ErrorActionPreference = "Continue"
$ProgressPreference    = "SilentlyContinue"

# ============================================================
# 路径解析 (相对脚本所在目录, 不硬编码绝对路径)
# ============================================================
$ScriptDir = $PSScriptRoot
if (-not $ScriptDir) { $ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
$ConfigDir = Join-Path $ScriptDir "config"

# ============================================================
# 服务列表 (13 个微服务)
# ============================================================
$services = @(
    "ydsz-pmis-gateway",
    "ydsz-pmis-auth",
    "ydsz-pmis-user",
    "ydsz-pmis-notification",
    "ydsz-pmis-workflow",
    "ydsz-pmis-project",
    "ydsz-pmis-execution",
    "ydsz-pmis-agent",
    "ydsz-pmis-config",
    "ydsz-pmis-file",
    "ydsz-pmis-audit",
    "ydsz-pmis-message",
    "ydsz-pmis-scheduler"
)

# ============================================================
# common 共享配置模板 (datasource / redis / mybatis-plus / logging)
# ============================================================
$commonTemplate = @'
# ============================================================
# 公共基础配置：pmis-common-{ENV}
# DataId  : pmis-common-{ENV_LOWER}.yaml
# Group   : PMIS_GROUP_{ENV_UPPER}
# Namespace: pmis
# 环境     : {ENV_CN}（{ENV_DESC}）
# 用途     : 各微服务共享的 DB / Redis / MyBatis-Plus / Logging 配置
# ============================================================

spring:
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
    default-property-inclusion: NON_NULL
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME:ydsz-pmis}?useUnicode=true&characterEncoding=utf8&currentSchema=public
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    druid:
      initial-size: {DRUID_INIT}
      min-idle: {DRUID_MIN}
      max-active: {DRUID_MAX}
      max-wait: 60000
      validation-query: SELECT 1
      test-while-idle: true
      filters: stat,wall,slf4j
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
      database: 0
      timeout: {REDIS_TIMEOUT}
      lettuce:
        pool:
          max-active: {REDIS_MAX_ACTIVE}
          max-idle: {REDIS_MAX_IDLE}
          min-idle: {REDIS_MIN_IDLE}
          max-wait: {REDIS_MAX_WAIT}

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    banner: false
    db-config:
      id-type: AUTO
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    db:
      enabled: true
    redis:
      enabled: true
  metrics:
    tags:
      env: ${spring.profiles.active}

logging:
  level:
    root: INFO
    com.njydsz.pmis: {LOG_LEVEL}
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{traceId:-}] [%thread] %logger{36} - %msg%n"

# ------------------------------------------------------------
# 轻量规则引擎（ydsz-pmis-literule）共享配置
# 由 execution / project / agent 等消费方通过 common 配置继承
# ------------------------------------------------------------
pmis:
  literule:
    auto-register-builtin-rules: true
    hot-reload-enabled: true
    stats-enabled: true
    dry-run-enabled: {DRY_RUN}
'@

# 各环境公共配置参数
$envParams = @{
    "dev"     = @{ Init = 5;  Min = 5;  Max = 20; RA = 16; RI = 8;  RMi = 2; RT = "3s"; RW = "3s"; Log = "DEBUG"; DryRun = "true";  CN = "开发";     Desc = "本地开发联调" }
    "sit"     = @{ Init = 5;  Min = 5;  Max = 20; RA = 16; RI = 8;  RMi = 2; RT = "3s"; RW = "3s"; Log = "INFO";  DryRun = "false"; CN = "系统集成"; Desc = "外部依赖全链路测试" }
    "uat"     = @{ Init = 10; Min = 10; Max = 40; RA = 32; RI = 16; RMi = 4; RT = "5s"; RW = "5s"; Log = "INFO";  DryRun = "false"; CN = "用户验收"; Desc = "生产仿真、含脱敏数据" }
    "staging" = @{ Init = 10; Min = 10; Max = 40; RA = 32; RI = 16; RMi = 4; RT = "5s"; RW = "5s"; Log = "INFO";  DryRun = "false"; CN = "预发布";   Desc = "生产仿真、含脱敏数据" }
    "prod"    = @{ Init = 20; Min = 20; Max = 80; RA = 64; RI = 32; RMi = 8; RT = "5s"; RW = "5s"; Log = "WARN";  DryRun = "false"; CN = "生产";     Desc = "线上真实数据" }
}

# ============================================================
# 工具函数
# ============================================================

function Write-Banner {
    param([string]$Text)
    Write-Host ""
    Write-Host ("=" * 60) -ForegroundColor Cyan
    Write-Host (" " + $Text) -ForegroundColor Cyan
    Write-Host ("=" * 60) -ForegroundColor Cyan
}

function Write-Step {
    param([string]$Text)
    Write-Host ""
    Write-Host "[STEP] $Text" -ForegroundColor Yellow
}

# 登录 Nacos 获取 accessToken (v1 auth 接口, v2 复用此 token)
function Get-NacosToken {
    param(
        [string]$BaseUrl,
        [string]$User,
        [string]$Pwd
    )
    $uri = "$BaseUrl/nacos/v1/auth/users/login"
    try {
        $resp = Invoke-RestMethod -Uri $uri -Method Post `
            -ContentType "application/x-www-form-urlencoded" `
            -Body @{ username = $User; password = $Pwd } -TimeoutSec 10
        if ($resp.accessToken) {
            return [string]$resp.accessToken
        }
    } catch {
        Write-Host "[FAIL] 登录失败: $($_.Exception.Message)" -ForegroundColor Red
    }
    return $null
}

# 计算 UTF-8 字符串的 MD5 (与 Nacos 服务端算法一致)
function Get-StringMd5 {
    param([string]$Text)
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    $hash = $md5.ComputeHash($bytes)
    return [BitConverter]::ToString($hash).Replace("-", "").ToLower()
}

# 发布配置 (v2 API)
function Publish-NacosConfig {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [string]$Ns,
        [string]$Group,
        [string]$DataId,
        [string]$Content
    )
    $uri = "$BaseUrl/nacos/v2/cs/config?accessToken=$Token"
    $body = @{
        dataId      = $DataId
        group       = $Group
        namespaceId = $Ns
        type        = "yaml"
        content     = $Content
    }
    try {
        $resp = Invoke-WebRequest -Uri $uri -Method Post -Body $body `
            -ContentType "application/x-www-form-urlencoded" `
            -UseBasicParsing -TimeoutSec 30
        $json = $resp.Content | ConvertFrom-Json
        if ($json.code -eq 0 -and $json.data -eq $true) {
            return $true
        }
        Write-Host "        resp: $($resp.Content)" -ForegroundColor Red
        return $false
    } catch {
        Write-Host "        err: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

# 删除配置 (v2 API)
function Remove-NacosConfig {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [string]$Ns,
        [string]$Group,
        [string]$DataId
    )
    $uri = "$BaseUrl/nacos/v2/cs/config?accessToken=$Token&dataId=$DataId&group=$Group&namespaceId=$Ns"
    try {
        $resp = Invoke-WebRequest -Uri $uri -Method Delete -UseBasicParsing -TimeoutSec 10
        $json = $resp.Content | ConvertFrom-Json
        return ($json.code -eq 0)
    } catch {
        return $false
    }
}

# 查询远程配置 MD5 (v2 API), 不存在返回 $null
function Get-NacosConfigMd5 {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [string]$Ns,
        [string]$Group,
        [string]$DataId
    )
    $uri = "$BaseUrl/nacos/v2/cs/config?accessToken=$Token&dataId=$DataId&group=$Group&namespaceId=$Ns"
    try {
        $resp = Invoke-WebRequest -Uri $uri -Method Get -UseBasicParsing -TimeoutSec 10
        $json = $resp.Content | ConvertFrom-Json
        if ($json.code -eq 0 -and $json.data) {
            return [string]$json.data.md5
        }
    } catch {
        # 配置不存在会抛异常, 返回 $null
    }
    return $null
}

# 生成 common 共享配置内容
function New-CommonConfigContent {
    param([string]$Environment)
    $p = $envParams[$Environment]
    if (-not $p) {
        throw "不支持的环境: $Environment (可用: $($envParams.Keys -join ', '))"
    }
    $r = $commonTemplate
    $r = $r.Replace('{ENV}', $Environment.ToUpper())
    $r = $r.Replace('{ENV_LOWER}', $Environment)
    $r = $r.Replace('{ENV_UPPER}', $Environment.ToUpper())
    $r = $r.Replace('{ENV_CN}', [string]$p.CN)
    $r = $r.Replace('{ENV_DESC}', [string]$p.Desc)
    $r = $r.Replace('{DRUID_INIT}', [string]$p.Init)
    $r = $r.Replace('{DRUID_MIN}', [string]$p.Min)
    $r = $r.Replace('{DRUID_MAX}', [string]$p.Max)
    $r = $r.Replace('{REDIS_MAX_ACTIVE}', [string]$p.RA)
    $r = $r.Replace('{REDIS_MAX_IDLE}', [string]$p.RI)
    $r = $r.Replace('{REDIS_MIN_IDLE}', [string]$p.RMi)
    $r = $r.Replace('{REDIS_TIMEOUT}', [string]$p.RT)
    $r = $r.Replace('{REDIS_MAX_WAIT}', [string]$p.RW)
    $r = $r.Replace('{LOG_LEVEL}', [string]$p.Log)
    $r = $r.Replace('{DRY_RUN}', [string]$p.DryRun)
    return $r
}

# ============================================================
# 主流程
# ============================================================

$envUpper = $Env.ToUpper()
$group    = "PMIS_GROUP_$envUpper"

Write-Banner "PMIS Nacos 配置同步 (sync-nacos.ps1)"
Write-Host "   Action    : $Action" -ForegroundColor Cyan
Write-Host "   Env       : $Env" -ForegroundColor Cyan
Write-Host "   NacosUrl  : $NacosUrl" -ForegroundColor Cyan
Write-Host "   Namespace : $Namespace" -ForegroundColor Cyan
Write-Host "   Group     : $group" -ForegroundColor Cyan
Write-Host "   ConfigDir : $ConfigDir" -ForegroundColor Cyan
Write-Host "   Services  : $($services.Count)" -ForegroundColor Cyan

# ------------------------------------------------------------
# 1. 密码校验 (从环境变量读取, 不硬编码)
# ------------------------------------------------------------
$password = $env:NACOS_PASSWORD
if (-not $password) {
    Write-Host ""
    Write-Host "[FAIL] 未检测到密码。请先设置环境变量 NACOS_PASSWORD:" -ForegroundColor Red
    Write-Host '       $env:NACOS_PASSWORD = "your-password"' -ForegroundColor Yellow
    exit 1
}

# ------------------------------------------------------------
# 2. 校验配置目录
# ------------------------------------------------------------
if (-not (Test-Path $ConfigDir)) {
    Write-Host ""
    Write-Host "[FAIL] 配置目录不存在: $ConfigDir" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------
# 3. 登录 Nacos
# ------------------------------------------------------------
Write-Step "登录 Nacos ..."
$token = Get-NacosToken -BaseUrl $NacosUrl -User $Username -Pwd $password
if (-not $token) {
    Write-Host "[FAIL] 登录失败, 终止。" -ForegroundColor Red
    exit 1
}
Write-Host "      accessToken 获取成功" -ForegroundColor Green

# ------------------------------------------------------------
# 4. 收集待推送配置清单
# ------------------------------------------------------------
Write-Step "收集 $Env 环境配置清单 ..."
$configList = @()

# 服务配置
foreach ($svc in $services) {
    $dataId   = "$svc-$Env.yaml"
    $filePath = Join-Path $ConfigDir $dataId
    if (Test-Path $filePath) {
        $configList += [PSCustomObject]@{
            Type     = "service"
            DataId   = $dataId
            Group    = $group
            FilePath = $filePath
        }
    } else {
        Write-Host "      [WARN] 缺失: $dataId" -ForegroundColor Yellow
    }
}

# common 共享配置 (sync / rebuild 时生成并推送)
$commonDataId = "pmis-common-$Env.yaml"
$commonFile   = Join-Path $ConfigDir $commonDataId

if ($Action -ne "push") {
    Write-Host "      生成 common 共享配置: $commonDataId" -ForegroundColor Cyan
    $commonContent = New-CommonConfigContent -Environment $Env
    Set-Content -Path $commonFile -Value $commonContent -Encoding UTF8 -NoNewline
    $configList += [PSCustomObject]@{
        Type     = "common"
        DataId   = $commonDataId
        Group    = $group
        FilePath = $commonFile
    }
}

Write-Host "      待处理配置: $($configList.Count) 条 (服务 $(
    ($configList | Where-Object { $_.Type -eq 'service' }).Count) + common $(
    ($configList | Where-Object { $_.Type -eq 'common' }).Count))" -ForegroundColor Green

# ------------------------------------------------------------
# 5. rebuild: 先删除目标环境下所有旧配置
# ------------------------------------------------------------
if ($Action -eq "rebuild") {
    Write-Step "rebuild 模式: 删除 $Env 环境下所有旧配置 ..."

    # 兼容历史遗留的多种 Group 命名
    $candidateGroups = @(
        "PMIS_GROUP_$envUpper",   # 当前规范
        $Env,                      # 旧错误: 小写环境名
        "PMIS_GROUP"               # 旧规范: 无环境后缀
    ) | Select-Object -Unique

    $allDataIds = @()
    foreach ($svc in $services) { $allDataIds += "$svc-$Env.yaml" }
    $allDataIds += "pmis-common-$Env.yaml"
    $allDataIds += "ydsz-pmis-common-$Env.yaml"

    $deleted = 0
    $delFail = 0
    foreach ($did in $allDataIds) {
        foreach ($grp in $candidateGroups) {
            $ok = Remove-NacosConfig -BaseUrl $NacosUrl -Token $token -Ns $Namespace -Group $grp -DataId $did
            if ($ok) {
                $deleted++
                Write-Host "      [DEL] $grp | $did" -ForegroundColor DarkGray
            } else {
                $delFail++
            }
        }
    }
    Write-Host "      删除完成: $deleted 个成功, $delFail 个不存在/失败" -ForegroundColor Green
}

# ------------------------------------------------------------
# 6. 推送配置
# ------------------------------------------------------------
$actionDesc = switch ($Action) {
    "push"    { "仅推送变更 (MD5 对比)" }
    "sync"    { "全量推送" }
    "rebuild" { "重建后全量推送" }
}
Write-Step "推送配置 [$actionDesc] ..."

$total   = 0
$pushed  = 0
$skipped = 0
$failed  = 0
$failList = @()
$idx = 0

foreach ($cfg in $configList) {
    $idx++
    $total++
    $content = Get-Content -Path $cfg.FilePath -Raw -Encoding UTF8
    $tag = if ($cfg.Type -eq "common") { "COMMON" } else { "SVC   " }

    # push 模式: 对比 MD5, 仅推送变更
    if ($Action -eq "push") {
        $remoteMd5 = Get-NacosConfigMd5 -BaseUrl $NacosUrl -Token $token -Ns $Namespace -Group $cfg.Group -DataId $cfg.DataId
        $localMd5  = Get-StringMd5 -Text $content
        if ($remoteMd5 -and $remoteMd5 -eq $localMd5) {
            Write-Host "  [$idx/$($configList.Count)] [SKIP] $tag $($cfg.Group) | $($cfg.DataId)  (MD5 一致)" -ForegroundColor DarkGray
            $skipped++
            continue
        }
    }

    $ok = Publish-NacosConfig -BaseUrl $NacosUrl -Token $token -Ns $Namespace `
        -Group $cfg.Group -DataId $cfg.DataId -Content $content

    if ($ok) {
        $pushed++
        Write-Host "  [$idx/$($configList.Count)] [OK]   $tag $($cfg.Group) | $($cfg.DataId)" -ForegroundColor Green
    } else {
        $failed++
        $failList += "$($cfg.Group) | $($cfg.DataId)"
        Write-Host "  [$idx/$($configList.Count)] [FAIL] $tag $($cfg.Group) | $($cfg.DataId)" -ForegroundColor Red
    }
}

# ------------------------------------------------------------
# 7. 汇总
# ------------------------------------------------------------
Write-Banner "推送完成 ($Action / $Env)"
Write-Host "   总计   : $total" -ForegroundColor Cyan
Write-Host "   成功   : $pushed" -ForegroundColor Green
if ($skipped -gt 0) {
    Write-Host "   跳过   : $skipped (MD5 一致, 无变更)" -ForegroundColor DarkGray
}
Write-Host "   失败   : $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
Write-Host "============================================================" -ForegroundColor Cyan

if ($failList.Count -gt 0) {
    Write-Host ""
    Write-Host "失败列表:" -ForegroundColor Red
    foreach ($f in $failList) {
        Write-Host "  - $f" -ForegroundColor Red
    }
}

if ($failed -gt 0) { exit 1 }
exit 0
