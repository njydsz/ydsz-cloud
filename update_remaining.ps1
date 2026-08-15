# Catch-all for remaining Javadoc/string references
$mappings = [ordered]@{
    # Javadoc {@link} references from API layer
    "com.njydsz.literule.server.expr.liteexpr.LiteExprEvaluator" = "com.njydsz.literule.server.engine.liteexpr.LiteExprEvaluator"
    "com.njydsz.literule.server.expr.liteexpr." = "com.njydsz.literule.server.engine.liteexpr."
    
    # Javadoc references to engine classes from API layer comments
    "com.njydsz.literule.server.expr.liteexpr" = "com.njydsz.literule.server.engine.liteexpr"
    
    # Javadoc specific cross-references (these are in the source code as comments)
    "{@link com.njydsz.literule.server.expr.liteexpr" = "{@link com.njydsz.literule.server.engine.liteexpr"
    "{@link com.njydsz.literule.server.expr.ExprLexer" = "{@link com.njydsz.literule.server.engine.liteexpr.ExprLexer"
    "{@link com.njydsz.literule.server.expr.ExprParser" = "{@link com.njydsz.literule.server.engine.liteexpr.ExprParser"
    "{@link com.njydsz.literule.server.expr.ExprNode" = "{@link com.njydsz.literule.server.engine.liteexpr.ExprNode"
    "{@link com.njydsz.literule.server.expr.ExprTraceBuilder" = "{@link com.njydsz.literule.server.engine.liteexpr.ExprTraceBuilder"
    "{@link com.njydsz.literule.server.expr.Token}" = "{@link com.njydsz.literule.server.engine.liteexpr.Token}"
    "{@link com.njydsz.literule.server.expr.TokenType}" = "{@link com.njydsz.literule.server.engine.liteexpr.TokenType}"
    "{@link com.njydsz.literule.server.expr.TreeInterpreter}" = "{@link com.njydsz.literule.server.engine.liteexpr.TreeInterpreter}"
    "{@link com.njydsz.literule.server.expr.FunctionRegistry}" = "{@link com.njydsz.literule.server.engine.liteexpr.FunctionRegistry}"
    "{@link com.njydsz.literule.server.expr.BuiltinFunctions}" = "{@link com.njydsz.literule.server.engine.liteexpr.BuiltinFunctions}"
    "{@link com.njydsz.literule.server.expr.LiteExprCompiler}" = "{@link com.njydsz.literule.server.engine.liteexpr.LiteExprCompiler}"
    "{@link com.njydsz.literule.server.expr.LiteExprSandbox}" = "{@link com.njydsz.literule.server.engine.liteexpr.LiteExprSandbox}"
    "{@link com.njydsz.literule.server.expr.LiteExprException}" = "{@link com.njydsz.literule.server.engine.liteexpr.LiteExprException}"
    "{@link com.njydsz.literule.server.expr.LiteExprFunction}" = "{@link com.njydsz.literule.server.engine.liteexpr.LiteExprFunction}"
    "{@link com.njydsz.literule.server.expr.ExpressionValidationService" = "{@link com.njydsz.literule.server.expression.ExpressionValidationService"
    "{@link com.njydsz.literule.server.expr.ExpressionPreviewResult" = "{@link com.njydsz.literule.server.expression.ExpressionPreviewResult"
    "{@link com.njydsz.literule.server.expr.EmptyVariableRegistry" = "{@link com.njydsz.literule.server.expression.EmptyVariableRegistry"
    "{@link com.njydsz.literule.server.expr.VariableDefinition" = "{@link com.njydsz.literule.server.expression.VariableDefinition"
    "{@link com.njydsz.literule.server.expr.VariableRegistry" = "{@link com.njydsz.literule.server.expression.VariableRegistry"
    
    # Catch remaining {@link com.njydsz.literule.server.expr. (no class name)
    "{@link com.njydsz.literule.server.expr." = "{@link com.njydsz.literule.server.engine.liteexpr."
}

$git = "C:\Program Files\Git\bin\git.exe"
$allFiles = & $git ls-files "ydsz-literule/" | Where-Object { $_ -like "*.java" }
$repoRoot = "D:\Code\open\ydsz-cloud"
$updatedCount = 0

foreach ($file in $allFiles) {
    $fullPath = Join-Path $repoRoot $file
    if (-not (Test-Path $fullPath)) { continue }
    
    $original = [System.IO.File]::ReadAllText($fullPath, [System.Text.Encoding]::UTF8)
    $text = $original
    $changed = $false
    
    foreach ($old in $mappings.Keys) {
        $new = $mappings[$old]
        if ($text.Contains($old)) {
            $text = $text.Replace($old, $new)
            $changed = $true
        }
    }
    
    if ($changed) {
        [System.IO.File]::WriteAllText($fullPath, $text, [System.Text.Encoding]::UTF8)
        $updatedCount++
    }
}

Write-Host "Updated $updatedCount files in pass 2"
