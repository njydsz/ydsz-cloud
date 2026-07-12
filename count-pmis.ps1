$base = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common'
$total = 0
Get-ChildItem -Directory -Path $base | Where-Object { $_.Name -like 'ydsz-pmis-common-*' } | ForEach-Object {
    $mod = $_.Name
    $files = Get-ChildItem -Recurse -Filter *.java -Path $_.FullName | Where-Object { $_.FullName -notmatch '\\target\\' }
    $count = $files.Count
    $total += $count
    Write-Output ('{0,-35} {1,5} files' -f $mod, $count)
    foreach ($f in $files) {
        Write-Output ('  ' + $f.FullName.Replace($_.FullName + '\', ''))
    }
}
Write-Output ('TOTAL: {0} files' -f $total)
