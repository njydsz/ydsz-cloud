# Remove UTF-8 BOM from all Java files
$cacheBase = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-cache\src\main\java'
$files = Get-ChildItem -Path $cacheBase -Recurse -Filter '*.java'
$count = 0

foreach ($f in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $newBytes = $bytes[3..($bytes.Length - 1)]
        [System.IO.File]::WriteAllBytes($f.FullName, $newBytes)
        $count++
        Write-Host "Removed BOM: $($f.Name)"
    }
}

Write-Host "`nTotal: $count files BOM removed"
