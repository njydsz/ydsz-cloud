$file = 'd:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-notify/src/main/java/com/njydsz/pmis/common/notify/channel/EmailNotifySender.java'
$bytes = [System.IO.File]::ReadAllBytes($file)

# Check for double CR (\r\r\n) or other line ending issues
$crCount = ($bytes | Where-Object { $_ -eq 0x0D }).Count
$lfCount = ($bytes | Where-Object { $_ -eq 0x0A }).Count
Write-Output "CR count: $crCount, LF count: $lfCount"

# Show bytes around line 3-4 boundary (looking for \r\n patterns)
# Find the first \n position
$positions = @()
for ($i = 0; $i -lt $bytes.Length; $i++) {
    if ($bytes[$i] -eq 0x0A) { $positions += $i }
}
Write-Output "First 5 newline positions: $($positions[0..4] -join ', ')"

# Show bytes around first few newlines
foreach ($pos in $positions[0..4]) {
    $start = [Math]::Max(0, $pos - 3)
    $end = [Math]::Min($bytes.Length - 1, $pos + 3)
    $hex = (($bytes[$start..$end]) | ForEach-Object { '{0:X2}' -f $_ }) -join ' '
    Write-Output "Around newline at $pos : $hex"
}

# Also check for UTF-16 BOM or other encoding markers
Write-Output "First 3 bytes: $(('{0:X2}' -f $bytes[0])) $(('{0:X2}' -f $bytes[1])) $(('{0:X2}' -f $bytes[2]))"

# Check for zero-width characters or other Unicode oddities
$zeroWidthCount = 0
for ($i = 0; $i -lt $bytes.Length; $i++) {
    if ($bytes[$i] -eq 0xEF -and $i + 2 -lt $bytes.Length -and $bytes[$i+1] -eq 0xBB -and $bytes[$i+2] -eq 0xBF) {
        $zeroWidthCount++
        Write-Output "Found UTF-8 BOM at position $i"
    }
}
Write-Output "UTF-8 BOM count: $zeroWidthCount"
