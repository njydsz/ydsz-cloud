# Fix broken imports and wildcard imports in migrated cache module
$cacheBase = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-cache\src\main\java\com\njydsz\pmis\common\cache'

# Step 1: Fix broken 'import Cache;' -> 'import com.njydsz.pmis.common.cache.api.Cache;'
$javaFiles = Get-ChildItem -Path $cacheBase -Recurse -Filter '*.java'
$fixedCount = 0

foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $modified = $false

    # Fix broken import Cache;
    if ($content -match '(?m)^import Cache;') {
        $content = $content -replace '(?m)^import Cache;', 'import com.njydsz.pmis.common.cache.api.Cache;'
        $modified = $true
    }

    if ($modified) {
        # Remove BOM if present
        $content = $content -replace '^\xEF\xBB\xBF', ''
        Set-Content $file.FullName -Value $content -Encoding UTF8 -NoNewline
        $fixedCount++
        Write-Host "Fixed import in: $($file.Name)"
    }
}

Write-Host "`nFixed $fixedCount files with broken imports"

# Step 2: Fix wildcard imports
# Map of file -> wildcard import -> specific imports
$wildcardFixes = @{
    'WindowTinyLFUCache.java' = @{
        Path = 'internal\tinylfu\WindowTinyLFUCache.java'
        Old = "import java.util.*;"
        New = @"
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
"@
    }
    'WTinyLFUCache.java' = @{
        Path = 'internal\tinylfu\WTinyLFUCache.java'
        Old = "import java.util.*;"
        New = @"
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
"@
    }
    'ConcurrentCache.java' = @{
        Path = 'internal\concurrent\ConcurrentCache.java'
        Old = "import java.util.*;"
        New = @"
import java.util.Collection;
import java.util.Set;
"@
    }
    'LRUCache.java' = @{
        Path = 'internal\lru\LRUCache.java'
        Old = "import java.util.*;"
        New = @"
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
"@
    }
    'CacheExportImport.java' = @{
        Path = 'export\CacheExportImport.java'
        Old = "import java.io.*;"
        New = @"
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
"@
    }
    'CacheAsMapView.java' = @{
        Path = 'api\CacheAsMapView.java'
        Old = "import java.util.*;"
        New = @"
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
"@
    }
    'CacheWarmer.java' = @{
        Path = 'support\CacheWarmer.java'
        Old = "import java.util.concurrent.*;"
        New = @"
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
"@
    }
}

$wildcardFixed = 0
foreach ($key in $wildcardFixes.Keys) {
    $fix = $wildcardFixes[$key]
    $filePath = Join-Path $cacheBase $fix.Path
    if (Test-Path $filePath) {
        $content = Get-Content $filePath -Raw -Encoding UTF8
        # Replace wildcard import (handle both \r\n and \n)
        $content = $content -replace [regex]::Escape($fix.Old), $fix.New.Trim()
        Set-Content $filePath -Value $content -Encoding UTF8 -NoNewline
        $wildcardFixed++
        Write-Host "Fixed wildcard import in: $key"
    }
}

Write-Host "`nFixed $wildcardFixed files with wildcard imports"

# Step 3: Remove duplicate imports that might have been introduced
foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $lines = $content -split "`r?`n"
    $seenImports = @{}
    $newLines = @()
    $inImports = $true

    foreach ($line in $lines) {
        if ($inImports -and $line -match '^import\s+(.+);') {
            $importStmt = $line.Trim()
            if ($seenImports.ContainsKey($importStmt)) {
                # Skip duplicate import
                continue
            }
            $seenImports[$importStmt] = $true
        } elseif ($inImports -and $line -ne '' -and -not $line.StartsWith('import') -and -not $line.StartsWith('package') -and -not $line.StartsWith('//') -and -not $line.StartsWith('/*') -and -not $line.StartsWith('*')) {
            $inImports = $false
        }
        $newLines += $line
    }

    $newContent = $newLines -join "`n"
    if ($newContent -ne $content) {
        Set-Content $file.FullName -Value $newContent -Encoding UTF8 -NoNewline
    }
}

Write-Host "`nAll import fixes complete!"
