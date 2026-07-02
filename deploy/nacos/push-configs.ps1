# ============================================================
# PMIS Nacos 配置批量推送脚本
# 互联网大厂标准 - Namespace(pmis) + Group(PMIS_GROUP_{ENV}) + DataId
# ============================================================
# 用法：
#   1) PowerShell 直接执行：.\push-configs.ps1
#   2) CI/CD 调用：.\push-configs.ps1 -NacosUrl ... -Username ... -Password ...
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

# Basic Auth 头
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))
$headers = @{
    "Authorization" = "Basic $auth"
    "Content-Type"  = "application/x-www-form-urlencoded; charset=utf-8"
}

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

# 环境列表
$envs = @("dev", "test", "staging", "prod")

# 统计
$total    = 0
$success  = 0
$failed   = 0
$skipped  = 0
$failedList = @()

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " PMIS Nacos 批量推送" -ForegroundColor Cyan
Write-Host "   Nacos      : $NacosUrl" -ForegroundColor Cyan
Write-Host "   Namespace  : $Namespace" -ForegroundColor Cyan
Write-Host "   Group 规则  : PMIS_GROUP_{ENV}（大写）" -ForegroundColor Cyan
Write-Host "   DataId 规则 : <service>-<env>.yaml" -ForegroundColor Cyan
Write-Host "   服务数      : $($services.Count)" -ForegroundColor Cyan
Write-Host "   环境数      : $($envs.Count)" -ForegroundColor Cyan
Write-Host "   总计        : $($services.Count * $envs.Count) 条" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

foreach ($service in $services) {
    foreach ($env in $envs) {
        $total++
        $filePath = Join-Path $BackendRoot "$service\src\main\resources\$service-$env.yaml"

        if (-not (Test-Path $filePath)) {
            Write-Host "[SKIP]  文件不存在: $filePath" -ForegroundColor Yellow
            $skipped++
            continue
        }

        $dataId = "$service-$env.yaml"
        $group  = "PMIS_GROUP_$($env.ToUpper())"
        $content = Get-Content -Path $filePath -Raw -Encoding UTF8

        # Nacos OpenAPI: POST /nacos/v1/cs/configs
        # 参数: dataId, group, namespaceId, content, type
        $uri = "$NacosUrl/nacos/v1/cs/configs"
        $body = @{
            dataId      = $dataId
            group       = $group
            namespaceId = $Namespace
            content     = $content
            type        = "yaml"
        }

        try {
            $response = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -Body $body -TimeoutSec 30

            # Nacos 返回 true 表示成功
            if ($response -eq $true -or $response -eq "true") {
                Write-Host "[OK]    $dataId  =>  group=$group" -ForegroundColor Green
                $success++
            } else {
                Write-Host "[FAIL]  $dataId  =>  group=$group  resp=$response" -ForegroundColor Red
                $failed++
                $failedList += "$dataId@$group"
            }
        } catch {
            $errMsg = $_.Exception.Message
            Write-Host "[FAIL]  $dataId  =>  group=$group  err=$errMsg" -ForegroundColor Red
            $failed++
            $failedList += "$dataId@$group"
        }
    }
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " 推送完成" -ForegroundColor Cyan
Write-Host "   总计   : $total" -ForegroundColor Cyan
Write-Host "   成功   : $success" -ForegroundColor Green
Write-Host "   失败   : $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
Write-Host "   跳过   : $skipped" -ForegroundColor $(if ($skipped -gt 0) { "Yellow" } else { "Green" })
Write-Host "============================================================" -ForegroundColor Cyan

if ($failedList.Count -gt 0) {
    Write-Host ""
    Write-Host "失败列表：" -ForegroundColor Red
    $failedList | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
}
