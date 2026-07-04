# =============================================================================
#  YDSZ PMIS · 批量构建 Docker 镜像（Windows PowerShell）
# -----------------------------------------------------------------------------
#  用法:
#    powershell -ExecutionPolicy Bypass -File deploy\scripts\build-images.ps1 [-Tag TAG] [-Registry REG] [-Push]
#
#  示例:
#    # 构建所有 7 个后端服务 + 前端，tag=v1.3.0-SNAPSHOT
#    .\deploy\scripts\build-images.ps1
#
#    # 构建并推送
#    .\deploy\scripts\build-images.ps1 -Tag v1.3.0 -Registry registry.cn-hangzhou.aliyuncs.com/your-org -Push
# =============================================================================

[CmdletBinding()]
param(
    [string]$Tag = "v1.3.0-SNAPSHOT",
    [string]$Registry = "ydsz-pmis",
    [switch]$Push
)

$services = @(
    @{name="gateway";  port=9000},
    @{name="system";   port=9001},
    @{name="userinfo"; port=9002},
    @{name="project";  port=9003},
    @{name="cronjob";  port=9004},
    @{name="workflow"; port=9005},
    @{name="agent";    port=9006}
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot  = Split-Path -Parent (Split-Path -Parent $scriptDir)
$backendDir   = Join-Path $repoRoot "ydsz-pmis-backend"
$frontendDir = Join-Path $repoRoot "ydsz-pmis-frontend"

Write-Host "================================================================"
Write-Host "  YDSZ PMIS · 批量构建 Docker 镜像"
Write-Host "  TAG:       $Tag"
Write-Host "  REGISTRY:  $Registry"
Write-Host "  PUSH:      $Push"
Write-Host "================================================================"

# 启用 BuildKit
$env:DOCKER_BUILDKIT = "1"

# 构建后端服务
foreach ($svc in $services) {
    $image = "$Registry/$($svc.name):$Tag"
    Write-Host ""
    Write-Host "▶ 构建后端镜像: $image" -ForegroundColor Yellow
    $success = $false
    try {
        docker build `
            --build-arg MODULE_NAME="ydsz-pmis-$($svc.name)" `
            --build-arg APP_PORT="$($svc.port)" `
            -t $image `
            -f "$backendDir/Dockerfile" `
            $backendDir
        if ($LASTEXITCODE -eq 0) { $success = $true }
    } catch {
        Write-Host "[FAIL] $image : $_" -ForegroundColor Red
        exit 1
    }
    if ($success) {
        Write-Host "[OK] $image" -ForegroundColor Green
        if ($Push) {
            docker push $image
            Write-Host "[PUSHED] $image" -ForegroundColor Green
        }
    } else {
        Write-Host "[FAIL] $image (exit code $LASTEXITCODE)" -ForegroundColor Red
        exit 1
    }
}

# 构建前端镜像
if (Test-Path "$frontendDir/Dockerfile") {
    Write-Host ""
    Write-Host "▶ 构建前端镜像: $Registry/frontend:$Tag" -ForegroundColor Yellow
    docker build -t "$Registry/frontend:$Tag" -f "$frontendDir/Dockerfile" $frontendDir
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] $Registry/frontend:$Tag" -ForegroundColor Green
        if ($Push) {
            docker push "$Registry/frontend:$Tag"
            Write-Host "[PUSHED] $Registry/frontend:$Tag" -ForegroundColor Green
        }
    } else {
        Write-Host "[FAIL] $Registry/frontend:$Tag" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "================================================================"
Write-Host "  构建完成"
Write-Host "================================================================"
Write-Host ""
Write-Host "下一步:"
Write-Host "  1. 推送镜像:    .\deploy\scripts\build-images.ps1 -Tag $Tag -Registry $Registry -Push"
Write-Host "  2. K8s 部署:    kubectl apply -k deploy/k8s/overlays/dev"
Write-Host "  3. Helm 部署:   helm install pmis deploy/helm/ydsz-pmis -n pmis -f deploy/helm/ydsz-pmis/values-dev.yaml"
Write-Host "  4. 冒烟测试:    .\deploy\scripts\smoke-test.ps1 -GatewayUrl http://<gateway-ip>:9000"
