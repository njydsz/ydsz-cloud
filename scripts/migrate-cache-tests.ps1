# Migrate test files from remi-cache-dev to ydsz-pmis-common-cache
$srcBase = 'D:\Code\remi\org\platform\remi-cache-dev'
$dstBase = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-cache\src\test\java\com\njydsz\pmis\common\cache'

New-Item -ItemType Directory -Path $dstBase -Force | Out-Null

$modules = @('remi-cache-core', 'remi-cache-export', 'remi-cache-spring')

$copiedCount = 0
foreach ($mod in $modules) {
    $srcModBase = Join-Path $srcBase "$mod\src\test\java\com\remisoft\cache"
    if (Test-Path $srcModBase) {
        Get-ChildItem -Path $srcModBase -Recurse -Filter '*.java' | ForEach-Object {
            $relPath = $_.FullName.Substring($srcModBase.Length)
            $dstFile = Join-Path $dstBase $relPath
            $dstDir = Split-Path $dstFile -Parent
            New-Item -ItemType Directory -Path $dstDir -Force | Out-Null
            $content = Get-Content $_.FullName -Raw -Encoding UTF8
            # Apply package name replacements
            $content = $content.Replace('com.remisoft.cache', 'com.njydsz.pmis.common.cache')
            $content = $content.Replace('RemiCache', 'YdszCache')
            $content = $content.Replace('SpringRemiCache', 'SpringYdszCache')
            $content = $content.Replace('remisoft', 'ydsz')
            # Remove BOM
            $content = $content -replace '^\xEF\xBB\xBF', ''
            [System.IO.File]::WriteAllText($dstFile, $content, [System.Text.UTF8Encoding]::new($false))
            $copiedCount++
            Write-Host "Migrated test: $relPath"
        }
    }
}

Write-Host "`nTotal test files migrated: $copiedCount"
