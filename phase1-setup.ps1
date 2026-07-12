$ErrorActionPreference = "Stop"
$base = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common"

# ============================================================
# Phase 1: Module Structure Alignment
# Create all new module directories and move Java files
# ============================================================

# Helper: create module directory structure
function New-Module($name) {
    $path = Join-Path $base $name
    $srcPath = Join-Path $path "src\main\java\com\njydsz\pmis\common"
    $resPath = Join-Path $path "src\main\resources\META-INF\spring"
    if (-not (Test-Path $srcPath)) { New-Item -ItemType Directory -Path $srcPath -Force | Out-Null }
    if (-not (Test-Path $resPath)) { New-Item -ItemType Directory -Path $resPath -Force | Out-Null }
    Write-Host "  Created module dir: $name"
}

# Helper: move directory preserving structure
function Move-Package($srcModule, $relPath, $dstModule) {
    $src = Join-Path $base "$srcModule\src\main\java\com\njydsz\pmis\common\$relPath"
    $dst = Join-Path $base "$dstModule\src\main\java\com\njydsz\pmis\common\$relPath"
    if (Test-Path $src) {
        $dstParent = Split-Path $dst -Parent
        if (-not (Test-Path $dstParent)) { New-Item -ItemType Directory -Path $dstParent -Force | Out-Null }
        Move-Item -Path $src -Destination $dst -Force
        Write-Host "  Moved: $srcModule/$relPath -> $dstModule/$relPath"
    } else {
        Write-Host "  WARN: Source not found: $src"
    }
}

Write-Host "`n=== Step 1: Create all new module directories ==="
$newModules = @(
    "ydsz-pmis-common-exception",
    "ydsz-pmis-common-jdbc",
    "ydsz-pmis-common-redis",
    "ydsz-pmis-common-lock",
    "ydsz-pmis-common-auth",
    "ydsz-pmis-common-safe",
    "ydsz-pmis-common-audit",
    "ydsz-pmis-common-file",
    "ydsz-pmis-common-notify",
    "ydsz-pmis-common-queue",
    "ydsz-pmis-common-doc",
    "ydsz-pmis-common-base",
    "ydsz-pmis-common-app",
)
foreach ($m in $newModules) { New-Module $m }

Write-Host "`n=== Step 2: Move exception package from core to exception module ==="
Move-Package "ydsz-pmis-common-core" "exception" "ydsz-pmis-common-exception"

Write-Host "`n=== Step 3: Move JDBC classes from data to jdbc module ==="
# Move config classes related to JDBC/MyBatis/DataSource
$jdbcConfigs = @("MybatisPlusAutoConfiguration.java", "PmisTenantLineHandler.java", "TenantContextHolder.java", "DruidMonitorEnabler.java", "PmisCacheConfig.java")
$dataConfigSrc = Join-Path $base "ydsz-pmis-common-data\src\main\java\com\njydsz\pmis\common\config"
$jdbcConfigDst = Join-Path $base "ydsz-pmis-common-jdbc\src\main\java\com\njydsz\pmis\common\config"
if (-not (Test-Path $jdbcConfigDst)) { New-Item -ItemType Directory -Path $jdbcConfigDst -Force | Out-Null }
foreach ($f in $jdbcConfigs) {
    $srcFile = Join-Path $dataConfigSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $jdbcConfigDst -Force
        Write-Host "  Moved config: $f"
    }
}
# Move datasource package
Move-Package "ydsz-pmis-common-data" "datasource" "ydsz-pmis-common-jdbc"
# Move DistributedLock annotation and aspect (JDBC-related, not Redis)
$dlSrc = Join-Path $base "ydsz-pmis-common-data\src\main\java\com\njydsz\pmis\common\annotation"
$dlDst = Join-Path $base "ydsz-pmis-common-jdbc\src\main\java\com\njydsz\pmis\common\annotation"
if (-not (Test-Path $dlDst)) { New-Item -ItemType Directory -Path $dlDst -Force | Out-Null }
$dlFile = Join-Path $dlSrc "DistributedLock.java"
if (Test-Path $dlFile) {
    Move-Item -Path $dlFile -Destination $dlDst -Force
    Write-Host "  Moved: DistributedLock.java annotation"
}
$dlAspectSrc = Join-Path $base "ydsz-pmis-common-data\src\main\java\com\njydsz\pmis\common\aspect"
$dlAspectDst = Join-Path $base "ydsz-pmis-common-jdbc\src\main\java\com\njydsz\pmis\common\aspect"
if (-not (Test-Path $dlAspectDst)) { New-Item -ItemType Directory -Path $dlAspectDst -Force | Out-Null }
$dlAspectFile = Join-Path $dlAspectSrc "DistributedLockAspect.java"
if (Test-Path $dlAspectFile) {
    Move-Item -Path $dlAspectFile -Destination $dlAspectDst -Force
    Write-Host "  Moved: DistributedLockAspect.java"
}

Write-Host "`n=== Step 4: Move Redis classes from data to redis module ==="
Move-Package "ydsz-pmis-common-data" "redis" "ydsz-pmis-common-redis"
# Move cache/bloom config and service
$redisConfigs = @("BloomFilterConfig.java", "MultiLevelCacheConfig.java")
foreach ($f in $redisConfigs) {
    $srcFile = Join-Path $dataConfigSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $jdbcConfigDst -Parent -ErrorAction SilentlyContinue
        # Actually move to redis module
        $redisConfigDst = Join-Path $base "ydsz-pmis-common-redis\src\main\java\com\njydsz\pmis\common\config"
        if (-not (Test-Path $redisConfigDst)) { New-Item -ItemType Directory -Path $redisConfigDst -Force | Out-Null }
        Move-Item -Path $srcFile -Destination $redisConfigDst -Force
        Write-Host "  Moved config: $f -> redis"
    }
}
# Move service package (BloomFilterService, MultiLevelCacheService)
Move-Package "ydsz-pmis-common-data" "service" "ydsz-pmis-common-redis"

Write-Host "`n=== Step 5: Move Lock classes from redis to lock module ==="
# The redis/lock package was moved to redis module in step 4, now move it to lock
Move-Package "ydsz-pmis-common-redis" "redis\lock" "ydsz-pmis-common-lock"
# Also move the lock-related files that are in redis/lock
$lockSrc = Join-Path $base "ydsz-pmis-common-redis\src\main\java\com\njydsz\pmis\common\redis\lock"
if (Test-Path $lockSrc) {
    $lockDst = Join-Path $base "ydsz-pmis-common-lock\src\main\java\com\njydsz\pmis\common\redis\lock"
    $lockDstParent = Split-Path $lockDst -Parent
    if (-not (Test-Path $lockDstParent)) { New-Item -ItemType Directory -Path $lockDstParent -Force | Out-Null }
    Move-Item -Path $lockSrc -Destination $lockDst -Force
    Write-Host "  Moved: redis/lock -> lock module"
}

Write-Host "`n=== Step 6: Move Auth classes from security to auth module ==="
# Move permission package
Move-Package "ydsz-pmis-common-security" "permission" "ydsz-pmis-common-auth"
# Move token package
Move-Package "ydsz-pmis-common-security" "token" "ydsz-pmis-common-auth"
# Move security-related auth classes (LoginUser, SecurityContext, TenantContext, DataScope, etc.)
$authSecurityClasses = @(
    "LoginUser.java", "SecurityContext.java", "TenantContext.java",
    "DataScope.java", "DataScopeContext.java", "DataScopeHelper.java",
    "LoginStatus.java", "LoginAuditEvent.java", "AccountLockedEvent.java",
    "AccountLockInfo.java", "PasswordPolicy.java", "TotpUtil.java",
    "SensitiveOperationEvent.java", "DataExportAuditEvent.java", "CsrfSecurityPolicy.java",
    "package-info.java"
)
$secSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\security"
$authDst = Join-Path $base "ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\security"
if (-not (Test-Path $authDst)) { New-Item -ItemType Directory -Path $authDst -Force | Out-Null }
foreach ($f in $authSecurityClasses) {
    $srcFile = Join-Path $secSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $authDst -Force
        Write-Host "  Moved: security/$f -> auth"
    }
}
# Move auth-related annotations
$authAnnotations = @("PrePermission.java", "PreFieldPermission.java", "DataScope.java")
$annSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\annotation"
$annDst = Join-Path $base "ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\annotation"
if (-not (Test-Path $annDst)) { New-Item -ItemType Directory -Path $annDst -Force | Out-Null }
foreach ($f in $authAnnotations) {
    $srcFile = Join-Path $annSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $annDst -Force
        Write-Host "  Moved: annotation/$f -> auth"
    }
}
# Move auth-related aspects
$authAspects = @("PermissionAspect.java", "DataScopeAspect.java")
$aspSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\aspect"
$aspDst = Join-Path $base "ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\aspect"
if (-not (Test-Path $aspDst)) { New-Item -ItemType Directory -Path $aspDst -Force | Out-Null }
foreach ($f in $authAspects) {
    $srcFile = Join-Path $aspSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $aspDst -Force
        Write-Host "  Moved: aspect/$f -> auth"
    }
}
# Move interceptor (AuthInterceptor)
$intSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\interceptor"
$intDst = Join-Path $base "ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\interceptor"
if (-not (Test-Path $intDst)) { New-Item -ItemType Directory -Path $intDst -Force | Out-Null }
$authIntFile = Join-Path $intSrc "AuthInterceptor.java"
if (Test-Path $authIntFile) {
    Move-Item -Path $authIntFile -Destination $intDst -Force
    Write-Host "  Moved: interceptor/AuthInterceptor.java -> auth"
}
$intPkgFile = Join-Path $intSrc "package-info.java"
if (Test-Path $intPkgFile) {
    Move-Item -Path $intPkgFile -Destination $intDst -Force
    Write-Host "  Moved: interceptor/package-info.java -> auth"
}

Write-Host "`n=== Step 7: Safe module keeps remaining security classes ==="
# The remaining classes in security module (filter, sensitive, captcha, crypto, etc.) stay
# but we need to rename the module directory
# Actually, we'll create safe module and move the remaining security files there
# Then the security module directory will be removed

# Move filter package
Move-Package "ydsz-pmis-common-security" "filter" "ydsz-pmis-common-safe"
# Move sensitive package
Move-Package "ydsz-pmis-common-security" "sensitive" "ydsz-pmis-common-safe"
# Move security/captcha
Move-Package "ydsz-pmis-common-security" "security\captcha" "ydsz-pmis-common-safe"
# Move security/crypto
Move-Package "ydsz-pmis-common-security" "security\crypto" "ydsz-pmis-common-safe"
# Move security/event
Move-Package "ydsz-pmis-common-security" "security\event" "ydsz-pmis-common-safe"
# Move security/nonce
Move-Package "ydsz-pmis-common-security" "security\nonce" "ydsz-pmis-common-safe"
# Move security/token
Move-Package "ydsz-pmis-common-security" "security\token" "ydsz-pmis-common-safe"
# Move remaining security files
$remainingSec = Get-ChildItem -Path $secSrc -Filter *.java -ErrorAction SilentlyContinue
if ($remainingSec) {
    foreach ($f in $remainingSec) {
        $safeDst = Join-Path $base "ydsz-pmis-common-safe\src\main\java\com\njydsz\pmis\common\security"
        if (-not (Test-Path $safeDst)) { New-Item -ItemType Directory -Path $safeDst -Force | Out-Null }
        Move-Item -Path $f.FullName -Destination $safeDst -Force
        Write-Host "  Moved: security/$($f.Name) -> safe"
    }
}
# Move safe-related annotations (RateLimit, Idempotent, IdempotentExempt, RequireReAuth)
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
# Move safe-related aspects (RateLimiterAspect, IdempotentAspect, RequireReAuthAspect)
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
# Move AuditFieldFiller from config
$auditFillerSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\config\AuditFieldFiller.java"
if (Test-Path $auditFillerSrc) {
    $safeCfgDst = Join-Path $base "ydsz-pmis-common-safe\src\main\java\com\njydsz\pmis\common\config"
    if (-not (Test-Path $safeCfgDst)) { New-Item -ItemType Directory -Path $safeCfgDst -Force | Out-Null }
    Move-Item -Path $auditFillerSrc -Destination $safeCfgDst -Force
    Write-Host "  Moved: config/AuditFieldFiller.java -> safe"
}
# Move SecurityAutoConfiguration
$secAutoSrc = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common\SecurityAutoConfiguration.java"
if (Test-Path $secAutoSrc) {
    $safeRootDst = Join-Path $base "ydsz-pmis-common-safe\src\main\java\com\njydsz\pmis\common"
    if (-not (Test-Path $safeRootDst)) { New-Item -ItemType Directory -Path $safeRootDst -Force | Out-Null }
    Move-Item -Path $secAutoSrc -Destination $safeRootDst -Force
    Write-Host "  Moved: SecurityAutoConfiguration.java -> safe"
}

Write-Host "`n=== Step 8: Move audit from infra to audit module ==="
Move-Package "ydsz-pmis-common-infra" "audit" "ydsz-pmis-common-audit"

Write-Host "`n=== Step 9: Move storage from infra to file module ==="
Move-Package "ydsz-pmis-common-infra" "storage" "ydsz-pmis-common-file"
# Move MinioConfig
$minioSrc = Join-Path $base "ydsz-pmis-common-infra\src\main\java\com\njydsz\pmis\common\config\MinioConfig.java"
if (Test-Path $minioSrc) {
    $fileCfgDst = Join-Path $base "ydsz-pmis-common-file\src\main\java\com\njydsz\pmis\common\config"
    if (-not (Test-Path $fileCfgDst)) { New-Item -ItemType Directory -Path $fileCfgDst -Force | Out-Null }
    Move-Item -Path $minioSrc -Destination $fileCfgDst -Force
    Write-Host "  Moved: config/MinioConfig.java -> file"
}

Write-Host "`n=== Step 10: Move mq from infra to queue module ==="
Move-Package "ydsz-pmis-common-infra" "mq" "ydsz-pmis-common-queue"

Write-Host "`n=== Step 11: Move doc config from web to doc module ==="
$docConfigs = @("Knife4jConfig.java", "OpenApiConfig.java")
$webConfigSrc = Join-Path $base "ydsz-pmis-common-web\src\main\java\com\njydsz\pmis\common\config"
$docConfigDst = Join-Path $base "ydsz-pmis-common-doc\src\main\java\com\njydsz\pmis\common\config"
if (-not (Test-Path $docConfigDst)) { New-Item -ItemType Directory -Path $docConfigDst -Force | Out-Null }
foreach ($f in $docConfigs) {
    $srcFile = Join-Path $webConfigSrc $f
    if (Test-Path $srcFile) {
        Move-Item -Path $srcFile -Destination $docConfigDst -Force
        Write-Host "  Moved: config/$f -> doc"
    }
}

# Move web resources (mapper, config) - keep in web for now
# Move web's application config files

Write-Host "`n=== Step 12: Clean up empty directories ==="
# Remove empty security module directory (all files moved out)
$secModulePath = Join-Path $base "ydsz-pmis-common-security\src\main\java\com\njydsz\pmis\common"
$remainingFiles = Get-ChildItem -Path $secModulePath -Recurse -Filter *.java -ErrorAction SilentlyContinue
if (-not $remainingFiles) {
    # Move the spring imports file
    $secImports = Join-Path $base "ydsz-pmis-common-security\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports"
    if (Test-Path $secImports) {
        $safeResDst = Join-Path $base "ydsz-pmis-common-safe\src\main\resources\META-INF\spring"
        if (-not (Test-Path $safeResDst)) { New-Item -ItemType Directory -Path $safeResDst -Force | Out-Null }
        Move-Item -Path $secImports -Destination $safeResDst -Force
        Write-Host "  Moved: AutoConfiguration.imports -> safe"
    }
    # Remove the old security module directory
    Remove-Item -Path (Join-Path $base "ydsz-pmis-common-security") -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "  Removed empty security module directory"
}

# Clean up empty data module if all files moved
$dataModulePath = Join-Path $base "ydsz-pmis-common-data\src\main\java\com\njydsz\pmis\common"
$remainingDataFiles = Get-ChildItem -Path $dataModulePath -Recurse -Filter *.java -ErrorAction SilentlyContinue
if (-not $remainingDataFiles) {
    $dataImports = Join-Path $base "ydsz-pmis-common-data\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports"
    if (Test-Path $dataImports) {
        # Split: jdbc configs go to jdbc, redis/lock go to respective modules
        $content = Get-Content $dataImports -Raw
        # Move to jdbc module
        $jdbcResDst = Join-Path $base "ydsz-pmis-common-jdbc\src\main\resources\META-INF\spring"
        if (-not (Test-Path $jdbcResDst)) { New-Item -ItemType Directory -Path $jdbcResDst -Force | Out-Null }
        Move-Item -Path $dataImports -Destination $jdbcResDst -Force
        Write-Host "  Moved: data AutoConfiguration.imports -> jdbc"
    }
    Remove-Item -Path (Join-Path $base "ydsz-pmis-common-data") -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "  Removed empty data module directory"
}

Write-Host "`n=== Phase 1 file moves complete ==="
