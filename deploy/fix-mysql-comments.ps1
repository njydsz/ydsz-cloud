$sqlFiles = @(
    "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0_016__init_pmis_security.sql",
    "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0_017__init_pmis_after_sales_schema.sql",
    "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0_018__init_pmis_smart_p4_2_schema.sql"
)

foreach ($file in $sqlFiles) {
    $content = Get-Content $file -Raw -Encoding UTF8
    $original = $content

    # Remove MySQL-style inline column comments
    # Pattern: starts with whitespace, then "COMMENT '...'", optional trailing comma/semicolon
    $content = [regex]::Replace($content, "(?m)^[ \t]+COMMENT\s+'[^']*'[ \t]*,?\s*\r?\n", "")

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($file, $content, [System.Text.Encoding]::UTF8)
        Write-Host "Fixed: $file"
    } else {
        Write-Host "No change: $file"
    }
}
