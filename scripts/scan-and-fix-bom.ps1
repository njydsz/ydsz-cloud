# Scan all Java files in the project for UTF-8 BOM
$rootPath = "d:/Code/ydsz/ydsz/ydsz-backend"
$javaFiles = Get-ChildItem -Path $rootPath -Recurse -Filter "*.java" -ErrorAction SilentlyContinue
$bomFiles = @()

foreach ($file in $javaFiles) {
    try {
        $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
        if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
            $bomFiles += $file.FullName
            Write-Host "BOM found: $($file.FullName)"
        }
    } catch {
        # Skip files that can't be read
    }
}

Write-Host ""
Write-Host "Total Java files scanned: $($javaFiles.Count)"
Write-Host "Files with BOM: $($bomFiles.Count)"

if ($bomFiles.Count -gt 0) {
    Write-Host ""
    Write-Host "Removing BOM from all affected files..."
    foreach ($file in $bomFiles) {
        $bytes = [System.IO.File]::ReadAllBytes($file)
        $newBytes = $bytes[3..($bytes.Length - 1)]
        [System.IO.File]::WriteAllBytes($file, $newBytes)
        Write-Host "  Fixed: $file"
    }
    Write-Host "All BOM characters removed."
}
