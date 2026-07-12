# Restore missing PMIS-specific classes from git history
# Commit fa6c7acf^ has the old common module before deletion
$ErrorActionPreference = "Stop"

$repoRoot = "d:/Code/ydsz/ydsz-pmis"
$oldCommit = "fa6c7acf^"
$commonDir = "$repoRoot/ydsz-pmis-backend/ydsz-pmis-common"

# Step 1: Get the list of all old Java files from git
Write-Host "Step 1: Getting old common module file list from git..."
$oldFiles = git ls-tree -r --name-only $oldCommit "ydsz-pmis-backend/ydsz-pmis-common/" | Where-Object {
    $_ -like "*.java" -and $_ -notlike "*target*" -and $_ -notlike "*test*"
}

Write-Host "Found $($oldFiles.Count) old Java files"

# Step 2: Get the list of missing classes (from business module imports)
$missingClasses = @(
    "MockLlmClient","OpenAICompatibleLlmClient","LlmClient","LlmClientConfig","LlmException",
    "UnifiedAlertEvent",
    "DataScope","Idempotent","IdempotentExempt","OperationLog","PrePermission","RateLimit",
    "BizErrorCode","Result",
    "DataScopeAspect",
    "ChaosExperiment","ChaosOutcome","ChaosService",
    "MinioConfig","ThresholdProvider",
    "AsyncExecutorNames","CommonConstants","PmisMessageTopics","SystemConstants",
    "DagFailureStrategy","DagGraph","DagInstanceStatus","DagNodeStatus",
    "DataSourceConstants",
    "BaseDO","CursorPageResult","LogBaseDO","VersionableDO",
    "OperationLogEvent","ProjectChangeExecutedEvent",
    "ExcelTemplate","ExcelUtil",
    "SysException",
    "FeatureFlag","FeatureFlagService","FeatureFlagSnapshot",
    "NotificationFeignDTO","RealtimePushDTO","FeignClientConstants","MessageRequest","MessageResult","MessageServiceClient","NotificationClient",
    "JobContextHolder","JobHandler","JobLogger","JobLoggerHolder","JobRunRecorder","MapContext","MapProcessor","MapReduceProcessor","MapTask","ProcessResult","ShardingContext",
    "AbstractModuleMetrics",
    "PermissionCodes",
    "AccountLockedEvent","AccountLockInfo","DataExportAuditEvent","DataScopeHelper","LoginAuditEvent","LoginStatus","LoginUser","PasswordPolicy","SecurityContext","TenantContext","TotpUtil",
    "SensitiveStrategy",
    "BloomFilterService",
    "JwtTokenProvider",
    "CryptoSignUtil","CryptoUtil","CursorHelper","InternalHeaderSigner","IpUtils","PathGuard","SnowflakeIdGenerator","SortBy","TraceIdUtil",
    "WebhookDispatcher","WebhookSubscription"
)

Write-Host "Need to restore $($missingClasses.Count) classes"

# Step 3: For each old file, check if it's a missing class and restore it
$restored = 0
$skipped = 0
$failed = 0

foreach ($oldFile in $oldFiles) {
    # Extract class name from file path
    $fileName = Split-Path $oldFile -Leaf
    $className = $fileName -replace ".java", ""

    # Check if this class is in the missing list
    if ($className -notin $missingClasses) {
        continue
    }

    # Extract the submodule name from the path
    # Path format: ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-{module}/src/main/java/com/njydsz/pmis/common/...
    $relativePath = $oldFile -replace "ydsz-pmis-backend/ydsz-pmis-common/", ""
    $moduleMatch = [regex]::Match($relativePath, "^(ydsz-pmis-common-[^/]+)/(.+)$")
    if (-not $moduleMatch.Success) {
        Write-Host "WARN: Could not parse module from: $oldFile"
        $failed++
        continue
    }
    $oldModule = $moduleMatch.Groups[1].Value
    $javaRelativePath = $moduleMatch.Groups[2].Value

    # The new common module has the same module names
    $newFilePath = "$commonDir/$oldModule/src/main/java/$($javaRelativePath -replace '/', '/')"
    $newFileDir = Split-Path $newFilePath -Parent

    # Check if file already exists in new common (skip if it does)
    if (Test-Path $newFilePath) {
        Write-Host "SKIP (exists): $className in $oldModule"
        $skipped++
        continue
    }

    # Get file content from git
    $gitPath = $oldFile -replace "/", "`\"
    try {
        $content = git show "${oldCommit}:$oldFile" 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "FAIL (git show): $className - $content"
            $failed++
            continue
        }

        # Create directory if needed
        if (-not (Test-Path $newFileDir)) {
            New-Item -ItemType Directory -Path $newFileDir -Force | Out-Null
        }

        # Write file without BOM
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($newFilePath, $content, $utf8NoBom)

        Write-Host "RESTORED: $className => $oldModule"
        $restored++
    } catch {
        Write-Host "FAIL: $className - $_"
        $failed++
    }
}

Write-Host ""
Write-Host "=== Summary ==="
Write-Host "Restored: $restored"
Write-Host "Skipped (already exist): $skipped"
Write-Host "Failed: $failed"
