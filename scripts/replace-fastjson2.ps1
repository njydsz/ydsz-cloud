$ErrorActionPreference = "Stop"
$rootDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

$files = Get-ChildItem -Path $rootDir -Recurse -Filter "*.java" |
    Select-String -Pattern "import com\.alibaba\.fastjson2\.|import com\.fasterxml\.jackson\." -List |
    Select-Object -ExpandProperty Path -Unique

Write-Host "Found $($files.Count) files to process"

foreach ($file in $files) {
    $content = Get-Content $file -Raw -Encoding UTF8
    $original = $content
    $modified = $false

    # 1. Remove fastjson2 import lines
    if ($content -match 'import com\.alibaba\.fastjson2\.') {
        $content = $content -replace 'import com\.alibaba\.fastjson2\.[\w.]*;\r?\n', ''
        $modified = $true
    }

    # 2. Replace Jackson TypeReference import
    if ($content -match 'import com\.fasterxml\.jackson\.core\.type\.TypeReference;') {
        $content = $content -replace 'import com\.fasterxml\.jackson\.core\.type\.TypeReference;', 'import com.njydsz.pmis.common.json.type.YdszJsonType;'
        $modified = $true
    }

    # 3. Replace Jackson annotation imports
    if ($content -match 'import com\.fasterxml\.jackson\.annotation\.JsonProperty;') {
        $content = $content -replace 'import com\.fasterxml\.jackson\.annotation\.JsonProperty;', 'import com.njydsz.pmis.common.json.annotation.YdszJsonField;'
        $modified = $true
    }
    if ($content -match 'import com\.fasterxml\.jackson\.annotation\.JsonInclude;') {
        $content = $content -replace 'import com\.fasterxml\.jackson\.annotation\.JsonInclude;', 'import com.njydsz.pmis.common.json.annotation.YdszJsonField;'
        $modified = $true
    }
    if ($content -match 'import com\.fasterxml\.jackson\.annotation\.JsonFormat;') {
        $content = $content -replace 'import com\.fasterxml\.jackson\.annotation\.JsonFormat;', 'import com.njydsz.pmis.common.json.annotation.YdszJsonFormat;'
        $modified = $true
    }
    if ($content -match 'import com\.fasterxml\.jackson\.annotation\.JsonIgnore;') {
        $content = $content -replace 'import com\.fasterxml\.jackson\.annotation\.JsonIgnore;', 'import com.njydsz.pmis.common.json.annotation.YdszJsonField;'
        $modified = $true
    }

    # 4. Remove other Jackson imports (databind, datatype, core)
    if ($content -match 'import com\.fasterxml\.jackson\.(databind|datatype|core)\.') {
        $content = $content -replace 'import com\.fasterxml\.jackson\.(databind|datatype|core)\.[\w.]*;\r?\n', ''
        $modified = $true
    }

    # 5. Replace TypeReference usage: new TypeReference -> new YdszJsonType
    if ($content -match 'TypeReference') {
        $content = $content -replace 'TypeReference', 'YdszJsonType'
        $modified = $true
    }

    # 6. Replace FastJSON2 API calls
    # JSON.toJSONString(x, JSONWriter.Feature.xxx) -> JsonUtils.toJson(x)
    $content = [regex]::Replace($content, 'JSON\.toJSONString\(([^,)]+),\s*JSONWriter\.Feature\.\w+\)', 'JsonUtils.toJson($1)')
    # JSON.toJSONString(x) -> JsonUtils.toJson(x)
    $content = [regex]::Replace($content, 'JSON\.toJSONString\(([^)]+)\)', 'JsonUtils.toJson($1)')
    # JSON.parseObject(x, Map.class, JSONReader.Feature.xxx) -> JsonUtils.parseMap(x)
    $content = [regex]::Replace($content, 'JSON\.parseObject\(([^,]+),\s*Map\.class,\s*JSONReader\.Feature\.\w+\)', 'JsonUtils.parseMap($1)')
    # JSON.parseObject(x) -> JsonUtils.parseMap(x)  (no class arg)
    $content = [regex]::Replace($content, 'JSON\.parseObject\(([^,)]+)\)', 'JsonUtils.parseMap($1)')
    # JSON.parseObject(x, SomeClass.class) -> JsonUtils.fromJson(x, SomeClass.class)
    $content = [regex]::Replace($content, 'JSON\.parseObject\(([^,]+),\s*(\w+\.class)\)', 'JsonUtils.fromJson($1, $2)')
    # JSON.parseArray(x) -> JsonUtils.parseList(x)
    $content = [regex]::Replace($content, 'JSON\.parseArray\(([^,)]+)\)', 'JsonUtils.parseList($1)')
    # JSON.parseArray(x, SomeClass.class) -> JsonUtils.fromJsonToList(x, SomeClass.class)
    $content = [regex]::Replace($content, 'JSON\.parseArray\(([^,]+),\s*(\w+\.class)\)', 'JsonUtils.fromJsonToList($1, $2)')

    # 7. Replace Jackson API calls
    # mapper.writeValueAsString(x) -> JsonUtils.toJson(x)
    $content = [regex]::Replace($content, '\b\w+\.writeValueAsString\(([^)]+)\)', 'JsonUtils.toJson($1)')
    # mapper.readValue(x, Class.class) -> JsonUtils.fromJson(x, Class.class)
    $content = [regex]::Replace($content, '\b\w+\.readValue\(([^,]+),\s*(\w+\.class)\)', 'JsonUtils.fromJson($1, $2)')

    # 8. Replace Jackson annotations
    $content = [regex]::Replace($content, '@JsonProperty\("([^"]+)"\)', '@YdszJsonField("$1")')
    $content = [regex]::Replace($content, '@JsonProperty\(value\s*=\s*"([^"]+)"\)', '@YdszJsonField("$1")')
    $content = [regex]::Replace($content, '@JsonInclude\([^)]+\)', '@YdszJsonField(notWriteNullValue = true)')
    $content = $content -replace '@JsonIgnore', '@YdszJsonField(ignore = true)'
    $content = [regex]::Replace($content, '@JsonFormat\(pattern\s*=\s*"([^"]+)"\)', '@YdszJsonFormat("$1")')
    $content = [regex]::Replace($content, '@JsonFormat\(pattern\s*=\s*"([^"]+)",\s*timezone\s*=\s*"([^"]+)"\)', '@YdszJsonFormat(value = "$1", timezone = "$2")')

    # 9. Replace ObjectMapper declarations
    $content = [regex]::Replace($content, 'private\s+static\s+final\s+ObjectMapper\s+\w+\s*=\s*new\s+ObjectMapper\(\);', '// JsonUtils as JSON engine')
    $content = [regex]::Replace($content, 'ObjectMapper\s+\w+\s*=\s*new\s+ObjectMapper\(\);', '// JsonUtils as JSON engine')

    # 10. Add JsonUtils import if needed
    $needJsonUtils = ($content -match 'JsonUtils\.') -and -not ($content -match 'import com\.njydsz\.pmis\.common\.util\.json\.JsonUtils;')
    if ($needJsonUtils) {
        $lastImport = [regex]::Match($content, '(import [^;]+;\r?\n)(?!import)')
        if ($lastImport.Success) {
            $insertAt = $lastImport.Index + $lastImport.Length
            $content = $content.Substring(0, $insertAt) + "`r`nimport com.njydsz.pmis.common.util.json.JsonUtils;`r`n" + $content.Substring($insertAt)
        } else {
            $pkgMatch = [regex]::Match($content, '(package [^;]+;\r?\n)')
            if ($pkgMatch.Success) {
                $insertAt = $pkgMatch.Index + $pkgMatch.Length
                $content = $content.Substring(0, $insertAt) + "`r`nimport com.njydsz.pmis.common.util.json.JsonUtils;`r`n" + $content.Substring($insertAt)
            }
        }
        $modified = $true
    }

    if ($modified -and $content -ne $original) {
        $content = [regex]::Replace($content, '(\r?\n){3,}', "`r`n`r`n")
        Set-Content -Path $file -Value $content -Encoding UTF8 -NoNewline
        Write-Host "  Modified: $(Split-Path $file -Leaf)"
    }
}

Write-Host "`nDone!"
