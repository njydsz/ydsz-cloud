$exceptionDir = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-exception\src\main\java\com\njydsz\pmis\common\exception\custom'

$files = @(
    'BusinessException.java',
    'ConcurrencyException.java',
    'CircuitBreakerException.java',
    'DegradeException.java',
    'DuplicateException.java',
    'ExternalException.java',
    'InfrastructureException.java',
    'RateLimitException.java',
    'ValidationException.java',
    'YdszSecurityException.java',
    'YdszTimeoutException.java',
    'SysException.java'
)

foreach ($file in $files) {
    $path = Join-Path $exceptionDir $file
    $content = Get-Content $path -Raw -Encoding UTF8
    
    # Extract the Builder class name and Exception type from the class declaration
    # Pattern: public static class XxxBuilder extends YdszExceptionBuilder<XxxException, XxxBuilder>
    if ($content -match 'public\s+static\s+class\s+(\w+Builder)\s+extends\s+YdszExceptionBuilder<(\w+),\s*\1>') {
        $builderName = $matches[1]
        $exceptionType = $matches[2]
        
        # Find the doBuild method and add self() before it
        $selfMethod = @"
        @Override
        protected ${builderName} self() {
            return this;
        }

"@
        
        # Insert self() method before the doBuild @Override
        $newContent = $content -replace '(\s+)(@Override\s*\n\s*protected\s+' + [regex]::Escape($exceptionType) + '\s+doBuild)', "`${1}${selfMethod}`${1}`${2}"
        
        if ($newContent -ne $content) {
            Set-Content $path -Value $newContent -Encoding UTF8 -NoNewline
            Write-Host "Added self() to $file ($builderName -> $exceptionType)"
        } else {
            Write-Host "WARN: Could not insert self() into $file"
        }
    } else {
        Write-Host "WARN: Could not match builder pattern in $file"
    }
}
