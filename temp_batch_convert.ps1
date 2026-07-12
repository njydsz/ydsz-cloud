# Batch convert multiple modules
# Each entry: @{ SourceModule="..."; TargetModule="..."; SourcePackage="..."; TargetPackage="..." }
$modules = @(
    @{ SourceModule="remi-comm-queue";  TargetModule="ydsz-pmis-common-queue";  SourcePackage="mq";    TargetPackage="mq" },
    @{ SourceModule="remi-comm-jdbc";   TargetModule="ydsz-pmis-common-jdbc";   SourcePackage="jdbc";  TargetPackage="jdbc" },
    @{ SourceModule="remi-comm-audit";  TargetModule="ydsz-pmis-common-audit";  SourcePackage="audit"; TargetPackage="audit" },
    @{ SourceModule="remi-comm-lock";   TargetModule="ydsz-pmis-common-lock";   SourcePackage="lock";  TargetPackage="lock" }
)

$utf8NoBom = New-Object System.Text.UTF8Encoding $false

foreach ($mod in $modules) {
    $sourceBase = "D:\Code\remi\vip\platform\remi-comm\$($mod.SourceModule)\src\main"
    $targetBase = "D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\$($mod.TargetModule)\src\main"
    $sourceJavaRoot = Join-Path $sourceBase "java\com\remisoft\comm\$($mod.SourcePackage)"
    $targetJavaRoot = Join-Path $targetBase "java\com\njydsz\pmis\common\$($mod.TargetPackage)"
    
    Write-Output "`n=== Converting $($mod.SourceModule) -> $($mod.TargetModule) ==="
    Write-Output "Source: $sourceJavaRoot"
    Write-Output "Target: $targetJavaRoot"
    
    if (-not (Test-Path $sourceJavaRoot)) {
        # Maybe the package is different - try listing what's under com/remisoft/comm
        $commRoot = Join-Path $sourceBase "java\com\remisoft\comm"
        Write-Output "Source root not found. Available packages:"
        Get-ChildItem -Path $commRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object { Write-Output "  $($_.Name)" }
        continue
    }
    
    # Clean target java directory
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
    
    # Get all Java files recursively
    $sourceFiles = Get-ChildItem -Path $sourceJavaRoot -Recurse -Filter '*.java' -ErrorAction SilentlyContinue
    
    foreach ($srcFile in $sourceFiles) {
        $relPath = $srcFile.FullName.Substring($sourceJavaRoot.Length)
        $targetFile = Join-Path $targetJavaRoot $relPath
        
        $targetDir2 = Split-Path $targetFile -Parent
        if (-not (Test-Path $targetDir2)) {
            New-Item -ItemType Directory -Force -Path $targetDir2 | Out-Null
        }
        
        $content = [System.IO.File]::ReadAllText($srcFile.FullName, [System.Text.Encoding]::UTF8)
        
        # Standard replacements
        $content = $content -replace 'com\.remisoft\.comm', 'com.njydsz.pmis.common'
        $content = $content -replace 'EnableRemi(\w+)', 'Enable$1'
        $content = $content -replace 'remi-comm-\w+', 'pmis-common'
        $content = $content -replace 'remi-comm', 'pmis-common'
        $content = $content -replace '@author Marvin Lee', '@author ydsz-pmis-team'
        $content = $content -replace '@email limw1888@126\.com', ''
        $content = $content -replace '@version 3\.5\.0', ''
        
        # Fix util sub-package imports
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.json\.JsonUtils;', 'import com.njydsz.pmis.common.util.JsonUtils;'
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.concurrent\.ExecutorUtils;', 'import com.njydsz.pmis.common.util.ExecutorUtils;'
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.string\.StringUtils;', 'import com.njydsz.pmis.common.util.StringUtils;'
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.io\.IOUtils;', 'import com.njydsz.pmis.common.util.IOUtils;'
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.file\.\w+;', 'import com.njydsz.pmis.common.util.FileUtils;'
        
        # Fix exception imports
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.exception\.enums\.ExceptionCode;', 'import com.njydsz.pmis.common.exception.code.ExceptionCode;'
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.exception\.custom\.BusinessException;', 'import com.njydsz.pmis.common.exception.BizException;'
        $content = $content -replace 'BusinessException', 'BizException'
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.exception\.custom\.BizException;', 'import com.njydsz.pmis.common.exception.BizException;'
        
        # Fix redis imports
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.redis\.service\.ops\.\w+;', 'import org.springframework.data.redis.core.StringRedisTemplate;'
        
        # Fix JsonUtils.fromJson -> JsonUtils.parseObject
        $content = $content -replace 'JsonUtils\.fromJson\(', 'JsonUtils.parseObject('
        
        # Remove health indicator imports
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.\w+\.health\.\w+HealthIndicator;\r?\n', ''
        
        # Clean up double blank lines
        while ($content -match "(\r\n|\n)\s*\r\n\s*\r\n") {
            $content = $content -replace "(\r\n|\n)\s*\r\n\s*\r\n", "`r`n`r`n"
        }
        
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
    
    # Remove HealthIndicator files
    $healthFiles = Get-ChildItem -Path $targetJavaRoot -Recurse -Filter '*HealthIndicator*.java' -ErrorAction SilentlyContinue
    foreach ($hf in $healthFiles) {
        Remove-Item $hf.FullName -Force
        Write-Output "  Removed: $($hf.Name)"
    }
    
    # Rename EnableRemi*.java to Enable*.java
    $enableRemiFiles = Get-ChildItem -Path $targetJavaRoot -Recurse -Filter 'EnableRemi*.java' -ErrorAction SilentlyContinue
    foreach ($ef in $enableRemiFiles) {
        $newName = $ef.Name -replace 'EnableRemi', 'Enable'
        $newPath = Join-Path $ef.Directory.FullName $newName
        $content = [System.IO.File]::ReadAllText($ef.FullName, [System.Text.Encoding]::UTF8)
        [System.IO.File]::WriteAllText($newPath, $content, $utf8NoBom)
        Remove-Item $ef.FullName -Force
        Write-Output "  Renamed: $($ef.Name) -> $newName"
    }
    
    Write-Output "  Converted $fileCount files"
}

Write-Output "`nBatch conversion complete!"
