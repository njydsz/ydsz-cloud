Set-Location 'd:\Code\ydsz\ydsz-pmis\deploy\sql'
Get-ChildItem -Filter '*.sql' | Sort-Object Name | ForEach-Object {
    $name = $_.Name
    $lines = (Get-Content $_.FullName).Count
    $comments = (Select-String -Path $_.FullName -Pattern 'COMMENT ON' -SimpleMatch).Count
    $tables = (Select-String -Path $_.FullName -Pattern 'CREATE TABLE' -SimpleMatch).Count
    '{0,-55} {1,6}行 {2,3}表 {3,4}注释' -f $name, $lines, $tables, $comments
}
