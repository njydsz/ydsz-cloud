$ErrorActionPreference = "Stop"
$rootDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

# Find files with JsonUtils.getMapper()
$files = Get-ChildItem -Path $rootDir -Recurse -Filter "*.java" |
    Select-String -Pattern 'JsonUtils\.getMapper\(\)' -List |
    Select-Object -ExpandProperty Path -Unique |
    Where-Object { $_ -notmatch 'JsonUtils\.java$' }

Write-Host "Found $($files.Count) files with JsonUtils.getMapper()"

foreach ($file in $files) {
    $content = Get-Content $file -Raw -Encoding UTF8
    $original = $content

    # Replace JsonUtils.getMapper().readTree(x) -> YdszJson.readTree(x)
    $content = [regex]::Replace($content, 'JsonUtils\.getMapper\(\)\.readTree\(([^)]+)\)', 'YdszJson.readTree($1)')

    # Replace JsonUtils.getMapper().writeValueAsString(x) -> JsonUtils.toJson(x)
    $content = [regex]::Replace($content, 'JsonUtils\.getMapper\(\)\.writeValueAsString\(([^)]+)\)', 'JsonUtils.toJson($1)')

    # Replace JsonUtils.getMapper().writeValueAsBytes(x) -> JsonUtils.toJsonBytes(x)
    $content = [regex]::Replace($content, 'JsonUtils\.getMapper\(\)\.writeValueAsBytes\(([^)]+)\)', 'JsonUtils.toJsonBytes($1)')

    # Replace JsonUtils.getMapper().readValue(x, Class.class) -> JsonUtils.fromJson(x, Class.class)
    $content = [regex]::Replace($content, 'JsonUtils\.getMapper\(\)\.readValue\(([^,]+),\s*(\w+\.class)\)', 'JsonUtils.fromJson($1, $2)')

    # Replace JsonUtils.getMapper().readValue(x, new YdszJsonType<...>() {}) -> JsonUtils.fromJson(x, new YdszJsonType<...>() {})
    $content = [regex]::Replace($content, 'JsonUtils\.getMapper\(\)\.readValue\(([^,]+),\s*(new YdszJsonType[^)]+)\)', 'JsonUtils.fromJson($1, $2)')

    # Replace JsonUtils.getMapper().createObjectNode() -> new ObjectNode()
    $content = $content -replace 'JsonUtils\.getMapper\(\)\.createObjectNode\(\)', 'new ObjectNode()'

    # Replace JsonUtils.getMapper().createArrayNode() -> new ArrayNode()
    $content = $content -replace 'JsonUtils\.getMapper\(\)\.createArrayNode\(\)', 'new ArrayNode()'

    # Replace JsonUtils.getMapper().configure(...) -> (remove the line)
    $content = [regex]::Replace($content, 'JsonUtils\.getMapper\(\)\.configure\([^;]+;\r?\n', '')

    # Replace JsonUtils.getMapper().setDateFormat(...) -> (remove the line)
    $content = [regex]::Replace($content, 'JsonUtils\.getMapper\(\)\.setDateFormat\([^;]+;\r?\n', '')

    # Replace any remaining JsonUtils.getMapper() variable assignment
    # ObjectMapper mapper = JsonUtils.getMapper(); -> // (removed, use JsonUtils static methods)
    $content = [regex]::Replace($content, 'ObjectMapper\s+\w+\s*=\s*JsonUtils\.getMapper\(\)\s*;', '// Use JsonUtils static methods (YdszJson engine)')

    # Replace mapper.readTree(x) -> YdszJson.readTree(x)  (for local mapper variables)
    $content = [regex]::Replace($content, '\bmapper\.readTree\(([^)]+)\)', 'YdszJson.readTree($1)')

    # Replace mapper.writeValueAsString(x) -> JsonUtils.toJson(x)
    $content = [regex]::Replace($content, '\bmapper\.writeValueAsString\(([^)]+)\)', 'JsonUtils.toJson($1)')

    # Replace mapper.readValue(x, Class.class) -> JsonUtils.fromJson(x, Class.class)
    $content = [regex]::Replace($content, '\bmapper\.readValue\(([^,]+),\s*(\w+\.class)\)', 'JsonUtils.fromJson($1, $2)')

    # Replace mapper.writeValueAsBytes(x) -> JsonUtils.toJsonBytes(x)
    $content = [regex]::Replace($content, '\bmapper\.writeValueAsBytes\(([^)]+)\)', 'JsonUtils.toJsonBytes($1)')

    # Replace mapper.createObjectNode() -> new ObjectNode()
    $content = $content -replace '\bmapper\.createObjectNode\(\)', 'new ObjectNode()'

    # Replace mapper.createArrayNode() -> new ArrayNode()
    $content = $content -replace '\bmapper\.createArrayNode\(\)', 'new ArrayNode()'

    # Add YdszJson import if needed
    if ($content -match 'YdszJson\.' -and -not ($content -match 'import com\.njydsz\.pmis\.common\.json\.YdszJson;')) {
        $lastImport = [regex]::Match($content, '(import [^;]+;\r?\n)(?!import)')
        if ($lastImport.Success) {
            $insertAt = $lastImport.Index + $lastImport.Length
            $content = $content.Substring(0, $insertAt) + "import com.njydsz.pmis.common.json.YdszJson;`r`n" + $content.Substring($insertAt)
        }
    }

    # Add ObjectNode import if needed
    if ($content -match 'new ObjectNode\(\)' -and -not ($content -match 'import com\.njydsz\.pmis\.common\.json\.tree\.ObjectNode;')) {
        $lastImport = [regex]::Match($content, '(import [^;]+;\r?\n)(?!import)')
        if ($lastImport.Success) {
            $insertAt = $lastImport.Index + $lastImport.Length
            $content = $content.Substring(0, $insertAt) + "import com.njydsz.pmis.common.json.tree.ObjectNode;`r`n" + $content.Substring($insertAt)
        }
    }

    # Add ArrayNode import if needed
    if ($content -match 'new ArrayNode\(\)' -and -not ($content -match 'import com\.njydsz\.pmis\.common\.json\.tree\.ArrayNode;')) {
        $lastImport = [regex]::Match($content, '(import [^;]+;\r?\n)(?!import)')
        if ($lastImport.Success) {
            $insertAt = $lastImport.Index + $lastImport.Length
            $content = $content.Substring(0, $insertAt) + "import com.njydsz.pmis.common.json.tree.ArrayNode;`r`n" + $content.Substring($insertAt)
        }
    }

    if ($content -ne $original) {
        Set-Content -Path $file -Value $content -Encoding UTF8 -NoNewline
        Write-Host "  Modified: $(Split-Path $file -Leaf)"
    }
}

Write-Host "`nDone!"
