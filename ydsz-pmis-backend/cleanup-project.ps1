# Cleanup Script: Delete moved files from ydsz-pmis-project module
$projectSrc = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-project\src\main\java\com\njydsz\pmis\project"
$projectRes = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-project\src\main\resources"

Write-Host "=== Deleting moved files from project module ==="

# Delete sales domain files
Remove-Item -Path "$projectSrc\entity\opportunity\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\entity\opportunity" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\entity\contract\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\entity\contract" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted entity/opportunity, entity/contract"

Remove-Item -Path "$projectSrc\dto\opportunity\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\dto\opportunity" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\dto\contract\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\dto\contract" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted dto/opportunity, dto/contract"

Remove-Item -Path "$projectSrc\enums\opportunity\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\enums\opportunity" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\enums\contract\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\enums\contract" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted enums/opportunity, enums/contract"

# Delete sales infra files
Remove-Item -Path "$projectSrc\mapper\opportunity\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\mapper\opportunity" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\mapper\contract\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\mapper\contract" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted mapper/opportunity, mapper/contract"

# Delete sales server files
Remove-Item -Path "$projectSrc\service\opportunity\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\service\opportunity" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\service\contract\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\service\contract" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\service\impl\opportunity\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\service\impl\opportunity" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\service\impl\contract\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\service\impl\contract" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted service/opportunity, service/contract, service/impl/opportunity, service/impl/contract"

# Delete sales web files
Remove-Item -Path "$projectSrc\controller\opportunity\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\controller\opportunity" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\controller\contract\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\controller\contract" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted controller/opportunity, controller/contract"

# Delete sales engine files
Remove-Item -Path "$projectSrc\engine\WinRateEvaluator.java" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\engine\ContractRiskEvaluator.java" -Force -ErrorAction SilentlyContinue
Write-Host "  Deleted engine/WinRateEvaluator, engine/ContractRiskEvaluator"

# Delete finance domain files
Remove-Item -Path "$projectSrc\entity\finance\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\entity\finance" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\dto\finance\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\dto\finance" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\enums\finance\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\enums\finance" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted entity/finance, dto/finance, enums/finance"

# Delete finance infra files
Remove-Item -Path "$projectSrc\mapper\finance\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\mapper\finance" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted mapper/finance"

# Delete finance server files
Remove-Item -Path "$projectSrc\service\finance\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\service\finance" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\service\impl\finance\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\service\impl\finance" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted service/finance, service/impl/finance"

# Delete finance web files
Remove-Item -Path "$projectSrc\controller\finance\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\controller\finance" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted controller/finance"

# Delete finance engine files
Remove-Item -Path "$projectSrc\engine\ProfitCalculator.java" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\engine\ReconcileHandler.java" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\engine\ReconcileReport.java" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\engine\ReconcileResult.java" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectSrc\engine\AlertCodeGen.java" -Force -ErrorAction SilentlyContinue
Write-Host "  Deleted engine: ProfitCalculator, ReconcileHandler, ReconcileReport, ReconcileResult, AlertCodeGen"

# Delete finance job
Remove-Item -Path "$projectSrc\job\DailyReconcileJobHandler.java" -Force -ErrorAction SilentlyContinue
Write-Host "  Deleted job/DailyReconcileJobHandler"

# Delete mapper XML files
Remove-Item -Path "$projectRes\mapper\opportunity\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectRes\mapper\opportunity" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectRes\mapper\contract\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectRes\mapper\contract" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$projectRes\mapper\finance\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$projectRes\mapper\finance" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted mapper XML: opportunity, contract, finance"

# Delete test files that were for moved controllers
Remove-Item -Path "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-project\src\test\java\com\njydsz\pmis\project\controller\finance\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-project\src\test\java\com\njydsz\pmis\project\controller\finance" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-project\src\test\java\com\njydsz\pmis\project\controller\opportunity\*" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-project\src\test\java\com\njydsz\pmis\project\controller\opportunity" -Force -Recurse -ErrorAction SilentlyContinue
Write-Host "  Deleted test files: controller/finance, controller/opportunity"

Write-Host "`n=== Cleanup Complete ==="
$remaining = (Get-ChildItem -Path "$projectSrc" -Recurse -File -Filter *.java | Measure-Object).Count
Write-Host "Remaining Java files in project module: $remaining"
