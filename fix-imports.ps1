$files = Get-Content "D:\Code\open\ydsz-cloud\crlf-files.txt" -Encoding utf8
$fixed = 0
foreach ($filePath in $files) {
    if (-not (Test-Path $filePath)) { continue }
    $text = [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8)
    $lines = $text -split "`n"
    $firstImportLine = -1
    $lastImportLine = -1
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match '^import\s+(static\s+)?') {
            if ($firstImportLine -eq -1) { $firstImportLine = $i }
            $lastImportLine = $i
        }
    }
    if ($firstImportLine -eq -1) { continue }
    $importSection = @()
    for ($i = $firstImportLine; $i -le $lastImportLine; $i++) {
        $importSection += $lines[$i]
    }
    $javaImports = @()
    $thirdParty = @()
    $njydsz = @()
    $staticImports = @()
    foreach ($line in $importSection) {
        if ($line -match '^import\s+static\s+') { $staticImports += $line }
        elseif ($line -match '^import\s+(java|javax)\.') { $javaImports += $line }
        elseif ($line -match '^import\s+com\.njydsz\.') { $njydsz += $line }
        elseif ($line -match '^import\s+') { $thirdParty += $line }
    }
    $javaImports = $javaImports | Sort-Object
    $thirdParty = $thirdParty | Sort-Object
    $njydsz = $njydsz | Sort-Object
    $staticImports = $staticImports | Sort-Object
    $newImportLines = @()
    if ($javaImports.Count -gt 0) { $newImportLines += $javaImports }
    if ($thirdParty.Count -gt 0) {
        if ($newImportLines.Count -gt 0) { $newImportLines += '' }
        $newImportLines += $thirdParty
    }
    if ($njydsz.Count -gt 0) {
        if ($newImportLines.Count -gt 0) { $newImportLines += '' }
        $newImportLines += $njydsz
    }
    if ($staticImports.Count -gt 0) {
        if ($newImportLines.Count -gt 0) { $newImportLines += '' }
        $newImportLines += $staticImports
    }
    $originalImportSection = ($importSection -join "`n")
    $newImportSection = ($newImportLines -join "`n")
    if ($originalImportSection -ne $newImportSection) {
        $newLines = @()
        for ($i = 0; $i -lt $firstImportLine; $i++) { $newLines += $lines[$i] }
        $newLines += $newImportLines
        for ($i = $lastImportLine + 1; $i -lt $lines.Length; $i++) { $newLines += $lines[$i] }
        $newContent = ($newLines -join "`n")
        [System.IO.File]::WriteAllText($filePath, $newContent, [System.Text.Encoding]::UTF8)
        $fixed++
    }
}
Write-Output "Reordered imports in $fixed files"
