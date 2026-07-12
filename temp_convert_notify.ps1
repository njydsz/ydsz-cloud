# Convert remi-comm-notify to ydsz-pmis-common-notify
$sourceBase = 'D:\Code\remi\vip\platform\remi-comm\remi-comm-notify\src\main'
$targetBase = 'D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-notify\src\main'

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

# Define source-to-target mappings
# Source: com/remisoft/comm/notify -> Target: com/njydsz/pmis/common/notify
# Source: com/remisoft/comm/email   -> Target: com/njydsz/pmis/common/notify/email
$mappings = @(
    @{
        SourceDir = Join-Path $sourceBase 'java\com\remisoft\comm\notify'
        TargetDir = $targetJavaRoot
    },
    @{
        SourceDir = Join-Path $sourceBase 'java\com\remisoft\comm\email'
        TargetDir = Join-Path $targetJavaRoot 'email'
    }
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
        
        # Replace author info
        $content = $content -replace '@author Marvin Lee', '@author ydsz-pmis-team'
        $content = $content -replace '@email limw1888@126\.com', ''
        $content = $content -replace '@version 3\.5\.0', ''
        
        # Clean up any double blank lines from removed lines
        while ($content -match "(\r\n|\n)\s*\r\n\s*\r\n") {
            $content = $content -replace "(\r\n|\n)\s*\r\n\s*\r\n", "`r`n`r`n"
        }
        
        # Write as UTF-8 without BOM
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($targetFile, $content, $utf8NoBom)
        
        $fileCount++
        Write-Output "Converted: $(Split-Path $srcFile -Leaf) -> $($targetFile.Substring($targetBase.Length))"
    }
}

# Copy resources
$sourceRes = Join-Path $sourceBase 'resources'
if (Test-Path $sourceRes) {
    Copy-Item -Path $sourceRes -Destination $targetBase -Recurse -Force
    Write-Output "Copied resources"
}

Write-Output "`nTotal files converted: $fileCount"
