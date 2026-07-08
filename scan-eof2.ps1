$file = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-agent\src\test\java\com\njydsz\pmis\agent\rag\RAGServiceTest.java'
$bytes = [System.IO.File]::ReadAllBytes($file)
Write-Host ('Total bytes: ' + $bytes.Length)
Write-Host ('First 3 bytes: {0:X2} {1:X2} {2:X2}' -f $bytes[0], $bytes[1], $bytes[2])
$found = $false
for ($i = 0; $i -lt $bytes.Length; $i++) {
    $b = $bytes[$i]
    if ($b -lt 0x20 -and $b -ne 0x09 -and $b -ne 0x0A -and $b -ne 0x0D) {
        Write-Host ('Control char 0x{0:X2} at offset {1}' -f $b, $i)
        $found = $true
    }
}
if (-not $found) { Write-Host 'No control chars found' }
# dump lines 82-86 as hex
$lineNum = 1
$lineStart = 0
for ($i = 0; $i -lt $bytes.Length; $i++) {
    if ($bytes[$i] -eq 0x0A) {
        if ($lineNum -ge 82 -and $lineNum -le 86) {
            $lineBytes = $bytes[$lineStart..($i-1)]
            $hex = ($lineBytes | ForEach-Object { '{0:X2}' -f $_ }) -join ' '
            Write-Host ('Line {0} ({1}b): {2}' -f $lineNum, $lineBytes.Length, $hex)
        }
        $lineNum++
        $lineStart = $i + 1
    }
}
