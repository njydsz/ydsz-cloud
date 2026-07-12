# Fix @SuperBuilder and DataSourceAutoConfiguration issues in common-jdbc
$targetRoot = "D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-jdbc\src\main\java"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$files = Get-ChildItem -Path $targetRoot -Recurse -Filter '*.java'

foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $modified = $false

    # Fix 1: Remove @SuperBuilder from Mp* entity classes
    if ($content -match '@SuperBuilder') {
        $content = $content -replace 'import lombok\.experimental\.SuperBuilder;\r?\n', ''
        $content = $content -replace '@SuperBuilder\r?\n', ''
        $modified = $true
    }

    # Fix 2: Remove DataSourceAutoConfiguration import and annotation
    if ($content -match 'DataSourceAutoConfiguration') {
        $content = $content -replace 'import org\.springframework\.boot\.autoconfigure\.jdbc\.DataSourceAutoConfiguration;\r?\n', ''
        $content = $content -replace '@AutoConfigureBefore\(DataSourceAutoConfiguration\.class\)\r?\n', ''
        $modified = $true
    }

    # Fix 3: Replace remi.jdbc with pmis.jdbc
    if ($content -match 'remi\.jdbc') {
        $content = $content -replace 'remi\.jdbc', 'pmis.jdbc'
        $modified = $true
    }

    if ($modified) {
        [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
        Write-Output "Fixed: $($file.Name)"
    }
}

Write-Output "Done"
