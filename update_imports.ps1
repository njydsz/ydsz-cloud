# Define the package/class mapping
$mappings = @{
    # API layer: api.expr -> api.expression
    "com.njydsz.literule.api.expr.ExpressionEvaluator" = "com.njydsz.literule.api.expression.ExpressionEvaluator"
    "com.njydsz.literule.api.expr.ExpressionFunctionDef" = "com.njydsz.literule.api.expression.ExpressionFunctionDef"
    "com.njydsz.literule.api.expr.ExpressionTraceNode" = "com.njydsz.literule.api.expression.ExpressionTraceNode"
    "com.njydsz.literule.api.expr.ExpressionValidationResult" = "com.njydsz.literule.api.expression.ExpressionValidationResult"
    
    # server.expr -> server.engine.liteexpr (engine internals)
    "com.njydsz.literule.server.expr.LiteExprEvaluator" = "com.njydsz.literule.server.engine.liteexpr.LiteExprEvaluator"
    "com.njydsz.literule.server.expr.LiteExprCompiler" = "com.njydsz.literule.server.engine.liteexpr.LiteExprCompiler"
    "com.njydsz.literule.server.expr.LiteExprSandbox" = "com.njydsz.literule.server.engine.liteexpr.LiteExprSandbox"
    "com.njydsz.literule.server.expr.LiteExprException" = "com.njydsz.literule.server.engine.liteexpr.LiteExprException"
    "com.njydsz.literule.server.expr.LiteExprFunction" = "com.njydsz.literule.server.engine.liteexpr.LiteExprFunction"
    "com.njydsz.literule.server.expr.BuiltinFunctions" = "com.njydsz.literule.server.engine.liteexpr.BuiltinFunctions"
    "com.njydsz.literule.server.expr.FunctionRegistry" = "com.njydsz.literule.server.engine.liteexpr.FunctionRegistry"
    "com.njydsz.literule.server.expr.ExprLexer" = "com.njydsz.literule.server.engine.liteexpr.ExprLexer"
    "com.njydsz.literule.server.expr.ExprParser" = "com.njydsz.literule.server.engine.liteexpr.ExprParser"
    "com.njydsz.literule.server.expr.ExprNode" = "com.njydsz.literule.server.engine.liteexpr.ExprNode"
    "com.njydsz.literule.server.expr.ExprTraceBuilder" = "com.njydsz.literule.server.engine.liteexpr.ExprTraceBuilder"
    "com.njydsz.literule.server.expr.Token" = "com.njydsz.literule.server.engine.liteexpr.Token"
    "com.njydsz.literule.server.expr.TokenType" = "com.njydsz.literule.server.engine.liteexpr.TokenType"
    "com.njydsz.literule.server.expr.TreeInterpreter" = "com.njydsz.literule.server.engine.liteexpr.TreeInterpreter"
    "com.njydsz.literule.server.expr.ExprNodeVisitor" = "com.njydsz.literule.server.engine.liteexpr.ExprNodeVisitor"
    "com.njydsz.literule.server.expr.LiteralNode" = "com.njydsz.literule.server.engine.liteexpr.LiteralNode"
    "com.njydsz.literule.server.expr.VariableNode" = "com.njydsz.literule.server.engine.liteexpr.VariableNode"
    "com.njydsz.literule.server.expr.BinaryOpNode" = "com.njydsz.literule.server.engine.liteexpr.BinaryOpNode"
    "com.njydsz.literule.server.expr.UnaryOpNode" = "com.njydsz.literule.server.engine.liteexpr.UnaryOpNode"
    "com.njydsz.literule.server.expr.TernaryNode" = "com.njydsz.literule.server.engine.liteexpr.TernaryNode"
    "com.njydsz.literule.server.expr.FunctionCallNode" = "com.njydsz.literule.server.engine.liteexpr.FunctionCallNode"
    "com.njydsz.literule.server.expr.MemberAccessNode" = "com.njydsz.literule.server.engine.liteexpr.MemberAccessNode"
    "com.njydsz.literule.server.expr.IndexNode" = "com.njydsz.literule.server.engine.liteexpr.IndexNode"
    "com.njydsz.literule.server.expr.ListNode" = "com.njydsz.literule.server.engine.liteexpr.ListNode"
    "com.njydsz.literule.server.expr.MapNode" = "com.njydsz.literule.server.engine.liteexpr.MapNode"
    "com.njydsz.literule.server.expr.LambdaNode" = "com.njydsz.literule.server.engine.liteexpr.LambdaNode"
    "com.njydsz.literule.server.expr.TemplateStringNode" = "com.njydsz.literule.server.engine.liteexpr.TemplateStringNode"
    
    # server.expr -> server.expression (expression services)
    "com.njydsz.literule.server.expr.ExpressionValidationService" = "com.njydsz.literule.server.expression.ExpressionValidationService"
    "com.njydsz.literule.server.expr.ExpressionPreviewResult" = "com.njydsz.literule.server.expression.ExpressionPreviewResult"
    "com.njydsz.literule.server.expr.EmptyVariableRegistry" = "com.njydsz.literule.server.expression.EmptyVariableRegistry"
    "com.njydsz.literule.server.expr.VariableDefinition" = "com.njydsz.literule.server.expression.VariableDefinition"
    "com.njydsz.literule.server.expr.VariableRegistry" = "com.njydsz.literule.server.expression.VariableRegistry"
}

# Get all Java files
$git = "C:\Program Files\Git\bin\git.exe"
$allFiles = & $git ls-files "ydsz-literule/" | Where-Object { $_ -like "*.java" }
$repoRoot = "D:\Code\open\ydsz-cloud"
$updatedCount = 0

foreach ($file in $allFiles) {
    $fullPath = Join-Path $repoRoot $file
    if (-not (Test-Path $fullPath)) { continue }
    
    $original = [System.IO.File]::ReadAllText($fullPath, [System.Text.Encoding]::UTF8)
    $content = $original
    $changed = $false
    
    foreach ($old in $mappings.Keys) {
        $new = $mappings[$old]
        if ($content.Contains($old)) {
            $content = $content.Replace($old, $new)
            $changed = $true
        }
    }
    
    if ($changed) {
        [System.IO.File]::WriteAllText($fullPath, $content, [System.Text.Encoding]::UTF8)
        $updatedCount++
    }
}

Write-Host "Updated $updatedCount files"
