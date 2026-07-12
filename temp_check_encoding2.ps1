$files = @(
    'd:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-notify/src/main/java/com/njydsz/pmis/common/notify/channel/FeishuNotifySender.java',
    'd:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-notify/src/main/java/com/njydsz/pmis/common/notify/channel/NotifyChannelStrategy.java'
)

foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file)
    Write-Output "=== $(Split-Path $file -Leaf) ==="
    Write-Output "Size: $($bytes.Length) bytes"
    Write-Output "First 10 bytes (hex): $(($bytes[0..9] | ForEach-Object { '{0:X2}' -f $_ }) -join ' ')"
    
    # Check for BOM
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        Write-Output "HAS UTF-8 BOM!"
    } elseif ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFF -and $bytes[1] -eq 0xFE) {
        Write-Output "HAS UTF-16 LE BOM!"
    } elseif ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFE -and $bytes[1] -eq 0xFF) {
        Write-Output "HAS UTF-16 BE BOM!"
    } else {
        Write-Output "No BOM detected"
    }
    
    # Check for any non-ASCII bytes in first 100 bytes
    $nonAscii = @()
    for ($i = 0; $i -lt [Math]::Min(100, $bytes.Length); $i++) {
        if ($bytes[$i] -gt 127) {
            $nonAscii += "$i=$('{0:X2}' -f $bytes[$i])"
        }
    }
    if ($nonAscii.Count -gt 0) {
        Write-Output "Non-ASCII in first 100 bytes: $($nonAscii -join ', ')"
    }
    Write-Output ""
}
