# 提取所有 CREATE TABLE 语句的表名和行号
$sqlFile = "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
$lines = Get-Content $sqlFile

$tables = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^CREATE TABLE IF NOT EXISTS (pmis_\w+)') {
        $tableName = $matches[1]
        # Skip partition tables
        if ($tableName -match '_y\d{4}m\d{2}$') { continue }
        $tables += [PSCustomObject]@{
            Line = $i + 1
            Table = $tableName
        }
    }
}

Write-Host "Total tables: $($tables.Count)"
Write-Host ""
foreach ($t in $tables) {
    Write-Host "$($t.Line): $($t.Table)"
}
