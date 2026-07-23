# =============================================================================
#  YDSZ · 部署后冒烟测试（Windows PowerShell）
# -----------------------------------------------------------------------------
#  用法:
#    powershell -ExecutionPolicy Bypass -File deploy\scripts\smoke-test.ps1 [-GatewayUrl URL]
#
#  默认: http://127.0.0.1:9000
# =============================================================================

[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://127.0.0.1:9000",
    [int]$TimeoutSec = 10
)

$Pass = 0
$Fail = 0
$Skip = 0

# 登录凭据（保留默认值方便开发，生产可通过环境变量覆盖）
$SmokeUser = if ($env:SMOKE_USER) { $env:SMOKE_USER } else { "admin" }
$SmokePass = if ($env:SMOKE_PASS) { $env:SMOKE_PASS } else { "admin123" }

function Ok($msg)   { Write-Host "[PASS] $msg" -ForegroundColor Green; $script:Pass++ }
function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red;   $script:Fail++ }
function Skip($msg) { Write-Host "[SKIP] $msg" -ForegroundColor Yellow; $script:Skip++ }

Write-Host "================================================================"
Write-Host "  YDSZ · Smoke Test"
Write-Host "  Gateway: $GatewayUrl"
Write-Host "================================================================"
Write-Host ""

# 1. Gateway 主健康检查
Write-Host "▶ 1. Gateway 健康检查"
try {
    $resp = Invoke-WebRequest -Uri "$GatewayUrl/actuator/health" -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
    if ($resp.StatusCode -eq 200) {
        Ok "Gateway /actuator/health → 200 (body: $($resp.Content))"
    } else {
        Fail "Gateway /actuator/health → $($resp.StatusCode)"
    }
} catch {
    Fail "Gateway /actuator/health 不可访问: $($_.Exception.Message)"
}

# 2. Gateway 路由表
Write-Host ""
Write-Host "▶ 2. Gateway 路由表"
try {
    $resp = Invoke-WebRequest -Uri "$GatewayUrl/actuator/gateway/routes" -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
    Ok "Gateway /actuator/gateway/routes → 200"
} catch {
    Skip "Gateway /actuator/gateway/routes 不可访问 (可能未开启 actuator 详情)"
}

# 3. 后端微服务健康检查
Write-Host ""
Write-Host "▶ 3. 后端微服务健康检查"
$services = @(
    @{name="ydsz-system";   port=9001},
    @{name="ydsz-userinfo"; port=9002},
    @{name="ydsz-project";  port=9003},
    @{name="ydsz-cronjob";  port=9004},
    @{name="ydsz-workflow"; port=9005},
    @{name="ydsz-agent";    port=9006}
)
foreach ($svc in $services) {
    try {
        $resp = Invoke-WebRequest -Uri "$GatewayUrl/$($svc.name)/actuator/health" -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
        Ok "$($svc.name)/actuator/health → 200"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code) {
            Fail "$($svc.name)/actuator/health → $code"
        } else {
            Fail "$($svc.name)/actuator/health 不可访问: $($_.Exception.Message)"
        }
    }
}

# 4. 登录接口
Write-Host ""
Write-Host "▶ 4. 登录接口"
$token = $null
try {
    $body = @{ username = $SmokeUser; password = $SmokePass } | ConvertTo-Json
    $resp = Invoke-WebRequest -Uri "$GatewayUrl/ydsz-userinfo/auth/login" -Method POST -ContentType "application/json" -Body $body -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
    $json = $resp.Content | ConvertFrom-Json
    $token = $json.data.token
    if ($token) {
        Ok "POST /auth/login → 拿到 token (前16字符: $($token.Substring(0, [Math]::Min(16, $token.Length)))...)"
    } else {
        Fail "POST /auth/login → 响应未包含 token: $($resp.Content.Substring(0, [Math]::Min(200, $resp.Content.Length)))"
    }
} catch {
    Fail "POST /auth/login 调用失败: $($_.Exception.Message)"
}

# 5. 鉴权链路
Write-Host ""
Write-Host "▶ 5. 鉴权链路"
if ($token) {
    try {
        $resp = Invoke-WebRequest -Uri "$GatewayUrl/ydsz-userinfo/users/me" -Headers @{ Authorization = "Bearer $token" } -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
        Ok "GET /users/me with token → 200"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        Fail "GET /users/me with token → $code"
    }
} else {
    Skip "GET /users/me (无 token,跳过)"
}

# 6. Swagger UI
Write-Host ""
Write-Host "▶ 6. Swagger UI"
try {
    $resp = Invoke-WebRequest -Uri "$GatewayUrl/swagger-ui.html" -MaximumRedirection 2 -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
    Ok "/swagger-ui.html → 200"
} catch {
    Skip "/swagger-ui.html 不可访问 (可能未启用)"
}

# 7. CORS 预检
Write-Host ""
Write-Host "▶ 7. CORS 预检"
try {
    $resp = Invoke-WebRequest -Uri "$GatewayUrl/ydsz-userinfo/auth/login" -Method OPTIONS `
        -Headers @{ "Origin"="http://localhost:5173"; "Access-Control-Request-Method"="POST"; "Access-Control-Request-Headers"="Content-Type" } `
        -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
    Ok "OPTIONS preflight → $($resp.StatusCode)"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 200 -or $code -eq 204) {
        Ok "OPTIONS preflight → $code"
    } else {
        Fail "OPTIONS preflight → $code (期望 200/204)"
    }
}

# 8. 内部头剥离
Write-Host ""
Write-Host "▶ 8. 内部头剥离"
try {
    $resp = Invoke-WebRequest -Uri "$GatewayUrl/ydsz-userinfo/users/me" `
        -Headers @{ "X-User-Id"="99999"; "X-Username"="fake-admin"; "X-User-Roles"="ROLE_ADMIN" } `
        -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
    Fail "伪造 X-User-Id → 200 (网关未剥离伪造头)"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 401 -or $code -eq 403) {
        Ok "伪造 X-User-Id → $code (网关已剥离伪造头)"
    } else {
        Fail "伪造 X-User-Id → $code (期望 401/403)"
    }
}

# 9. 路径穿越拦截
Write-Host ""
Write-Host "▶ 9. 路径穿越拦截"
try {
    $resp = Invoke-WebRequest -Uri "$GatewayUrl/ydsz-userinfo/../../etc/passwd" -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
    Fail "/../../etc/passwd → 200 (可能存在路径穿越漏洞)"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 400) {
        Ok "/../../etc/passwd → 400 (路径穿越已拦截)"
    } else {
        Fail "/../../etc/passwd → $code (期望 400)"
    }
}

# 汇总
Write-Host ""
Write-Host "================================================================"
Write-Host "  Smoke Test 汇总"
Write-Host "================================================================"
Write-Host "  PASS: $Pass  FAIL: $Fail  SKIP: $Skip"
Write-Host ""

if ($Fail -gt 0) {
    Write-Host "存在失败项,请检查后再上线!" -ForegroundColor Red
    exit 1
}
Write-Host "所有关键项通过,可继续上线流程。" -ForegroundColor Green
exit 0
