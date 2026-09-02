# Fix import ordering for all Java files in literule-web module
# Checkstyle rules: SAME_PACKAGE(0) ### STANDARD_JAVA_PACKAGE
# Sort alphabetically within groups using Java ordinal order

$ErrorActionPreference = "Continue"

$modulePath = "D:\Code\open\ydsz-cloud\ydsz-literule\ydsz-literule-web\src\main\java"
$javaFiles = Get-ChildItem -Path $modulePath -Recurse -Filter "*.java"
$fixedCount = 0

foreach ($file in $javaFiles) {
    [string[]]$lines = Get-Content $file.FullName -Encoding UTF8
    
    # Find package declaration
    $currentPackage = ""
    foreach ($line in $lines) {
        if ($line -match "^\s*package\s+([\w.]+)\s*;") {
            $currentPackage = $Matches[1]
            break
        }
    }
    
    # Find import block (consecutive import lines at top of file)
    $importStart = -1
    $importEnd = -1
    $inImportBlock = $false
    
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $trimmed = $lines[$i].Trim()
        if ($trimmed -match "^import\s+" -or $trimmed -match "^static\s+import\s+") {
            if (-not $inImportBlock) {
                $importStart = $i
                $inImportBlock = $true
            }
            $importEnd = $i
        }
        elseif ($inImportBlock -and $trimmed -ne "" -and $trimmed -notmatch "^\s*//" -and $trimmed -notmatch "^\s*/\*") {
            break
        }
    }
    
    if ($importStart -eq -1) { continue }
    
    # Skip blank lines within import block
    $firstImport = -1
    $lastImport = -1
    for ($i = $importStart; $i -le $importEnd; $i++) {
        if ($lines[$i].Trim() -match "^(import|static\s+import)\s+") {
            if ($firstImport -eq -1) { $firstImport = $i }
            $lastImport = $i
        }
    }
    
    if ($firstImport -eq -1) { continue }
    
    # Categorize imports
    $other = @()      # non-java, non-javax (includes SAME_PACKAGE with depth 0)
    $stdJava = @()    # java.* and javax.*
    
    for ($i = $firstImport; $i -le $lastImport; $i++) {
        $line = $lines[$i]
        if ($line -match "^(\s*)import\s+(static\s+)?([\w.]+)\s*;") {
            $indent = $Matches[1]
            $importPath = $Matches[3]
            
            # SAME_PACKAGE(0) goes with "other" since it's same depth as others
            if ($importPath -eq $currentPackage -or $importPath.StartsWith("$currentPackage.")) {
                $other += $line
            }
            elseif ($importPath.StartsWith("java.") -or $importPath.StartsWith("javax.")) {
                $stdJava += $line
            }
            else {
                $other += $line
            }
        }
    }
    
    # Sort using String.CompareOrdinal (Java-like ordinal comparison)
    $comparer = [System.StringComparer]::Ordinal
    $otherSorted = @($other | Sort-Object { $_.Trim() } -ErrorAction SilentlyContinue)
    $stdJavaSorted = @($stdJava | Sort-Object { $_.Trim() } -ErrorAction SilentlyContinue)
    
    # If no categories found, skip
    if ($otherSorted.Count + $stdJavaSorted.Count -eq 0) { continue }
    
    # Preserve original blank lines within each group - but rebuild
    $newImportLines = @()
    
    if ($otherSorted.Count -gt 0) {
        $newImportLines += $otherSorted
    }
    if ($otherSorted.Count -gt 0 -and $stdJavaSorted.Count -gt 0) {
        $newImportLines += ""
    }
    if ($stdJavaSorted.Count -gt 0) {
        $newImportLines += $stdJavaSorted
    }
    
    # Compare with original
    $originalBlock = @()
    for ($i = $firstImport; $i -le $lastImport; $i++) {
        $originalBlock += $lines[$i]
    }
    
    if ((Compare-Object $newImportLines $originalBlock | Where-Object { $null -ne $_ }).Count -ne 0) {
        # Rebuild file
        $newFileLines = @()
        
        # Lines before import block
        for ($i = 0; $i -lt $firstImport; $i++) { $newFileLines += $lines[$i] }
        
        # New import block
        $newFileLines += $newImportLines
        
        # Lines after import block
        for ($i = $lastImport + 1; $i -lt $lines.Count; $i++) { $newFileLines += $lines[$i] }
        
        $newContent = ($newFileLines -join "`r`n") + "`r`n"
        [System.IO.File]::WriteAllText($file.FullName, $newContent, [System.Text.Encoding]::UTF8)
        $fixedCount++
        Write-Output "Fixed: $($file.Name)"
    }
}

Write-Output "`nTotal fixed: $fixedCount files"
