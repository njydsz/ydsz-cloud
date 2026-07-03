<#
.SYNOPSIS
    规则引擎回归测试 CI 集成脚本

.DESCRIPTION
    调用规则引擎批量测试用例执行 API，根据通过率判断是否阻断 CI 流水线。
    当 allPassed=false 时以非零退出码退出，阻断流水线。

.PARAMETER BaseUrl
    后端服务地址（默认 http://localhost:8080）

.PARAMETER TimeoutSec
    HTTP 请求超时秒数（默认 120）

.EXAMPLE
    .\regression-test.ps1 -BaseUrl http://192.168.1.100:8080
    .\regression-test.ps1 -BaseUrl http://localhost:8080 -TimeoutSec 300

.EXITCODE
    0 = 全部测试通过
    1 = 存在失败用例
    2 = API 调用失败
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$TimeoutSec = 120
)

$ErrorActionPreference = "Stop"
$apiUrl = "$BaseUrl/api/v1/rules/test-cases/batch-run"

Write-Host "[CI] 开始规则引擎回归测试..." -ForegroundColor Cyan
Write-Host "[CI] API: $apiUrl" -ForegroundColor Gray

try {
    $response = Invoke-RestMethod -Uri $apiUrl -Method POST -ContentType "application/json" `
        -Body '{"ids":[]}' -TimeoutSec $TimeoutSec
}
catch {
    Write-Host "[CI] API 调用失败: $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}

$data = $response.data
if ($null -eq $data) {
    Write-Host "[CI] 响应数据为空" -ForegroundColor Red
    exit 2
}

$total = $data.total
$passed = $data.passed
$failed = $data.failed
$passRate = $data.passRate
$allPassed = $data.allPassed

Write-Host ""
Write-Host "========== 回归测试报告 ==========" -ForegroundColor Cyan
Write-Host "  总用例数: $total"
Write-Host "  通过: $passed" -ForegroundColor Green
Write-Host "  失败: $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Gray" })
Write-Host "  通过率: $passRate"
Write-Host "==================================="
Write-Host ""

# 输出失败用例详情
if ($failed -gt 0) {
    Write-Host "[CI] 失败用例详情:" -ForegroundColor Yellow
    foreach ($case in $data.caseResults) {
        if (-not $case.pass) {
            Write-Host "  - [$($case.testCaseName)] 关联规则: $($case.ruleCode)" -ForegroundColor Red
            if ($case.missing.Count -gt 0) {
                Write-Host "    缺失触发: $($case.missing -join ', ')" -ForegroundColor Yellow
            }
            if ($case.unexpected.Count -gt 0) {
                Write-Host "    意外触发: $($case.unexpected -join ', ')" -ForegroundColor Yellow
            }
        }
    }
    Write-Host ""
}

if ($allPassed) {
    Write-Host "[CI] 回归测试全部通过" -ForegroundColor Green
    exit 0
} else {
    Write-Host "[CI] 回归测试未通过，阻断流水线 (passRate=$passRate)" -ForegroundColor Red
    exit 1
}
