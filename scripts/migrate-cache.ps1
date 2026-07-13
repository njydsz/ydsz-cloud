# Cache module migration script
# Copies all Java source files from remi-cache-dev to ydsz-pmis-common-cache
# and applies package/class name replacements

$srcBase = 'D:\Code\remi\org\platform\remi-cache-dev'
$dstBase = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-cache\src\main\java\com\njydsz\pmis\common\cache'

# Create target base directory
New-Item -ItemType Directory -Path $dstBase -Force | Out-Null

# Source modules to migrate (excluding benchmarks, bom, starter, tests)
$modules = @('remi-cache-api', 'remi-cache-core', 'remi-cache-spring', 'remi-cache-metrics-micrometer', 'remi-cache-export')

$copiedCount = 0

# Step 1: Copy all Java files
foreach ($mod in $modules) {
    $srcModBase = Join-Path $srcBase "$mod\src\main\java\com\remisoft\cache"
    if (Test-Path $srcModBase) {
        Get-ChildItem -Path $srcModBase -Recurse -Filter '*.java' | ForEach-Object {
            $relPath = $_.FullName.Substring($srcModBase.Length)
            $dstFile = Join-Path $dstBase $relPath
            $dstDir = Split-Path $dstFile -Parent
            New-Item -ItemType Directory -Path $dstDir -Force | Out-Null
            Copy-Item $_.FullName $dstFile -Force
            $copiedCount++
            Write-Host "Copied: $relPath"
        }
    }
}

Write-Host "`nTotal files copied: $copiedCount`n"

# Step 2: Apply text replacements to all Java files
$javaFiles = Get-ChildItem -Path $dstBase -Recurse -Filter '*.java'

# Define replacements in order (most specific first to avoid partial matches)
$replacements = @(
    # Package name replacement
    @{ Old = 'com.remisoft.cache'; New = 'com.njydsz.pmis.common.cache' },
    # Class name replacements (most specific first)
    @{ Old = 'SpringRemiCacheManager'; New = 'YdszCacheManager' },
    @{ Old = 'SpringRemiCache'; New = 'SpringYdszCache' },
    @{ Old = 'RemiCacheAutoConfiguration'; New = 'YdszCacheAutoConfiguration' },
    @{ Old = 'RemiCacheProperties'; New = 'YdszCacheProperties' },
    @{ Old = 'RemiCache'; New = 'YdszCache' },
    # Configuration prefix replacement
    @{ Old = 'remisoft.cache'; New = 'ydsz.cache' },
    @{ Old = 'remisoft'; New = 'ydsz' },
    # Thread name replacement
    @{ Old = 'remi-cache'; New = 'ydsz-cache' },
    # Remaining FQN reference fix (com.remisoft.cache.api.Cache -> Cache after import)
    @{ Old = 'com.njydsz.pmis.common.cache.api.Cache'; New = 'Cache' }
)

$replacedCount = 0
foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $modified = $false
    foreach ($rep in $replacements) {
        if ($content -cmatch [regex]::Escape($rep.Old)) {
            $content = $content.Replace($rep.Old, $rep.New)
            $modified = $true
        }
    }
    if ($modified) {
        Set-Content $file.FullName -Value $content -Encoding UTF8 -NoNewline
        $replacedCount++
    }
}

Write-Host "Text replacements applied to $replacedCount files"

# Step 3: Rename files that have Remi in their name
$renameMap = @{
    'RemiCache.java' = 'YdszCache.java'
    'RemiCacheAutoConfiguration.java' = 'YdszCacheAutoConfiguration.java'
    'RemiCacheProperties.java' = 'YdszCacheProperties.java'
    'SpringRemiCache.java' = 'SpringYdszCache.java'
    'SpringRemiCacheManager.java' = 'YdszCacheManager.java'
}

$renamedCount = 0
Get-ChildItem -Path $dstBase -Recurse -Filter '*.java' | ForEach-Object {
    if ($renameMap.ContainsKey($_.Name)) {
        $newName = $renameMap[$_.Name]
        $newPath = Join-Path $_.DirectoryName $newName
        Rename-Item $_.FullName $newName
        $renamedCount++
        Write-Host "Renamed: $($_.Name) -> $newName"
    }
}

Write-Host "`nTotal files renamed: $renamedCount"
Write-Host "`nMigration complete!"
