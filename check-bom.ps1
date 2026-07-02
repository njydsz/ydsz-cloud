$path = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\src\test\java\com\njydsz\pmis\common\migration\EncryptedFieldMigrationServiceTest.java'
$bytes = [System.IO.File]::ReadAllBytes($path)
$first8 = $bytes[0..7] | ForEach-Object { $_.ToString('X2') }
Write-Output ("First 8 bytes: " + ($first8 -join ' '))
Write-Output ("Has BOM (EF BB BF): " + ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF))
