# Restore missing PMIS-specific classes from git history - Version 2
# Uses git show with file output to preserve newlines
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

# Step 2: List of missing classes to restore
$missingClasses = @(
    "MockLlmClient","OpenAICompatibleLlmClient","LlmClient","LlmClientConfig","LlmException",
    "UnifiedAlertEvent",
    "DataScope","Idempotent","IdempotentExempt","OperationLog","PrePermission","RateLimit","RequireReAuth",
    "BizErrorCode","Result",
    "DataScopeAspect","RequireReAuthAspect",
    "ChaosExperiment","ChaosOutcome","ChaosService",
    "MinioConfig","ThresholdProvider",
    "AsyncExecutorNames","CommonConstants","PmisMessageTopics","SystemConstants",
    "DagFailureStrategy","DagGraph","DagInstanceStatus","DagNodeStatus",
    "DataSourceConstants",
    "BaseDO","CursorPageResult","LogBaseDO","VersionableDO",
    "OperationLogEvent","ProjectChangeExecutedEvent",
    "ExcelTemplate","ExcelUtil",
    "BizException",
    "FeatureFlag","FeatureFlagService","FeatureFlagSnapshot",
    "NotificationFeignDTO","RealtimePushDTO","FeignClientConstants","MessageRequest","MessageResult","MessageServiceClient","NotificationClient",
    "JobContextHolder","JobHandler","JobLogger","JobLoggerHolder","JobRunRecorder","MapContext","MapProcessor","MapReduceProcessor","MapTask","ProcessResult","ShardingContext",
    "AbstractModuleMetrics",
    "PermissionCodes",
    "AccountLockedEvent","AccountLockInfo","DataExportAuditEvent","DataScopeHelper","LoginAuditEvent","LoginStatus","LoginUser","PasswordPolicy","SecurityContext","SensitiveOperationEvent","TenantContext","TotpUtil",
    "SensitiveStrategy",
    "BloomFilterService",
    "JwtTokenProvider",
    "CryptoSignUtil","CryptoUtil","CursorHelper","InternalHeaderSigner","IpUtils","PathGuard","SnowflakeIdGenerator","SortBy","TraceIdUtil",
    "WebhookDispatcher","WebhookSubscription"
)

Write-Host "Need to restore $($missingClasses.Count) classes"

# Step 3: For each old file, restore using git show > file
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

    # Extract the submodule name and relative Java path
    $relativePath = $oldFile -replace "ydsz-pmis-backend/ydsz-pmis-common/", ""
    $moduleMatch = [regex]::Match($relativePath, "^(ydsz-pmis-common-[^/]+)/(.+)$")
    if (-not $moduleMatch.Success) {
        Write-Host "WARN: Could not parse module from: $oldFile"
        $failed++
        continue
    }
    $moduleName = $moduleMatch.Groups[1].Value
    $javaRelativePath = $moduleMatch.Groups[2].Value  # e.g., src/main/java/com/njydsz/pmis/common/api/Result.java

    # Construct the target file path
    $targetFilePath = Join-Path $commonDir "$moduleName\$($javaRelativePath -replace '/', '\')"
    $targetDir = Split-Path $targetFilePath -Parent

    # Create directory if needed
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }

    # Use git show with output redirection to preserve newlines
    # Use cmd.exe for reliable file redirection
    $tempFile = [System.IO.Path]::GetTempFileName()
    $gitCmd = "git show `"${oldCommit}:$oldFile`" > `"$tempFile`""
    cmd /c $gitCmd 2>&1 | Out-Null

    if (Test-Path $tempFile) {
        $content = [System.IO.File]::ReadAllText($tempFile)

        # Remove BOM if present
        if ($content.Length -gt 0 -and $content[0] -eq [char]0xFEFF) {
            $content = $content.Substring(1)
        }

        # Write with UTF-8 no BOM
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($targetFilePath, $content, $utf8NoBom)

        Remove-Item $tempFile -Force
        Write-Host "RESTORED: $className => $moduleName"
        $restored++
    } else {
        Write-Host "FAIL: $className - git show failed"
        $failed++
    }
}

Write-Host ""
Write-Host "=== Summary ==="
Write-Host "Restored: $restored"
Write-Host "Failed: $failed"
