$base = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common"
$src = "$base\src\main\java\com\njydsz\pmis\common"

# Step 1: Delete annotation, aspect, config from ALL sub-modules
$modules = @("ydsz-pmis-common-core","ydsz-pmis-common-util","ydsz-pmis-common-domain","ydsz-pmis-common-data","ydsz-pmis-common-security","ydsz-pmis-common-feign","ydsz-pmis-common-web","ydsz-pmis-common-infra","ydsz-pmis-common-all")
foreach($m in $modules) {
    foreach($d in @("annotation","aspect","config")) {
        $path = "$base\$m\src\main\java\com\njydsz\pmis\common\$d"
        if (Test-Path $path) {
            Remove-Item -Path $path -Recurse -Force
            Write-Output "  Deleted $m\$d"
        }
    }
}

# Step 2: Re-create annotation in the correct modules
function CopyFile($srcFile, $destModule, $pkg) {
    $destDir = "$base\$destModule\src\main\java\com\njydsz\pmis\common\$pkg"
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    Copy-Item -Path $srcFile -Destination "$destDir\$(Split-Path $srcFile -Leaf)" -Force
}

# Annotations
$annSrc = "$src\annotation"
# Data annotations
CopyFile "$annSrc\DistributedLock.java" "ydsz-pmis-common-data" "annotation"
# Security annotations
foreach($f in @("PrePermission.java","RateLimit.java","Idempotent.java","IdempotentExempt.java","RequireReAuth.java","DataScope.java","PreAuthorize.java")) {
    if (Test-Path "$annSrc\$f") { CopyFile "$annSrc\$f" "ydsz-pmis-common-security" "annotation" }
}
# Web annotations
foreach($f in @("OperationLog.java","DataExportAudit.java","ApiMetrics.java")) {
    if (Test-Path "$annSrc\$f") { CopyFile "$annSrc\$f" "ydsz-pmis-common-web" "annotation" }
}

# Aspects
$aspSrc = "$src\aspect"
# Data aspects
foreach($f in @("DistributedLockAspect.java")) {
    if (Test-Path "$aspSrc\$f") { CopyFile "$aspSrc\$f" "ydsz-pmis-common-data" "aspect" }
}
# Security aspects
foreach($f in @("PermissionAspect.java","RateLimiterAspect.java","IdempotentAspect.java","RequireReAuthAspect.java","DataScopeAspect.java","PreAuthorizeAspect.java")) {
    if (Test-Path "$aspSrc\$f") { CopyFile "$aspSrc\$f" "ydsz-pmis-common-security" "aspect" }
}
# Web aspects
foreach($f in @("OperationLogAspect.java","DataExportAuditAspect.java","ApiMetricsAspect.java")) {
    if (Test-Path "$aspSrc\$f") { CopyFile "$aspSrc\$f" "ydsz-pmis-common-web" "aspect" }
}

# Config
$cfgSrc = "$src\config"
# Data configs
foreach($f in @("MybatisPlusAutoConfiguration.java","AuditFieldFiller.java","PmisTenantLineHandler.java","PmisCacheConfig.java","BloomFilterConfig.java","MultiLevelCacheConfig.java","DruidMonitorEnabler.java","DataPermissionConfig.java")) {
    if (Test-Path "$cfgSrc\$f") { CopyFile "$cfgSrc\$f" "ydsz-pmis-common-data" "config" }
}
# Web configs
foreach($f in @("WebMvcConfig.java","OpenApiConfig.java","I18nConfig.java","ApiVersionConfig.java","CommonAutoConfiguration.java","GlobalResponseAdvice.java","CorsConfig.java")) {
    if (Test-Path "$cfgSrc\$f") { CopyFile "$cfgSrc\$f" "ydsz-pmis-common-web" "config" }
}
# Infra configs
foreach($f in @("AsyncAutoConfiguration.java","AsyncThreadPoolConfig.java","Resilience4jConfig.java","SeataAutoConfiguration.java","SentinelAutoConfiguration.java","SentinelRuleConfig.java","ThresholdProvider.java","MinioConfig.java","JasyptConfig.java","SentryConfig.java","TracingConfig.java")) {
    if (Test-Path "$cfgSrc\$f") { CopyFile "$cfgSrc\$f" "ydsz-pmis-common-infra" "config" }
}
# Security configs
foreach($f in @("SecurityAutoConfiguration.java","JwtAutoConfiguration.java","SensitiveAutoConfiguration.java","FilterAutoConfiguration.java","RateLimitConfig.java","CaptchaConfig.java","PermissionConfig.java")) {
    if (Test-Path "$cfgSrc\$f") { CopyFile "$cfgSrc\$f" "ydsz-pmis-common-security" "config" }
}

# Step 3: Check for any remaining files in source annotation/aspect/config not yet copied
Write-Output "`n=== Uncopied annotation files ==="
Get-ChildItem "$annSrc" -Filter "*.java" | ForEach-Object { Write-Output "  $($_.Name)" }
Write-Output "`n=== Uncopied aspect files ==="
Get-ChildItem "$aspSrc" -Filter "*.java" | ForEach-Object { Write-Output "  $($_.Name)" }
Write-Output "`n=== Uncopied config files ==="
Get-ChildItem "$cfgSrc" -Filter "*.java" | ForEach-Object { Write-Output "  $($_.Name)" }

# Step 4: Final count
Write-Output "`n=== Final Summary ==="
foreach($m in $modules){
    $count = (Get-ChildItem -Path "$base\$m\src" -Recurse -Filter "*.java" -ErrorAction SilentlyContinue | Measure-Object).Count
    Write-Output "  $m = $count java files"
}
