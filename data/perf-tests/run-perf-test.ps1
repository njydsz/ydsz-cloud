<#
.SYNOPSIS
    云顶云平台 Gatling 性能测试一键启动脚本。

.DESCRIPTION
    自动解析父 pom.xml 中的 Gatling 依赖版本，启动 mvn gatling:test。
    结果输出到 data/perf-tests/target/gatling-results/。

.PARAMETER BaseUrl
    目标网关地址。默认: http://localhost:9000。
    覆盖方式: -BaseUrl "http://sit.example.com:9000"。

.PARAMETER SimulationClass
    仅运行指定的 Simulation 类（全限定名，不含 .class）。
    默认为空（运行全部）。

.PARAMETER SkipDependencyDownload
    跳过依赖下载（适用于已下载过依赖的场景）。

.EXAMPLE
    .\run-perf-test.ps1
    默认压测本地网关。

.EXAMPLE
    .\run-perf-test.ps1 -BaseUrl "http://sit.example.com:9000" -SimulationClass "com.njydsz.perf.CloudPerfTest"
    压测 SIT 环境，仅运行轻载模拟。
#>

param(
    [string]$BaseUrl = "http://localhost:9000",
    [string]$SimulationClass = "",
    [switch]$SkipDependencyDownload = $false
)

# ── 工作目录切换到 perf-tests 子模块根 ──
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $scriptDir

# ── 颜色输出函数 ──
function Write-Title { param($msg) Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Write-Success { param($msg) Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Error { param($msg) Write-Host "[ERR] $msg" -ForegroundColor Red }
function Write-Info { param($msg) Write-Host "[INFO] $msg" -ForegroundColor Yellow }

Write-Title "ydsz-cloud Gatling 性能测试"
Write-Host "目标地址 : $BaseUrl"
Write-Host "工作目录 : $(Get-Location)"
Write-Host "时间戳   : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"

# ── 前置检查：mvn 可用性 ──
$mvnExe = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnExe) {
    Write-Error "未找到 mvn 命令，请确认 Maven 在 PATH 中。"
    exit 1
}
Write-Success "Maven 可用: $($mvnExe.Source)"

# ── 父 POM 路径 ──
$parentPom = Join-Path $scriptDir "..\..\pom.xml"
if (-not (Test-Path $parentPom)) {
    Write-Error "未找到父 pom.xml: $parentPom"
    exit 1
}
Write-Success "父 POM: $parentPom"

# ── 组装 Maven 命令 ──
$mvnArgs = @(
    "gatling:test",
    "-pl", ".",
    "-P", "perf-test",
    "-f", $parentPom,
    "-DbaseUrl=$BaseUrl"
)

if ($SimulationClass) {
    $mvnArgs += "-Dgatling.simulationClass=$SimulationClass"
}

if ($SkipDependencyDownload) {
    $mvnArgs += "-o"
}

Write-Host ""
Write-Title "执行命令"
Write-Host "mvn $($mvnArgs -join ' ')"
Write-Host ""

# ── 执行压测 ──
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
& mvn @mvnArgs

$exitCode = $LASTEXITCODE
$stopwatch.Stop()

# ── 结果判定 ──
Write-Host ""
if ($exitCode -eq 0) {
    Write-Success "压测完成！耗时: $($stopwatch.Elapsed.ToString('mm\:ss\.fff'))"

    # 打开最新的报告目录
    $resultsPath = Join-Path $scriptDir "target\gatling-results"
    if (Test-Path $resultsPath) {
        $latestReport = Get-ChildItem -Path $resultsPath -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($latestReport) {
            $htmlPath = Join-Path $latestReport.FullName "index.html"
            if (Test-Path $htmlPath) {
                Write-Success "HTML 报告: $htmlPath"
                Write-Host ""
                Write-Host "正在打开报告..." -ForegroundColor Cyan
                Start-Process $htmlPath
            }
        }
    }
} else {
    Write-Error "压测失败！退出码: $exitCode"
    Write-Host ""
    Write-Host "排查建议:" -ForegroundColor Yellow
    Write-Host "  1. 确认目标服务已启动: curl $BaseUrl/actuator/health"
    Write-Host "  2. 检查网络连通性: Test-NetConnection -ComputerName localhost -Port 9000"
    Write-Host "  3. 查看详细日志: target/gatling-results/*/simulation.log"
}

exit $exitCode
