# Generic module conversion script
# Usage: powershell -File temp_convert_module.ps1 -SourceModule <module> -TargetModule <module> -TargetPkg <pkg>
param(
    [Parameter(Mandatory=$true)]
    [string]$SourceModule,
    
    [Parameter(Mandatory=$true)]
    [string]$TargetModule,
    
    [Parameter(Mandatory=$true)]
    [string]$TargetPackage
)

$sourceBase = "D:\Code\remi\vip\platform\remi-comm\$SourceModule\src\main"
$targetBase = "D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\$TargetModule\src\main"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# Source java root: com/remisoft/comm/*
$sourceJavaRoot = Join-Path $sourceBase 'java\com\remisoft\comm'

# Target java root: com/njydsz/pmis/common/<TargetPackage>
$targetJavaRoot = Join-Path $targetBase "java\com\njydsz\pmis\common\$TargetPackage"

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

# Get all subdirectories under source java root
$sourceSubDirs = Get-ChildItem -Path $sourceJavaRoot -Directory -ErrorAction SilentlyContinue

foreach ($subDir in $sourceSubDirs) {
    $subDirName = $subDir.Name
    $srcDir = $subDir.FullName
    $tgtDir = Join-Path $targetJavaRoot $subDirName
    
    # Get all Java files in this subdirectory recursively
    $sourceFiles = Get-ChildItem -Path $srcDir -Recurse -Filter '*.java' -ErrorAction SilentlyContinue
    
    foreach ($srcFile in $sourceFiles) {
        # Calculate relative path from source subdirectory
        $relPath = $srcFile.FullName.Substring($srcDir.Length)
        
        # Determine target path
        $targetFile = Join-Path $tgtDir $relPath
        
        # Create directory if needed
        $targetDir2 = Split-Path $targetFile -Parent
        if (-not (Test-Path $targetDir2)) {
            New-Item -ItemType Directory -Force -Path $targetDir2 | Out-Null
        }
        
        # Read and transform file content
        $content = [System.IO.File]::ReadAllText($srcFile.FullName, [System.Text.Encoding]::UTF8)
        
        # Replace package names
        $content = $content -replace 'com\.remisoft\.comm', 'com.njydsz.pmis.common'
        
        # Replace EnableRemi* with Enable*
        $content = $content -replace 'EnableRemi(\w+)', 'Enable$1'
        
        # Replace remi references
        $content = $content -replace 'remi-comm-\w+', 'pmis-common'
        $content = $content -replace 'remi-comm', 'pmis-common'
        $content = $content -replace 'remisoft', 'njydsz'
        
        # Replace author info
        $content = $content -replace '@author Marvin Lee', '@author ydsz-pmis-team'
        $content = $content -replace '@email limw1888@126\.com', ''
        $content = $content -replace '@version 3\.5\.0', ''
        
        # Fix util sub-package imports
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.json\.JsonUtils;', 'import com.njydsz.pmis.common.util.JsonUtils;'
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.concurrent\.ExecutorUtils;', 'import com.njydsz.pmis.common.util.ExecutorUtils;'
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.string\.StringUtils;', 'import com.njydsz.pmis.common.util.StringUtils;'
        
        # Clean up double blank lines
        while ($content -match "(\r\n|\n)\s*\r\n\s*\r\n") {
            $content = $content -replace "(\r\n|\n)\s*\r\n\s*\r\n", "`r`n`r`n"
        }
        
        # Write as UTF-8 without BOM
        [System.IO.File]::WriteAllText($targetFile, $content, $utf8NoBom)
        
        $fileCount++
    }
}

# Copy resources
$sourceRes = Join-Path $sourceBase 'resources'
if (Test-Path $sourceRes) {
    # Merge into target resources
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

# Remove HealthIndicator files (actuator optional dependency issue)
$healthFiles = Get-ChildItem -Path $targetJavaRoot -Recurse -Filter '*HealthIndicator*.java' -ErrorAction SilentlyContinue
foreach ($hf in $healthFiles) {
    Remove-Item $hf.FullName -Force
    Write-Output "  Removed: $($hf.Name) (actuator dependency)"
}

# Rename EnableRemi*.java files to Enable*.java
$enableRemiFiles = Get-ChildItem -Path $targetJavaRoot -Recurse -Filter 'EnableRemi*.java' -ErrorAction SilentlyContinue
foreach ($ef in $enableRemiFiles) {
    $newName = $ef.Name -replace 'EnableRemi', 'Enable'
    $newPath = Join-Path $ef.Directory.FullName $newName
    Remove-Item $ef.FullName -Force
    # The file content was already renamed internally, just need to not have the old file
}

Write-Output "Converted $fileCount files from $SourceModule to $TargetModule"
