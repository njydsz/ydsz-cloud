# Fix JsonUtils.fromJson -> JsonUtils.parseObject across ALL common modules
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
$modulesRoot = 'D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common'
$javaFiles = Get-ChildItem -Path $modulesRoot -Recurse -Filter '*.java' -ErrorAction SilentlyContinue

$fixCount = 0
foreach ($f in $javaFiles) {
    $c = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $original = $c
    $c = $c -replace 'JsonUtils\.fromJson\(', 'JsonUtils.parseObject('
    if ($c -ne $original) {
        [System.IO.File]::WriteAllText($f.FullName, $c, $utf8NoBom)
        $fixCount++
    }
}
Write-Output "Fixed $fixCount files (fromJson -> parseObject)"
