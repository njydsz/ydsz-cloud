# Fix AuthInfo/AuthInfoUtils/RequestHolder imports in common-jdbc
$targetRoot = "D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-jdbc\src\main\java"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$files = Get-ChildItem -Path $targetRoot -Recurse -Filter '*.java'
$fixCount = 0

foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $modified = $false

    # Fix: security.AuthInfo -> context.AuthInfo
    # Fix: security.AuthInfoUtils -> context.AuthInfoUtils
    # Fix: security.RequestHolder -> context.RequestHolder
    if ($content -match 'com\.njydsz\.pmis\.common\.security\.(AuthInfo|AuthInfoUtils|RequestHolder)') {
        $content = $content -replace 'com\.njydsz\.pmis\.common\.security\.AuthInfoUtils', 'com.njydsz.pmis.common.context.AuthInfoUtils'
        $content = $content -replace 'com\.njydsz\.pmis\.common\.security\.AuthInfo\b', 'com.njydsz.pmis.common.context.AuthInfo'
        $content = $content -replace 'com\.njydsz\.pmis\.common\.security\.RequestHolder', 'com.njydsz.pmis.common.context.RequestHolder'
        $modified = $true
    }

    if ($modified) {
        [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
        $fixCount++
        Write-Output "Fixed: $($file.Name)"
    }
}

Write-Output "Total files fixed: $fixCount"
