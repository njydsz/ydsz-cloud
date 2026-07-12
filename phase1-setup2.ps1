$ErrorActionPreference = "Stop"
$base = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common"

function Move-Package($srcModule, $relPath, $dstModule) {
    $src = Join-Path $base "$srcModule\src\main\java\com\njydsz\pmis\common\$relPath"
    $dst = Join-Path $base "$dstModule\src\main\java\com\njydsz\pmis\common\$relPath"
    if (Test-Path $src) {
        $dstParent = Split-Path $dst -Parent
        if (-not (Test-Path $dstParent)) { New-Item -ItemType Directory -Path $dstParent -Force | Out-Null }
        Move-Item -Path $src -Destination $dst -Force
        Write-Host "  Moved: $srcModule/$relPath -> $dstModule/$relPath"
    } else {
        Write-Host "  SKIP (not found): $srcModule/$relPath"
    }
}

Write-Host "`n=== Step 4 (retry): Move Redis config from data to redis ==="
$dataConfigSrc = Join-Path $base "ydsz-pmis-common-data\src\main\java\com\njydsz\pmis\common\config"
$redisConfigDst = Join-Path $base "ydsz-pmis-common-redis\src\main\java\com\njydsz\pmis\common\config"
if (-not (Test-Path $redisConfigDst)) { New-Item -ItemType Directory -Path $redisConfigDst -Force | Out-Null }
$redisConfigs = @("BloomFilterConfig.java", "MultiLevelCacheConfig.java")
foreach ($f in $redisConfigs) {
    $srcFile = Join-Path $dataConfigSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $redisConfigDst -Force
        Write-Host "  Moved config: $f -> redis"
    }
}
# Move service package
Move-Package "ydsz-pmis-common-data" "service" "ydsz-pmis-common-redis"

Write-Host "`n=== Step 5: Move Lock from redis to lock ==="
Move-Package "ydsz-pmis-common-redis" "redis\lock" "ydsz-pmis-common-lock"

Write-Host "`n=== Step 6: Move Auth from security to auth ==="
Move-Package "ydsz-pmis-common-security" "permission" "ydsz-pmis-common-auth"
Move-Package "ydsz-pmis-common-security" "token" "ydsz-pmis-common-auth"
Move-Package "ydsz-pmis-common-security" "interceptor" "ydsz-pmis-common-auth"

# Move security auth classes
$secSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\security"
$authDst = Join-Path $base "ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\security"
if (-not (Test-Path $authDst)) { New-Item -ItemType Directory -Path $authDst -Force | Out-Null }
$authSecurityClasses = @(
    "LoginUser.java", "SecurityContext.java", "TenantContext.java",
    "DataScope.java", "DataScopeContext.java", "DataScopeHelper.java",
    "LoginStatus.java", "LoginAuditEvent.java", "AccountLockedEvent.java",
    "AccountLockInfo.java", "PasswordPolicy.java", "TotpUtil.java",
    "SensitiveOperationEvent.java", "DataExportAuditEvent.java", "CsrfSecurityPolicy.java",
    "package-info.java"
)
foreach ($f in $authSecurityClasses) {
    $srcFile = Join-Path $secSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $authDst -Force
        Write-Host "  Moved: security/$f -> auth"
    }
}

# Move auth annotations
$annSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\annotation"
$annDst = Join-Path $base "ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\annotation"
if (-not (Test-Path $annDst)) { New-Item -ItemType Directory -Path $annDst -Force | Out-Null }
$authAnnotations = @("PrePermission.java", "PreFieldPermission.java", "DataScope.java")
foreach ($f in $authAnnotations) {
    $srcFile = Join-Path $annSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $annDst -Force
        Write-Host "  Moved: annotation/$f -> auth"
    }
}

# Move auth aspects
$aspSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\aspect"
$aspDst = Join-Path $base "ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\aspect"
if (-not (Test-Path $aspDst)) { New-Item -ItemType Directory -Path $aspDst -Force | Out-Null }
$authAspects = @("PermissionAspect.java", "DataScopeAspect.java")
foreach ($f in $authAspects) {
    $srcFile = Join-Path $aspSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $aspDst -Force
        Write-Host "  Moved: aspect/$f -> auth"
    }
}

Write-Host "`n=== Step 7: Move Safe (remaining security) ==="
Move-Package "ydsz-pmis-common-security" "filter" "ydsz-pmis-common-safe"
Move-Package "ydsz-pmis-common-security" "sensitive" "ydsz-pmis-common-safe"
Move-Package "ydsz-pmis-common-security" "security\captcha" "ydsz-pmis-common-safe"
Move-Package "ydsz-pmis-common-security" "security\crypto" "ydsz-pmis-common-safe"
Move-Package "ydsz-pmis-common-security" "security\event" "ydsz-pmis-common-safe"
Move-Package "ydsz-pmis-common-security" "security\nonce" "ydsz-pmis-common-safe"
Move-Package "ydsz-pmis-common-security" "security\token" "ydsz-pmis-common-safe"

# Move remaining security files
$remainingSec = Get-ChildItem -Path $secSrc -Filter *.java -ErrorAction SilentlyContinue
if ($remainingSec) {
    $safeDst = Join-Path $base "ydsz-pmis-common-safe\src\main\java\com\njydsz\pmis\common\security"
    if (-not (Test-Path $safeDst)) { New-Item -ItemType Directory -Path $safeDst -Force | Out-Null }
    foreach ($f in $remainingSec) {
        Move-Item -Path $f.FullName -Destination $safeDst -Force
        Write-Host "  Moved: security/$($f.Name) -> safe"
    }
}

# Move safe annotations
$safeAnnotations = @("RateLimit.java", "Idempotent.java", "IdempotentExempt.java", "RequireReAuth.java")
foreach ($f in $safeAnnotations) {
    $srcFile = Join-Path $annSrc $f
    if (Test-Path $srcFile) {
        $safeAnnDst = Join-Path $base "ydsz-pmis-common-safe\src\main\java\com\njydsz\pmis\common\annotation"
        if (-not (Test-Path $safeAnnDst)) { New-Item -ItemType Directory -Path $safeAnnDst -Force | Out-Null }
        Move-Item -Path $srcFile -Destination $safeAnnDst -Force
        Write-Host "  Moved: annotation/$f -> safe"
    }
}

# Move safe aspects
$safeAspects = @("RateLimiterAspect.java", "IdempotentAspect.java", "RequireReAuthAspect.java")
foreach ($f in $safeAspects) {
    $srcFile = Join-Path $aspSrc $f
    if (Test-Path $srcFile) {
        $safeAspDst = Join-Path $base "ydsz-pmis-common-safe\src\main\java\com\njydsz\pmis\common\aspect"
        if (-not (Test-Path $safeAspDst)) { New-Item -ItemType Directory -Path $safeAspDst -Force | Out-Null }
        Move-Item -Path $srcFile -Destination $safeAspDst -Force
        Write-Host "  Moved: aspect/$f -> safe"
    }
}

# Move AuditFieldFiller and SecurityAutoConfiguration
$auditFillerSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\config\AuditFieldFiller.java"
if (Test-Path $auditFillerSrc) {
    $safeCfgDst = Join-Path $base "ydsz-pmis-common-safe\src\main\java\com\njydsz\pmis\common\config"
    if (-not (Test-Path $safeCfgDst)) { New-Item -ItemType Directory -Path $safeCfgDst -Force | Out-Null }
    Move-Item -Path $auditFillerSrc -Destination $safeCfgDst -Force
    Write-Host "  Moved: AuditFieldFiller.java -> safe"
}
$secAutoSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\SecurityAutoConfiguration.java"
if (Test-Path $secAutoSrc) {
    $safeRootDst = Join-Path $base "ydsz-pmis-common-safe\src\main\java\com\njydsz\pmis\common"
    Move-Item -Path $secAutoSrc -Destination $safeRootDst -Force
    Write-Host "  Moved: SecurityAutoConfiguration.java -> safe"
}

Write-Host "`n=== Step 8-10: Move audit/storage/mq from infra ==="
Move-Package "ydsz-pmis-common-infra" "audit" "ydsz-pmis-common-audit"
Move-Package "ydsz-pmis-common-infra" "storage" "ydsz-pmis-common-file"
Move-Package "ydsz-pmis-common-infra" "mq" "ydsz-pmis-common-queue"
$minioSrc = Join-Path $base "ydsz-pmis-common-infra\src\main\java\com\njydsz\pmis\common\config\MinioConfig.java"
if (Test-Path $minioSrc) {
    $fileCfgDst = Join-Path $base "ydsz-pmis-common-file\src\main\java\com\njydsz\pmis\common\config"
    if (-not (Test-Path $fileCfgDst)) { New-Item -ItemType Directory -Path $fileCfgDst -Force | Out-Null }
    Move-Item -Path $minioSrc -Destination $fileCfgDst -Force
    Write-Host "  Moved: MinioConfig.java -> file"
}

Write-Host "`n=== Step 11: Move doc from web ==="
$webConfigSrc = Join-Path $base "ydsz-pmis-common-web\src\main\java\com\njydsz\pmis\common\config"
$docConfigDst = Join-Path $base "ydsz-pmis-common-doc\src\main\java\com\njydsz\pmis\common\config"
if (-not (Test-Path $docConfigDst)) { New-Item -ItemType Directory -Path $docConfigDst -Force | Out-Null }
$docConfigs = @("Knife4jConfig.java", "OpenApiConfig.java")
foreach ($f in $docConfigs) {
    $srcFile = Join-Path $webConfigSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $docConfigDst -Force
        Write-Host "  Moved: config/$f -> doc"
    }
}

Write-Host "`n=== Step 12: Move resources and cleanup ==="
# Move security imports to safe
$secImports = Join-Path $base "ydsz-pmis-common-security\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports"
if (Test-Path $secImports) {
    $safeResDst = Join-Path $base "ydsz-pmis-common-safe\src\main\resources\META-INF\spring"
    Move-Item -Path $secImports -Destination $safeResDst -Force
    Write-Host "  Moved: security imports -> safe"
}
# Move data imports to jdbc
$dataImports = Join-Path $base "ydsz-pmis-common-data\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports"
if (Test-Path $dataImports) {
    $jdbcResDst = Join-Path $base "ydsz-pmis-common-jdbc\src\main\resources\META-INF\spring"
    Move-Item -Path $dataImports -Destination $jdbcResDst -Force
    Write-Host "  Moved: data imports -> jdbc"
}

# Remove old security and data module dirs
$secPath = Join-Path $base "ydsz-pmis-common-security"
$dataPath = Join-Path $base "ydsz-pmis-common-data"
if (Test-Path $secPath) { Remove-Item -Path $secPath -Recurse -Force -ErrorAction SilentlyContinue; Write-Host "  Removed: security module dir" }
if (Test-Path $dataPath) { Remove-Item -Path $dataPath -Recurse -Force -ErrorAction SilentlyContinue; Write-Host "  Removed: data module dir" }

Write-Host "`n=== Phase 1 file moves complete ==="
