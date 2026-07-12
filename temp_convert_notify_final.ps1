# Final conversion script for remi-comm-notify to ydsz-pmis-common-notify
# Uses .NET APIs to avoid encoding issues with Get-Content/Set-Content

$sourceBase = 'D:\Code\remi\vip\platform\remi-comm\remi-comm-notify\src\main'
$targetBase = 'D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-notify\src\main'
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# Target java root
$targetJavaRoot = Join-Path $targetBase 'java\com\njydsz\pmis\common\notify'

# Clean target java directory completely
if (Test-Path $targetJavaRoot) {
    Remove-Item -Recurse -Force $targetJavaRoot
}
New-Item -ItemType Directory -Force -Path $targetJavaRoot | Out-Null

# Also clean and re-copy resources
$targetRes = Join-Path $targetBase 'resources'
if (Test-Path $targetRes) {
    Remove-Item -Recurse -Force $targetRes
}

$fileCount = 0

# Source-to-target mappings
$mappings = @(
    @{ SourceDir = Join-Path $sourceBase 'java\com\remisoft\comm\notify'; TargetDir = $targetJavaRoot },
    @{ SourceDir = Join-Path $sourceBase 'java\com\remisoft\comm\email'; TargetDir = Join-Path $targetJavaRoot 'email' }
)

foreach ($mapping in $mappings) {
    $srcDir = $mapping.SourceDir
    $tgtDir = $mapping.TargetDir
    
    if (-not (Test-Path $srcDir)) {
        Write-Warning "Source directory not found: $srcDir"
        continue
    }
    
    $sourceFiles = Get-ChildItem -Path $srcDir -Recurse -Filter '*.java'
    
    foreach ($srcFile in $sourceFiles) {
        # Calculate relative path from source dir
        $relPath = $srcFile.FullName.Substring($srcDir.Length)
        
        # Determine target path
        $targetFile = Join-Path $tgtDir $relPath
        
        # Create directory if needed
        $targetDir2 = Split-Path $targetFile -Parent
        if (-not (Test-Path $targetDir2)) {
            New-Item -ItemType Directory -Force -Path $targetDir2 | Out-Null
        }
        
        # Read file content as UTF-8
        $content = [System.IO.File]::ReadAllText($srcFile.FullName, [System.Text.Encoding]::UTF8)
        
        # Replace package names
        $content = $content -replace 'com\.remisoft\.comm', 'com.njydsz.pmis.common'
        
        # Replace RemiNotify annotation with EnableNotify
        $content = $content -replace 'EnableRemiNotify', 'EnableNotify'
        
        # Replace remi-comm references in comments
        $content = $content -replace 'remi-comm-notify', 'pmis-common-notify'
        $content = $content -replace 'remi-comm-email', 'pmis-common-notify'
        $content = $content -replace 'remi-comm', 'pmis-common'
        
        # Replace remi/瑞米 references
        $content = $content -replace '瑞米', 'PMIS'
        $content = $content -replace 'RemiNotify', 'Notify'
        
        # Replace author info
        $content = $content -replace '@author Marvin Lee', '@author ydsz-pmis-team'
        $content = $content -replace '@email limw1888@126\.com', ''
        $content = $content -replace '@version 3\.5\.0', ''
        
        # Clean up any double blank lines from removed lines
        while ($content -match "(\r\n|\n)\s*\r\n\s*\r\n") {
            $content = $content -replace "(\r\n|\n)\s*\r\n\s*\r\n", "`r`n`r`n"
        }
        
        # Write as UTF-8 without BOM
        [System.IO.File]::WriteAllText($targetFile, $content, $utf8NoBom)
        
        $fileCount++
        Write-Output "Converted: $(Split-Path $srcFile -Leaf)"
    }
}

# Copy resources
$sourceRes = Join-Path $sourceBase 'resources'
if (Test-Path $sourceRes) {
    Copy-Item -Path $sourceRes -Destination $targetBase -Recurse -Force
    Write-Output "Copied resources"
}

Write-Output "`nTotal files converted: $fileCount"

# Fix all util package imports
$javaDir = Join-Path $targetBase 'java'
$javaFiles = Get-ChildItem -Path $javaDir -Recurse -Filter '*.java'

foreach ($file in $javaFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.[a-z]+\.JsonUtils;', 'import com.njydsz.pmis.common.util.JsonUtils;'
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.concurrent\.ExecutorUtils;', 'import com.njydsz.pmis.common.util.ExecutorUtils;'
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.string\.StringUtils;', 'import com.njydsz.pmis.common.util.StringUtils;'
    [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
}

Write-Output "Fixed util package imports in $($javaFiles.Count) files"

# Rename EnableRemiNotify to EnableNotify
$enableNotifyFile = Join-Path $targetJavaRoot 'annotation\EnableRemiNotify.java'
if (Test-Path $enableNotifyFile) {
    Remove-Item $enableNotifyFile -Force
    Write-Output "Removed old EnableRemiNotify.java"
}

Write-Output "`nDone!"