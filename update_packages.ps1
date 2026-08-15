# Package declarations to update for files that were moved
$packageMappings = @{
    "package com.njydsz.literule.api.expr;" = "package com.njydsz.literule.api.expression;"
    "package com.njydsz.literule.server.expr.liteexpr;" = "package com.njydsz.literule.server.engine.liteexpr;"
    "package com.njydsz.literule.server.expr;" = "package com.njydsz.literule.server.expression;"
}

$git = "C:\Program Files\Git\bin\git.exe"
$allFiles = & $git ls-files "ydsz-literule/" | Where-Object { $_ -like "*.java" }
$repoRoot = "D:\Code\open\ydsz-cloud"
$updatedCount = 0

foreach ($file in $allFiles) {
    $fullPath = Join-Path $repoRoot $file
    if (-not (Test-Path $fullPath)) { continue }
    
    $original = [System.IO.File]::ReadAllText($fullPath, [System.Text.Encoding]::UTF8)
    $content = $original
    $changed = $false
    
    foreach ($old in $packageMappings.Keys) {
        $new = $packageMappings[$old]
        if ($content.Contains($old)) {
            $content = $content.Replace($old, $new)
            $changed = $true
        }
    }
    
    if ($changed) {
        [System.IO.File]::WriteAllText($fullPath, $content, [System.Text.Encoding]::UTF8)
        $updatedCount++
        Write-Host "Updated: $file"
    }
}

Write-Host "Updated $updatedCount files"
