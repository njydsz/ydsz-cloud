# ============================================================
# PMIS Nacos 配置修复脚本
#   1) 删除 public 命名空间下错误推送的 56 个 ydsz-pmis-* / pmis-common-* 配置
#   2) 使用 v2 API 重新推送到 pmis 命名空间
# ============================================================

param(
    [string]$NacosUrl   = "http://127.0.0.1:8848",
    [string]$Username   = "nacos",
    [string]$Password   = "Limw1020",
    [string]$Namespace  = "pmis",
    [string]$BackendRoot = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
)

$ErrorActionPreference = "Continue"
$ProgressPreference    = "SilentlyContinue"

# 服务列表
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

$envs = @("dev", "sit", "uat", "prod")

# 1. 登录
Write-Host "[1/4] Login Nacos ..." -ForegroundColor Cyan
$loginUri = "$NacosUrl/nacos/v1/auth/users/login"
try {
    $resp = Invoke-WebRequest -Uri $loginUri -Method POST -Body @{username=$Username;password=$Password} -UseBasicParsing -TimeoutSec 10
    $loginJson = $resp.Content | ConvertFrom-Json
    $token = $loginJson.accessToken
    if (-not $token) { throw "登录返回无 token" }
    Write-Host "      token OK" -ForegroundColor Green
} catch {
    Write-Host "      [FAIL] $_" -ForegroundColor Red
    exit 1
}

# 2. 删除 public 命名空间下的 56 个错误配置
Write-Host "[2/4] Deleting 56 wrong configs from public namespace ..." -ForegroundColor Cyan
$deleted = 0
$deleteFail = 0
foreach ($svc in $services) {
    foreach ($env in $envs) {
        $dataId = "$svc-$env.yaml"
        $group  = "PMIS_GROUP_$($env.ToUpper())"
        $uri = "$NacosUrl/nacos/v1/cs/configs?dataId=$dataId&group=$group&tenant=&accessToken=$token"
        try {
            $r = Invoke-WebRequest -Uri $uri -Method DELETE -UseBasicParsing -TimeoutSec 10
            $deleted++
        } catch {
            $deleteFail++
        }
    }
}
# common
foreach ($env in $envs) {
    $dataId = "pmis-common-$env.yaml"
    $group  = "PMIS_GROUP_$($env.ToUpper())"
    $uri = "$NacosUrl/nacos/v1/cs/configs?dataId=$dataId&group=$group&tenant=&accessToken=$token"
    try {
        Invoke-WebRequest -Uri $uri -Method DELETE -UseBasicParsing -TimeoutSec 10 | Out-Null
        $deleted++
    } catch {
        $deleteFail++
    }
}
Write-Host "      Deleted: $deleted (fail: $deleteFail)" -ForegroundColor Green

# 3. 收集所有 56 个配置文件路径 + 公共配置
Write-Host "[3/4] Preparing 56 configs ..." -ForegroundColor Cyan
$configList = @()
foreach ($svc in $services) {
    foreach ($env in $envs) {
        $filePath = Join-Path $BackendRoot "$svc\src\main\resources\$svc-$env.yaml"
        if (Test-Path $filePath) {
            $configList += [pscustomobject]@{
                DataId  = "$svc-$env.yaml"
                Group   = "PMIS_GROUP_$($env.ToUpper())"
                FilePath = $filePath
            }
        } else {
            Write-Host "      [WARN] 缺失: $filePath" -ForegroundColor Yellow
        }
    }
}
foreach ($env in $envs) {
    $filePath = Join-Path $BackendRoot "..\deploy\nacos\common\pmis-common-$env.yaml"
    if (Test-Path $filePath) {
        $configList += [pscustomobject]@{
            DataId  = "pmis-common-$env.yaml"
            Group   = "PMIS_GROUP_$($env.ToUpper())"
            FilePath = $filePath
        }
    } else {
        Write-Host "      [WARN] 缺失: $filePath" -ForegroundColor Yellow
    }
}
Write-Host "      待推送: $($configList.Count)" -ForegroundColor Green

# 4. 使用 v2 API 推送
Write-Host "[4/4] Pushing $($configList.Count) configs to [$Namespace] using v2 API ..." -ForegroundColor Cyan
$pushOk = 0
$pushFail = 0
$i = 0
foreach ($c in $configList) {
    $i++
    $content = Get-Content -Path $c.FilePath -Raw -Encoding UTF8
    $uri = "$NacosUrl/nacos/v2/cs/config?accessToken=$token"
    $body = @{
        dataId      = $c.DataId
        group       = $c.Group
        namespaceId = $Namespace
        type        = "yaml"
        content     = $content
    }
    try {
        $r = Invoke-WebRequest -Uri $uri -Method POST -Body $body -UseBasicParsing -TimeoutSec 15 -ContentType "application/x-www-form-urlencoded"
        $j = $r.Content | ConvertFrom-Json
        if ($j.code -eq 0 -and $j.data -eq $true) {
            Write-Host "  [$i/$($configList.Count)] [OK]  $($c.Group) | $($c.DataId)" -ForegroundColor Green
            $pushOk++
        } else {
            Write-Host "  [$i/$($configList.Count)] [FAIL] $($c.Group) | $($c.DataId) resp=$($r.Content)" -ForegroundColor Red
            $pushFail++
        }
    } catch {
        Write-Host "  [$i/$($configList.Count)] [FAIL] $($c.Group) | $($c.DataId) err=$($_.Exception.Message)" -ForegroundColor Red
        $pushFail++
    }
}

Write-Host ""
Write-Host "===== Push Summary =====" -ForegroundColor Cyan
Write-Host "Success: $pushOk / $($configList.Count)" -ForegroundColor $(if ($pushFail -eq 0) { "Green" } else { "Red" })
Write-Host "=========================" -ForegroundColor Cyan
