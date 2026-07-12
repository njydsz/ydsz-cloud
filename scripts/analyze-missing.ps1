# Analyze which classes from business modules are missing in the new common module
$ErrorActionPreference = "SilentlyContinue"

# Step 1: Collect all unique imports from business modules
$businessDir = "d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend"
$imports = @()
Get-ChildItem -Path $businessDir -Recurse -Filter "*.java" | Where-Object {
    $_.FullName -notlike "*ydsz-pmis-common*" -and $_.FullName -notlike "*target*"
} | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $matches = [regex]::Matches($content, "import\s+(com\.njydsz\.pmis\.common\.[a-zA-Z0-9_.]+);")
    foreach ($m in $matches) {
        $imports += $m.Groups[1].Value
    }
}
$uniqueImports = $imports | Sort-Object -Unique

# Step 2: Build class name to package mapping from new common module
$commonDir = "d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common"
$newClassMap = @{}
Get-ChildItem -Path $commonDir -Recurse -Filter "*.java" | Where-Object {
    $_.FullName -notlike "*target*" -and $_.FullName -notlike "*test*"
} | ForEach-Object {
    $className = $_.BaseName
    $pkg = $_.DirectoryName -replace ".*\\src\\main\\java\\", "" -replace "\\", "."
    if (-not $newClassMap.ContainsKey($className)) {
        $newClassMap[$className] = @()
    }
    $newClassMap[$className] += $pkg
}

# Step 3: Check each import
$missing = @()
$found = @()
foreach ($imp in $uniqueImports) {
    # Extract class name (last part, handle inner classes)
    $parts = $imp -split "\."
    $className = $parts[-1]
    # Handle inner class like JobRunRecorder.JobRunResult
    if ($className -eq "JobRunResult") { $className = "JobRunRecorder" }

    $oldPkg = ($parts[0..($parts.Length-2)] -join ".")

    if ($newClassMap.ContainsKey($className)) {
        $newPkgs = $newClassMap[$className] -join ", "
        # Check if the old package matches any new package
        $exactMatch = $false
        foreach ($np in $newClassMap[$className]) {
            if ($np -eq $oldPkg) {
                $exactMatch = $true
                break
            }
        }
        if ($exactMatch) {
            $found += "OK (exact): $imp"
        } else {
            $found += "MOVED: $imp => $newPkgs"
        }
    } else {
        $missing += "MISSING: $imp"
    }
}

# Step 4: Output results
Write-Host "=== MISSING CLASSES (not in new common module) ==="
$missing | ForEach-Object { Write-Host $_ }
Write-Host ""
Write-Host "=== MOVED CLASSES (exist but in different package) ==="
$found | Where-Object { $_ -like "MOVED*" } | ForEach-Object { Write-Host $_ }
Write-Host ""
Write-Host "=== EXACT MATCH (same package) ==="
$found | Where-Object { $_ -like "OK*" } | ForEach-Object { Write-Host $_ }
Write-Host ""
Write-Host "Summary: $($missing.Count) missing, $(($found | Where-Object { $_ -like "MOVED*" }).Count) moved, $(($found | Where-Object { $_ -like "OK*" }).Count) exact"
