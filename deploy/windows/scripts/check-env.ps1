# =============================================================================
#  YDSZ PMIS - 环境检查脚本 (Windows PowerShell)
# =============================================================================
$ErrorActionPreference = 'Continue'

function Check-Ok   { param($m) Write-Host "  [OK] $m" -ForegroundColor Green; $script:Pass++ }
function Check-Fail { param($m) Write-Host "  [FAIL] $m" -ForegroundColor Red;   $script:Fail++ }
function Check-Warn { param($m) Write-Host "  [WARN] $m" -ForegroundColor Yellow }

$Pass = 0; $Fail = 0

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  YDSZ PMIS · 环境检查 (Windows)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. OS
Write-Host "`n[1/8] 操作系统" -ForegroundColor Cyan
$os = (Get-CimInstance Win32_OperatingSystem).Caption
if ($os -match "Windows") { Check-Ok $os } else { Check-Fail "不支持: $os" }

# 2. JDK
Write-Host "`n[2/8] JDK" -ForegroundColor Cyan
try {
  $javaOut = & java -version 2>&1 | Select-Object -First 1
  if ($javaOut -match '"(\d+)\.') {
    $major = [int]$Matches[1]
    if ($major -ge 21) { Check-Ok $javaOut } else { Check-Fail "需要 JDK 21+，当前: $javaOut" }
  } else { Check-Fail "无法解析 Java 版本" }
} catch { Check-Fail "未安装 Java" }

# 3. Maven
Write-Host "`n[3/8] Maven" -ForegroundColor Cyan
try {
  $mvnOut = & mvn --version 2>&1 | Select-Object -First 1
  if ($mvnOut -match 'Apache Maven (\d+)\.') {
    $major = [int]$Matches[1]
    if ($major -ge 3) { Check-Ok $mvnOut } else { Check-Fail "需要 Maven 3.9+，当前: $mvnOut" }
  }
} catch { Check-Fail "未安装 Maven" }

# 4. Node + pnpm
Write-Host "`n[4/8] Node.js & pnpm" -ForegroundColor Cyan
try {
  $nodeVer = & node --version
  if ($nodeVer -match 'v(\d+)\.') {
    $major = [int]$Matches[1]
    if ($major -ge 20) { Check-Ok "Node.js $nodeVer" } else { Check-Fail "需要 Node.js 20+，当前: $nodeVer" }
  }
} catch { Check-Fail "未安装 Node.js" }
try {
  $pnpmVer = & pnpm --version
  Check-Ok "pnpm $pnpmVer"
} catch { Check-Warn "未安装 pnpm（运行 npm i -g pnpm）" }

# 5. Docker
Write-Host "`n[5/8] Docker" -ForegroundColor Cyan
try {
  $dockerVer = & docker --version
  Check-Ok $dockerVer
  $composeOut = & docker compose version 2>&1
  if ($LASTEXITCODE -eq 0) { Check-Ok "docker compose v2" } else { Check-Fail "需要 docker compose v2" }
} catch { Check-Fail "未安装 Docker Desktop" }

# 6. 端口
Write-Host "`n[6/8] 端口检查" -ForegroundColor Cyan
$ports = @(5432, 6379, 8848, 9848, 9100, 9101, 9000, 9001, 9002, 9003, 9004, 9005, 9006, 5173)
$busy = @()
foreach ($p in $ports) {
  $conn = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
  if ($conn) { $busy += $p }
}
if ($busy.Count -gt 0) {
  Check-Warn "以下端口已被占用: $($busy -join ', ')"
} else {
  Check-Ok "所有端口空闲"
}

# 7. 内存
Write-Host "`n[7/8] 内存" -ForegroundColor Cyan
$totalMem = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)
if ($totalMem -ge 8) { Check-Ok "${totalMem}GB 可用" } else { Check-Warn "可用内存 ${totalMem}GB，建议 ≥ 8GB" }

# 8. 项目结构
Write-Host "`n[8/8] 项目结构" -ForegroundColor Cyan
$root = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSCommandPath))
$files = @(
  "ydsz-pmis-backend\pom.xml",
  "ydsz-pmis-frontend\package.json",
  "docs\V1.0.0.sql",
  "deploy\docker\docker-compose.dev.yml"
)
foreach ($f in $files) {
  $full = Join-Path $root $f
  if (Test-Path $full) { Check-Ok $f } else { Check-Fail "缺失: $f" }
}

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "  结果：通过 $Pass  失败 $Fail" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
exit $Fail
