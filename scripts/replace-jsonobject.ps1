$ErrorActionPreference = "Stop"
$rootDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

# Find files with JSONObject or JSONArray variable declarations
$files = Get-ChildItem -Path $rootDir -Recurse -Filter "*.java" |
    Select-String -Pattern 'JSONObject\s+\w+\s*=|JSONArray\s+\w+\s*=' -List |
    Select-Object -ExpandProperty Path -Unique

Write-Host "Found $($files.Count) files with JSONObject/JSONArray"

foreach ($file in $files) {
    $content = Get-Content $file -Raw -Encoding UTF8
    $original = $content

    # Replace JSONObject variable declarations: JSONObject xxx = -> Map<String, Object> xxx =
    $content = [regex]::Replace($content, 'JSONObject\s+(\w+)\s*=', 'Map<String, Object> $1 =')

    # Replace JSONArray variable declarations: JSONArray xxx = -> List<Object> xxx =
    $content = [regex]::Replace($content, 'JSONArray\s+(\w+)\s*=', 'List<Object> $1 =')

    # Replace JSONObject type references in method params: JSONObject -> Map<String, Object>
    # e.g. "void method(JSONObject xxx)" -> "void method(Map<String, Object> xxx)"
    $content = [regex]::Replace($content, '\((JSONObject)\s+(\w+)', '({Map<String, Object>} $2')

    # Replace JSONObject method return types: JSONObject method() -> Map<String, Object> method()
    $content = [regex]::Replace($content, 'public\s+JSONObject\s+(\w+)\s*\(', 'public Map<String, Object> $1(')
    $content = [regex]::Replace($content, 'private\s+JSONObject\s+(\w+)\s*\(', 'private Map<String, Object> $1(')

    # Replace .getJSONObject() calls - keep as is since they return Map-compatible
    # Actually, parseMap already returns Map<String, Object>, so JSONObject json = JsonUtils.parseMap(x)
    # becomes Map<String, Object> json = JsonUtils.parseMap(x) - which is correct

    # Add Map import if needed and not present
    if ($content -match 'Map<String, Object>' -and -not ($content -match 'import java\.util\.Map;')) {
        # Add after last import
        $lastImport = [regex]::Match($content, '(import [^;]+;\r?\n)(?!import)')
        if ($lastImport.Success) {
            $insertAt = $lastImport.Index + $lastImport.Length
            $content = $content.Substring(0, $insertAt) + "import java.util.Map;`r`n" + $content.Substring($insertAt)
        }
    }

    # Add List import if needed and not present
    if ($content -match 'List<Object>' -and -not ($content -match 'import java\.util\.List;')) {
        $lastImport = [regex]::Match($content, '(import [^;]+;\r?\n)(?!import)')
        if ($lastImport.Success) {
            $insertAt = $lastImport.Index + $lastImport.Length
            $content = $content.Substring(0, $insertAt) + "import java.util.List;`r`n" + $content.Substring($insertAt)
        }
    }

    if ($content -ne $original) {
        Set-Content -Path $file -Value $content -Encoding UTF8 -NoNewline
        Write-Host "  Modified: $(Split-Path $file -Leaf)"
    }
}

Write-Host "`nDone!"
