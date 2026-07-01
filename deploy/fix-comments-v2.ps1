# Fix the column separators lost when MySQL-style COMMENT lines were removed.
# Pattern: line N ends with column definition, line N+1 was the COMMENT line ending with comma/semicolon.
# We need to add comma/semicolon to the end of line N, then remove line N+1.

$sqlFiles = @(
    "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0_016__init_pmis_security.sql",
    "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0_017__init_pmis_after_sales_schema.sql",
    "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0_018__init_pmis_smart_p4_2_schema.sql"
)

foreach ($file in $sqlFiles) {
    $content = Get-Content $file -Raw -Encoding UTF8
    $original = $content

    # Pattern 1: Line ends with comma after COMMENT 'xxx',
    # The line above (the column definition) needs a comma added
    # Format:
    #     column_def_line
    #         COMMENT 'xxx',
    # Should become:
    #     column_def_line,
    $content = [regex]::Replace(
        $content,
        "([^\r\n]+?)\r?\n[ \t]+COMMENT '[^']*',\r?\n",
        "`$1,`r`n",
        [System.Text.RegularExpressions.RegexOptions]::Multiline
    )

    # Pattern 2: Line ends with semicolon after COMMENT 'xxx';
    $content = [regex]::Replace(
        $content,
        "([^\r\n]+?)\r?\n[ \t]+COMMENT '[^']*';\r?\n",
        "`$1;`r`n",
        [System.Text.RegularExpressions.RegexOptions]::Multiline
    )

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($file, $content, [System.Text.Encoding]::UTF8)
        Write-Host "Fixed: $file"
    } else {
        Write-Host "No change: $file"
    }
}
