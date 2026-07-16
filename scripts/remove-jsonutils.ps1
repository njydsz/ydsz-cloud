$ErrorActionPreference = "Stop"
$rootDir = "d:\Code\ydsz\ydsz\ydsz-backend"

# Find all Java files that reference JsonUtils (excluding JsonUtils.java itself)
$files = Get-ChildItem -Path $rootDir -Recurse -Include '*.java' |
    Select-String -Pattern 'JsonUtils' -List |
    Select-Object -ExpandProperty Path -Unique |
    Where-Object { $_ -notmatch 'JsonUtils\.java$' -and $_ -notmatch 'JsonMetrics' }

Write-Host "Found $($files.Count) files with JsonUtils references (comments/javadoc)"

foreach ($file in $files) {
    $content = Get-Content $file -Raw -Encoding UTF8
    $original = $content

    # Replace Javadoc references: {@link JsonUtils#xxx} -> {@link YdszJson#xxx}
    $content = [regex]::Replace($content, '\{@link JsonUtils#(\w+)', '{@link YdszJson#$1')
    # Replace Javadoc references: {@link JsonUtils} -> {@link YdszJson}
    $content = $content -replace '\{@link JsonUtils\}', '{@link YdszJson}'
    # Replace comment references: JsonUtils -> YdszJson (but not the class name in JsonUtils.java)
    $content = $content -replace 'JsonUtils', 'YdszJson'

    # Fix import: if the file imported JsonUtils, replace with YdszJson import
    if ($content -match 'import com\.njydsz\.pmis\.common\.util\.json\.JsonUtils;') {
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.json\.JsonUtils;', 'import com.njydsz.common.json.YdszJson;'
    }

    if ($content -ne $original) {
        Set-Content -Path $file -Value $content -Encoding UTF8 -NoNewline
        Write-Host "  Modified: $(Split-Path $file -Leaf)"
    }
}

Write-Host "`nDone!"
