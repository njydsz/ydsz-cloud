$file = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-agent\src\test\java\com\njydsz\pmis\agent\rag\RAGServiceTest.java'
$bytes = [System.IO.File]::ReadAllBytes($file)
# 查找 UTF-8 BOM (EF BB BF) 在文件中间
for ($i = 0; $i -lt $bytes.Length - 2; $i++) {
    if ($bytes[$i] -eq 0xEF -and $bytes[$i+1] -eq 0xBB -and $bytes[$i+2] -eq 0xBF) {
        Write-Host ('UTF-8 BOM at offset {0}' -f $i)
    }
}
# 查找 0xEF 0xBF 0xBD (replacement char)
for ($i = 0; $i -lt $bytes.Length - 2; $i++) {
    if ($bytes[$i] -eq 0xEF -and $bytes[$i+1] -eq 0xBF -and $bytes[$i+2] -eq 0xBD) {
        Write-Host ('Replacement char U+FFFD at offset {0}' -f $i)
    }
}
# 统计行数
$lineCount = 0
for ($i = 0; $i -lt $bytes.Length; $i++) {
    if ($bytes[$i] -eq 0x0A) { $lineCount++ }
}
Write-Host ('Line count (LF): ' + $lineCount)
# 检查是否有 CR
$crCount = 0
for ($i = 0; $i -lt $bytes.Length; $i++) {
    if ($bytes[$i] -eq 0x0D) { $crCount++ }
}
Write-Host ('CR count: ' + $crCount)
# 检查是否有 0xC0 0x80 (overlong null)
for ($i = 0; $i -lt $bytes.Length - 1; $i++) {
    if ($bytes[$i] -eq 0xC0 -and $bytes[$i+1] -eq 0x80) {
        Write-Host ('Overlong null at offset {0}' -f $i)
    }
}
Write-Host 'Done'
