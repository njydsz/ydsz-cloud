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
    $lines = Get-Content $path -Encoding UTF8
    $newLines = [System.Collections.ArrayList]::new()
    $inserted = $false
    
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $newLines.Add($line)
        
        # After the builder class declaration line, insert self() method
        if (-not $inserted -and $line -match 'public static class (\w+Builder) extends YdszExceptionBuilder') {
            $builderName = $matches[1]
            $newLines.Add('')
            $newLines.Add('        @Override')
            $newLines.Add("        protected ${builderName} self() {")
            $newLines.Add('            return this;')
            $newLines.Add('        }')
            $inserted = $true
        }
    }
    
    if ($inserted) {
        Set-Content $path -Value $newLines -Encoding UTF8
        Write-Host "Added self() to $file"
    } else {
        Write-Host "WARN: No builder class found in $file"
    }
}
