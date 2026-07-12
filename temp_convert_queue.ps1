# Queue module conversion script
$sourceBase = "D:\Code\remi\vip\platform\remi-comm\remi-comm-queue\src\main"
$targetBase = "D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-queue\src\main"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$sourceJavaRoot = Join-Path $sourceBase "java\com\remisoft\comm\queue"
$targetJavaRoot = Join-Path $targetBase "java\com\njydsz\pmis\common\queue"

Write-Output "Source: $sourceJavaRoot"
Write-Output "Target: $targetJavaRoot"

# Clean target java directory (remove old mq package too)
$oldMqRoot = Join-Path $targetBase "java\com\njydsz\pmis\common\mq"
if (Test-Path $oldMqRoot) {
    Remove-Item -Recurse -Force $oldMqRoot
    Write-Output "Removed old mq package: $oldMqRoot"
}
if (Test-Path $targetJavaRoot) {
    Remove-Item -Recurse -Force $targetJavaRoot
}
New-Item -ItemType Directory -Force -Path $targetJavaRoot | Out-Null

# Clean and re-copy resources
$targetRes = Join-Path $targetBase 'resources'
if (Test-Path $targetRes) {
    Remove-Item -Recurse -Force $targetRes
}

$fileCount = 0

# Get all Java files recursively from source root
$sourceFiles = Get-ChildItem -Path $sourceJavaRoot -Recurse -Filter '*.java' -ErrorAction SilentlyContinue

foreach ($srcFile in $sourceFiles) {
    # Skip HealthIndicator files
    if ($srcFile.Name -match 'HealthIndicator') {
        Write-Output "  Skipped: $($srcFile.Name) (actuator dependency)"
        continue
    }

    # Calculate relative path from source root
    $relPath = $srcFile.FullName.Substring($sourceJavaRoot.Length)

    # Determine target path
    $targetFile = Join-Path $targetJavaRoot $relPath

    # Create directory if needed
    $targetDir2 = Split-Path $targetFile -Parent
    if (-not (Test-Path $targetDir2)) {
        New-Item -ItemType Directory -Force -Path $targetDir2 | Out-Null
    }

    # Read file content
    $content = [System.IO.File]::ReadAllText($srcFile.FullName, [System.Text.Encoding]::UTF8)

    # Remove BOM if present
    $content = $content -replace "^\xEF\xBB\xBF", ""

    # 1. Replace package names
    $content = $content -replace 'com\.remisoft\.comm', 'com.njydsz.pmis.common'

    # 2. Replace EnableRemi* with Enable*
    $content = $content -replace 'EnableRemi(\w+)', 'Enable$1'

    # 3. Replace remi references in strings/logs
    $content = $content -replace 'remi-comm-redis', 'pmis-common-redis'
    $content = $content -replace 'remi-comm', 'pmis-common'
    $content = $content -replace 'remi\.queue', 'pmis.queue'

    # 4. Replace author info
    $content = $content -replace '@author Marvin Lee', '@author ydsz-pmis-team'
    $content = $content -replace '@email limw1888@126\.com', ''
    $content = $content -replace '@version 3\.5\.0', ''

    # 5. Fix BusinessException builder pattern -> BizException constructor (multiline)
    # Pattern: BusinessException.builder().key(X).cause(Y).build() -> new BizException(X, Y)
    $content = [regex]::Replace($content,
        'BusinessException\.builder\(\)\s*\.\s*key\((.*?)\)\s*\.\s*cause\((.*?)\)\s*\.\s*build\(\)',
        'new BizException($1, $2)',
        [System.Text.RegularExpressions.RegexOptions]::Singleline)

    # Pattern: BusinessException.builder().key(X).build() -> new BizException(X)
    $content = [regex]::Replace($content,
        'BusinessException\.builder\(\)\s*\.\s*key\((.*?)\)\s*\.\s*build\(\)',
        'new BizException($1)',
        [System.Text.RegularExpressions.RegexOptions]::Singleline)

    # 6. Replace BusinessException import
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.exception\.custom\.BusinessException;', 'import com.njydsz.pmis.common.exception.BizException;'

    # 7. Replace remaining BusinessException references (catch clauses, etc.)
    $content = $content -replace '\bBusinessException\b', 'BizException'

    # 8. Fix TracerUtils -> TraceIdUtil
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.id\.TracerUtils;', 'import com.njydsz.pmis.common.util.TraceIdUtil;'
    $content = $content -replace '\bTracerUtils\.generateTraceId\(\)', 'TraceIdUtil.generate()'
    $content = $content -replace '\bTracerUtils\b', 'TraceIdUtil'

    # 9. Fix JsonUtils import
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.json\.JsonUtils;', 'import com.njydsz.pmis.common.util.JsonUtils;'

    # 10. Fix util sub-package imports
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.concurrent\.ExecutorUtils;', 'import com.njydsz.pmis.common.util.ExecutorUtils;'
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.string\.StringUtils;', 'import com.njydsz.pmis.common.util.StringUtils;'
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.file\.', 'import com.njydsz.pmis.common.util.'

    # 11. Fix redis service import path (already handled by base replacement)
    # com.njydsz.pmis.common.redis.service.RedisService is correct

    # 12. Fix AOP pointcut expression (package name changed)
    $content = $content -replace 'com\.remisoft\.comm\.queue', 'com.njydsz.pmis.common.queue'

    # 13. Clean up double blank lines
    while ($content -match "(\r\n|\n)\s*\r\n\s*\r\n") {
        $content = $content -replace "(\r\n|\n)\s*\r\n\s*\r\n", "`r`n`r`n"
    }

    # Write as UTF-8 without BOM
    [System.IO.File]::WriteAllText($targetFile, $content, $utf8NoBom)

    $fileCount++
}

# Copy resources
$sourceRes = Join-Path $sourceBase 'resources'
if (Test-Path $sourceRes) {
    Get-ChildItem -Path $sourceRes -Recurse | Where-Object { -not $_.PSIsContainer } | ForEach-Object {
        $relPath = $_.FullName.Substring($sourceRes.Length)
        $targetFile = Join-Path $targetRes $relPath
        $targetDir2 = Split-Path $targetFile -Parent
        if (-not (Test-Path $targetDir2)) {
            New-Item -ItemType Directory -Force -Path $targetDir2 | Out-Null
        }
        Copy-Item -Path $_.FullName -Destination $targetFile -Force
    }
}

Write-Output "Converted $fileCount files from remi-comm-queue to ydsz-pmis-common-queue"
