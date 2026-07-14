# Fix BOM: Remove UTF-8 BOM (EF BB BF) from Java source files
$files = @(
    "d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-util/src/main/java/com/njydsz/pmis/common/util/CursorHelper.java",
    "d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-util/src/main/java/com/njydsz/pmis/common/util/json/YamlUtils.java"
)

foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file)
    $bom = "{0:X2} {1:X2} {2:X2}" -f $bytes[0], $bytes[1], $bytes[2]
    Write-Host "File: $file"
    Write-Host "  First 3 bytes: $bom"
    
    if ($bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        Write-Host "  BOM detected! Removing BOM..."
        $newBytes = $bytes[3..($bytes.Length - 1)]
        [System.IO.File]::WriteAllBytes($file, $newBytes)
        Write-Host "  BOM removed successfully."
    } else {
        Write-Host "  No BOM found."
    }
}
