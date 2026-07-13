param([string]$FilePath)
$bytes = [System.IO.File]::ReadAllBytes($FilePath)
$hex = ($bytes[0..5] | ForEach-Object { $_.ToString('X2') }) -join ' '
Write-Output "First 6 bytes: $hex"
Write-Output "File size: $($bytes.Length) bytes"
if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
    Write-Output "HAS BOM: YES"
} else {
    Write-Output "HAS BOM: NO"
}
