$rootPath = "D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\src\main\java"
$files = Get-ChildItem -Path $rootPath -Recurse -Filter "*.java"
$count = 0
foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $newBytes = $bytes[3..($bytes.Length - 1)]
        [System.IO.File]::WriteAllBytes($file.FullName, $newBytes)
        Write-Output "Fixed: $($file.FullName)"
        $count++
    }
}
Write-Output "Total fixed: $count files"
