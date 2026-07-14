# 安全地移除 Java 文件 UTF-8 BOM（基于 TextEncoding）
$target = 'ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-util/src/main/java/com/njydsz/pmis/common/util/json'
Get-ChildItem -Path $target -Recurse -Filter '*.java' | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $newBytes = New-Object byte[] ($bytes.Length - 3)
        [Array]::Copy($bytes, 3, $newBytes, 0, $newBytes.Length)
        [System.IO.File]::WriteAllBytes($_.FullName, $newBytes)
        Write-Output ("BOM removed: " + $_.FullName)
    }
}
Write-Output "Done"
