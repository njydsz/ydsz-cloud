# Fix remaining compilation errors in common-queue module
$targetRoot = "D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-queue\src\main\java\com\njydsz\pmis\common\queue"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$files = Get-ChildItem -Path $targetRoot -Recurse -Filter '*.java'
$fixCount = 0

foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $modified = $false

    # Fix 1: Replace JsonUtils.fromJson -> JsonUtils.parseObject
    if ($content -match 'JsonUtils\.fromJson') {
        $content = $content -replace 'JsonUtils\.fromJson', 'JsonUtils.parseObject'
        $modified = $true
    }

    # Fix 2: Replace InfrastructureException(msg, exception) -> InfrastructureException.of("queue", msg, exception)
    # But we need to be careful - only replace the (String, Exception) pattern
    # Pattern: new InfrastructureException("msg", exceptionVar)
    if ($content -match 'new InfrastructureException\(') {
        # This is complex - let's just add a constructor instead
        # Skip this fix here, will handle by adding constructor
    }

    # Fix 3: Fix remaining BizException.builder() patterns
    if ($content -match 'BizException\.builder\(\)') {
        # Pattern: BizException.builder().key("msg") -> new BizException("msg")
        $content = [regex]::Replace($content,
            'BizException\.builder\(\)\s*\.\s*key\((.*?)\)\s*\.\s*build\(\)',
            'new BizException($1)',
            [System.Text.RegularExpressions.RegexOptions]::Singleline)
        # Pattern: BizException.builder().key("msg") (without .build())
        $content = [regex]::Replace($content,
            'throw BizException\.builder\(\)\s*\.\s*key\((.*?)\);',
            'throw new BizException($1);',
            [System.Text.RegularExpressions.RegexOptions]::Singleline)
        $modified = $true
    }

    # Fix 4: Fix new BizException("msg").build() -> new BizException("msg")
    if ($content -match 'new BizException\([^)]+\)\.build\(\)') {
        $content = [regex]::Replace($content,
            'new BizException\((.*?)\)\.build\(\)',
            'new BizException($1)',
            [System.Text.RegularExpressions.RegexOptions]::Singleline)
        $modified = $true
    }

    if ($modified) {
        [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
        $fixCount++
        Write-Output "Fixed: $($file.Name)"
    }
}

Write-Output "Total files fixed: $fixCount"
