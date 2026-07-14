$files = Get-ChildItem -Path 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend' -Recurse -Include '*.java'
$results = @()
foreach ($f in $files) {
    $matches = Select-String -Path $f.FullName -Pattern 'JsonUtils' -AllMatches
    foreach ($m in $matches) {
        $results += $m.Line.Trim()
    }
}
$results | Sort-Object -Unique | ForEach-Object { Write-Host $_ }
