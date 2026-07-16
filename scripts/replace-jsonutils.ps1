$ErrorActionPreference = "Stop"
$rootDir = "d:\Code\ydsz\ydsz\ydsz-backend"

$files = Get-ChildItem -Path $rootDir -Recurse -Filter "*.java" |
    Select-String -Pattern 'JsonUtils\.' -List |
    Select-Object -ExpandProperty Path -Unique |
    Where-Object { $_ -notmatch '\\YdszJson\.java$' }

Write-Host "Found $($files.Count) files with JsonUtils references"

foreach ($file in $files) {
    $content = Get-Content $file -Raw -Encoding UTF8
    $original = $content

    # 1. Replace import
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.json\.JsonUtils;', 'import com.njydsz.common.json.YdszJson;'

    # 2. Replace JsonUtils.JsonException -> YdszJsonException (in catch blocks, type refs)
    $content = $content -replace 'JsonUtils\.JsonException', 'YdszJsonException'
    # Also handle the import if it was explicitly imported
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.json\.JsonUtils\.JsonException;', 'import com.njydsz.common.json.exception.YdszJsonException;'

    # 3. Replace API calls (order matters - longer patterns first!)
    # JsonUtils.fromJsonToList(json, Class) -> YdszJson.parseArray(json, Class)
    $content = [regex]::Replace($content, 'JsonUtils\.fromJsonToList\(', 'YdszJson.parseArray(')

    # JsonUtils.fromJsonBytes( -> YdszJson.fromJsonBytes(
    $content = [regex]::Replace($content, 'JsonUtils\.fromJsonBytes\(', 'YdszJson.fromJsonBytes(')

    # JsonUtils.fromJsonToMap( -> YdszJson.fromJsonToMap(
    $content = [regex]::Replace($content, 'JsonUtils\.fromJsonToMap\(', 'YdszJson.fromJsonToMap(')

    # JsonUtils.toPrettyJson( -> YdszJson.format(
    $content = [regex]::Replace($content, 'JsonUtils\.toPrettyJson\(', 'YdszJson.format(')

    # JsonUtils.fromJson( -> YdszJson.toObject(
    $content = [regex]::Replace($content, 'JsonUtils\.fromJson\(', 'YdszJson.toObject(')

    # JsonUtils.parseObject(json, Class) -> YdszJson.toObject(json, Class)
    # (has 2 args = deserialize to object, NOT parseMap)
    $content = [regex]::Replace($content, 'JsonUtils\.parseObject\(([^,)]+),', 'YdszJson.toObject($1,')

    # JsonUtils.parseMap( -> YdszJson.parseMap(
    $content = [regex]::Replace($content, 'JsonUtils\.parseMap\(', 'YdszJson.parseMap(')

    # JsonUtils.parseList( -> YdszJson.parseArray(
    $content = [regex]::Replace($content, 'JsonUtils\.parseList\(', 'YdszJson.parseArray(')

    # JsonUtils.toJsonBytes( -> YdszJson.toJsonBytes(
    $content = [regex]::Replace($content, 'JsonUtils\.toJsonBytes\(', 'YdszJson.toJsonBytes(')

    # JsonUtils.toJson( -> YdszJson.toJson(
    $content = [regex]::Replace($content, 'JsonUtils\.toJson\(', 'YdszJson.toJson(')

    # JsonUtils.isValidJson( -> YdszJson.isValid(
    $content = [regex]::Replace($content, 'JsonUtils\.isValidJson\(', 'YdszJson.isValid(')

    # JsonUtils.setMetrics( -> YdszJson.setMetricsCallback(
    $content = [regex]::Replace($content, 'JsonUtils\.setMetrics\(', 'YdszJson.setMetricsCallback(')

    # JsonUtils.getMetrics() -> YdszJson.getMetricsCallback()
    $content = [regex]::Replace($content, 'JsonUtils\.getMetrics\(\)', 'YdszJson.getMetricsCallback()')

    # Any remaining JsonUtils.xxx -> YdszJson.xxx (fallback)
    $content = [regex]::Replace($content, 'JsonUtils\.', 'YdszJson.')

    if ($content -ne $original) {
        Set-Content -Path $file -Value $content -Encoding UTF8 -NoNewline
        Write-Host "  Modified: $(Split-Path $file -Leaf)"
    }
}

Write-Host "`nDone!"
