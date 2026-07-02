# ============================================================
# 一键同步 Nacos（互联网大厂标准）
# 1) 修正 52 个 *-dev/sit/uat/prod.yaml 的 namespace/group
# 2) 修正 13 个 application.yml 注入 Nacos namespace/group
# 3) 生成 4 个 pmis-common-<env>.yaml
# 4) 批量推送到 Nacos 命名空间 pmis
# ============================================================

# 公共配置模板
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
'@

# 占位符替换
function Format-CommonConfig {
    param(
        [string]$Template,
        [string]$Env,
        [hashtable]$Params
    )
    $envUpper = $Env.ToUpper()
    $r = $Template
    $r = $r.Replace('{ENV}', $envUpper)
    $r = $r.Replace('{ENV_LOWER}', $Env)
    $r = $r.Replace('{ENV_UPPER}', $envUpper)
    $r = $r.Replace('{ENV_CN}', [string]$Params.CN)
    $r = $r.Replace('{ENV_DESC}', [string]$Params.Desc)
    $r = $r.Replace('{DRUID_INIT}', [string]$Params.Init)
    $r = $r.Replace('{DRUID_MIN}', [string]$Params.Min)
    $r = $r.Replace('{DRUID_MAX}', [string]$Params.Max)
    $r = $r.Replace('{REDIS_MAX_ACTIVE}', [string]$Params.RA)
    $r = $r.Replace('{REDIS_MAX_IDLE}', [string]$Params.RI)
    $r = $r.Replace('{REDIS_MIN_IDLE}', [string]$Params.RMi)
    $r = $r.Replace('{REDIS_TIMEOUT}', [string]$Params.RT)
    $r = $r.Replace('{REDIS_MAX_WAIT}', [string]$Params.RW)
    $r = $r.Replace('{LOG_LEVEL}', [string]$Params.Log)
    return $r
}

# 推送单个配置
function Push-NacosConfig {
    param(
        [string]$NacosHost,
        [string]$Token,
        [string]$Namespace,
        [string]$Group,
        [string]$DataId,
        [string]$Content
    )
    $uri = "$NacosHost/nacos/v1/cs/configs?accessToken=$Token"
    $body = @{
        dataId       = $DataId
        group        = $Group
        namespaceId  = $Namespace
        type         = "yaml"
        content      = $Content
    }
    try {
        $resp = Invoke-WebRequest -Uri $uri -Method POST -Body $body -TimeoutSec 15 `
            -ContentType "application/x-www-form-urlencoded" -UseBasicParsing
        return ($resp.StatusCode -eq 200)
    } catch {
        return $false
    }
}

# 在 application.yml 中插入 group 行
function Insert-GroupLine {
    param([string]$Content)
    if ($Content -match '(?m)^\s*group:\s*PMIS_GROUP_') {
        return $Content
    }
    $lines = $Content -split "`n"
    $newLines = @()
    $inserted = $false
    foreach ($line in $lines) {
        $newLines += $line
        if (-not $inserted -and $line -match '^\s*file-extension:\s*yaml\s*$') {
            # 计算缩进
            $indent = ''
            if ($line -match '^(?<sp>\s*)file-extension') {
                $indent = $Matches.sp
            }
            $newLines += ($indent + 'group: PMIS_GROUP_${spring.profiles.active}')
            $inserted = $true
        }
    }
    return ($newLines -join "`n")
}

# ============================================================
# 主流程
# ============================================================
$ErrorActionPreference = "Stop"

$NacosHost = "http://127.0.0.1:8848"
$Username  = "nacos"
$Password  = "Limw1020"
$Namespace = "pmis"
$BasePath  = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$CommonDir = "d:\Code\ydsz\ydsz-pmis\deploy\nacos\common"

$envs = @("dev","sit","uat","prod")
$svcs = @("gateway","auth","user","notification","workflow","project",
          "execution","agent","config","file","audit","message","scheduler")

# 1) 登录
Write-Host "[1/4] Login Nacos ..." -ForegroundColor Cyan
$loginBody = "username=$Username&password=$Password"
$loginResp = Invoke-RestMethod -Uri "$NacosHost/nacos/v1/auth/users/login" `
    -Method POST -ContentType "application/x-www-form-urlencoded" -Body $loginBody `
    -TimeoutSec 10
$token = $loginResp.accessToken
if (-not $token) { throw "Login failed" }
Write-Host "      token OK" -ForegroundColor Green

# 2) 修正 52 个服务配置
Write-Host "[2/4] Modifying 52 service configs ..." -ForegroundColor Cyan
$pushList = @()
foreach ($svc in $svcs) {
    foreach ($env in $envs) {
        $envUpper = $env.ToUpper()
        $group    = "PMIS_GROUP_$envUpper"
        $file     = Join-Path $BasePath "ydsz-pmis-$svc\src\main\resources\ydsz-pmis-$svc-$env.yaml"
        if (-not (Test-Path $file)) { continue }

        $content = Get-Content $file -Raw -Encoding UTF8
        $old = '${NACOS_NAMESPACE:pmis-' + $env + '}'
        $content = $content.Replace($old, '${NACOS_NAMESPACE:pmis}')
        $content = [regex]::Replace($content, '(?m)^(\s*group:\s*)PMIS_GROUP\s*$', ('$1' + $group))
        $content = $content.Replace("Namespace: pmis-$env", "Namespace: pmis")
        $content = $content.Replace("Group   : PMIS_GROUP", "Group   : $group")
        $content = $content.Replace("Group   :PMIS_GROUP", "Group   : $group")

        Set-Content -Path $file -Value $content -Encoding UTF8 -NoNewline
        $pushList += [PSCustomObject]@{
            File   = $file
            Group  = $group
            DataId = "ydsz-pmis-$svc-$env.yaml"
        }
    }
}
Write-Host "      Modified $($pushList.Count) files" -ForegroundColor Green

# 3) 修正 13 个 application.yml
Write-Host "[3/4] Modifying 13 application.yml ..." -ForegroundColor Cyan
foreach ($svc in $svcs) {
    $file = Join-Path $BasePath "ydsz-pmis-$svc\src\main\resources\application.yml"
    if (-not (Test-Path $file)) { continue }
    $content = Get-Content $file -Raw -Encoding UTF8
    $content = [regex]::Replace($content, '\$\{NACOS_NAMESPACE(:[^}]*)?\}', '${NACOS_NAMESPACE:pmis}')
    $content = Insert-GroupLine -Content $content
    Set-Content -Path $file -Value $content -Encoding UTF8 -NoNewline
}
Write-Host "      Modified 13 application.yml" -ForegroundColor Green

# 4) 生成 4 个公共配置
Write-Host "[4/4] Generating & pushing common configs ..." -ForegroundColor Cyan
$envParams = @{
    "dev"  = @{ Init=5;  Min=5;  Max=20; RA=16; RI=8;  RMi=2; RT="3s";  RW="3s";  Log="DEBUG"; CN="开发";  Desc="本地开发联调" }
    "sit"  = @{ Init=5;  Min=5;  Max=20; RA=16; RI=8;  RMi=2; RT="3s";  RW="3s";  Log="INFO";  CN="系统集成"; Desc="外部依赖全链路测试" }
    "uat"  = @{ Init=10; Min=10; Max=40; RA=32; RI=16; RMi=4; RT="5s";  RW="5s";  Log="INFO";  CN="用户验收"; Desc="生产仿真、含脱敏数据" }
    "prod" = @{ Init=20; Min=20; Max=80; RA=64; RI=32; RMi=8; RT="5s";  RW="5s";  Log="WARN";  CN="生产";   Desc="线上真实数据" }
}

if (-not (Test-Path $CommonDir)) { New-Item -ItemType Directory -Path $CommonDir -Force | Out-Null }
foreach ($env in $envs) {
    $envUpper = $env.ToUpper()
    $p = $envParams[$env]
    $body = Format-CommonConfig -Template $commonTemplate -Env $env -Params $p
    $path = Join-Path $CommonDir "pmis-common-$env.yaml"
    Set-Content -Path $path -Value $body -Encoding UTF8 -NoNewline
    $pushList += [PSCustomObject]@{
        File   = $path
        Group  = "PMIS_GROUP_$envUpper"
        DataId = "pmis-common-$env.yaml"
    }
}

# 5) 批量推送
Write-Host "Pushing $($pushList.Count) configs to Nacos [namespace=$Namespace] ..." -ForegroundColor Cyan
$okCount = 0
$failList = @()
$i = 0
foreach ($item in $pushList) {
    $i++
    $content = Get-Content $item.File -Raw -Encoding UTF8
    $ok = Push-NacosConfig -NacosHost $NacosHost -Token $token -Namespace $Namespace `
        -Group $item.Group -DataId $item.DataId -Content $content
    if ($ok) {
        $okCount++
        Write-Host "  [$i/$($pushList.Count)] [OK]  $($item.Group) | $($item.DataId)"
    } else {
        $failList += $item
        Write-Host "  [$i/$($pushList.Count)] [FAIL] $($item.Group) | $($item.DataId)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "===== Push Summary =====" -ForegroundColor Cyan
Write-Host "Success: $okCount / $($pushList.Count)" -ForegroundColor Green
if ($failList.Count -gt 0) {
    Write-Host "Failed: $($failList.Count)" -ForegroundColor Red
    foreach ($f in $failList) {
        Write-Host "  - $($f.Group) | $($f.DataId)" -ForegroundColor Red
    }
}
Write-Host "========================="
