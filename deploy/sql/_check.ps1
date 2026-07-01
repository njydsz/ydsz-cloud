$sqlDir = 'd:\Code\ydsz\ydsz-pmis\deploy\sql'
$files = Get-ChildItem -Path $sqlDir -Filter 'V*.sql' | Sort-Object Name
Write-Host ("{0,-55} {1,5} {2,5} {3,8} {4,8}" -f 'File', 'Tables', 'Views', 'TblComm', 'ColComm')
Write-Host ('-' * 90)
foreach ($f in $files) {
    $content = Get-Content $f.FullName -Raw -Encoding UTF8
    $ct = ([regex]::Matches($content, 'CREATE TABLE')).Count
    $cv = ([regex]::Matches($content, 'CREATE OR REPLACE VIEW')).Count
    $tc = ([regex]::Matches($content, 'COMMENT ON TABLE')).Count
    $cc = ([regex]::Matches($content, 'COMMENT ON COLUMN')).Count
    Write-Host ("{0,-55} {1,5} {2,5} {3,8} {4,8}" -f $f.Name, $ct, $cv, $tc, $cc)
}
