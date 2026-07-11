# DDD Migration Script: Split ydsz-pmis-project into sales/finance/project modules
# Run from ydsz-pmis-backend directory

$ErrorActionPreference = "Continue"
$backendRoot = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$projectSrc = "$backendRoot\ydsz-pmis-project\src\main\java\com\njydsz\pmis\project"
$projectRes = "$backendRoot\ydsz-pmis-project\src\main\resources"
$salesSrc = "$backendRoot\ydsz-pmis-sales\src\main\java\com\njydsz\pmis\sales"
$salesRes = "$backendRoot\ydsz-pmis-sales\src\main\resources"
$financeSrc = "$backendRoot\ydsz-pmis-finance\src\main\java\com\njydsz\pmis\finance"
$financeRes = "$backendRoot\ydsz-pmis-finance\src\main\resources"

function Copy-Files($srcPath, $dstPath, $filter = "*.java") {
    if (Test-Path $srcPath) {
        $files = Get-ChildItem -Path $srcPath -Filter $filter -File
        foreach ($f in $files) {
            Copy-Item -Path $f.FullName -Destination $dstPath -Force
        }
        Write-Host "  Copied $($files.Count) files from $srcPath"
    }
}

function Update-PackageNames($filePath, $oldPkg, $newPkg) {
    if (Test-Path $filePath) {
        $content = Get-Content -Path $filePath -Raw -Encoding UTF8
        $content = $content -replace [regex]::Escape($oldPkg), $newPkg
        Set-Content -Path $filePath -Value $content -Encoding UTF8 -NoNewline
    }
}

function Update-FilePackages($dirPath, $oldPkg, $newPkg) {
    $files = Get-ChildItem -Path $dirPath -Recurse -Filter "*.java" -File
    foreach ($f in $files) {
        $content = Get-Content -Path $f.FullName -Raw -Encoding UTF8
        $content = $content -replace [regex]::Escape($oldPkg), $newPkg
        Set-Content -Path $f.FullName -Value $content -Encoding UTF8 -NoNewline
    }
    Write-Host "  Updated $($files.Count) files package names"
}

Write-Host "`n=== Phase 2: Sales Domain Layer Migration ==="

# Sales Domain: Entity
Copy-Files "$projectSrc\entity\opportunity" "$salesSrc\domain\entity"
Copy-Files "$projectSrc\entity\contract" "$salesSrc\domain\entity"

# Sales Domain: DTO
Copy-Files "$projectSrc\dto\opportunity" "$salesSrc\domain\dto"
Copy-Files "$projectSrc\dto\contract" "$salesSrc\domain\dto"

# Sales Domain: Enums
Copy-Files "$projectSrc\enums\opportunity" "$salesSrc\domain\enums"
Copy-Files "$projectSrc\enums\contract" "$salesSrc\domain\enums"

# Sales Domain: VO (ruleengine DTOs are shared, keep in project)
# dto/ruleengine stays in PM

# Update package names in sales domain files
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.entity.opportunity" "com.njydsz.pmis.sales.domain.entity"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.entity.contract" "com.njydsz.pmis.sales.domain.entity"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.dto.opportunity" "com.njydsz.pmis.sales.domain.dto"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.dto.contract" "com.njydsz.pmis.sales.domain.dto"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.enums.opportunity" "com.njydsz.pmis.sales.domain.enums"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.enums.contract" "com.njydsz.pmis.sales.domain.enums"
# Update remaining project.entity/vo/vo references that are cross-domain
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.entity.execution" "com.njydsz.pmis.project.entity.execution"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.dto.execution" "com.njydsz.pmis.project.dto.execution"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.enums.execution" "com.njydsz.pmis.project.enums.execution"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.enums.common" "com.njydsz.pmis.project.enums.common"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.dto.common" "com.njydsz.pmis.project.dto.common"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.entity.initiation" "com.njydsz.pmis.project.entity.initiation"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.dto.initiation" "com.njydsz.pmis.project.dto.initiation"
Update-FilePackages "$salesSrc\domain" "com.njydsz.pmis.project.enums.initiation" "com.njydsz.pmis.project.enums.initiation"

Write-Host "`n=== Phase 3: Sales Infra Layer Migration ==="

# Sales Infra: Mapper
Copy-Files "$projectSrc\mapper\opportunity" "$salesSrc\infra\mapper"
Copy-Files "$projectSrc\mapper\contract" "$salesSrc\infra\mapper"

# Sales Infra: Mapper XML
New-Item -ItemType Directory -Force -Path "$salesRes\mapper\opportunity" | Out-Null
New-Item -ItemType Directory -Force -Path "$salesRes\mapper\contract" | Out-Null
Copy-Files "$projectRes\mapper\opportunity" "$salesRes\mapper\opportunity" "*.xml"
Copy-Files "$projectRes\mapper\contract" "$salesRes\mapper\contract" "*.xml"

# Update package names in sales infra files
Update-FilePackages "$salesSrc\infra" "com.njydsz.pmis.project.mapper.opportunity" "com.njydsz.pmis.sales.infra.mapper"
Update-FilePackages "$salesSrc\infra" "com.njydsz.pmis.project.mapper.contract" "com.njydsz.pmis.sales.infra.mapper"
Update-FilePackages "$salesSrc\infra" "com.njydsz.pmis.project.entity.opportunity" "com.njydsz.pmis.sales.domain.entity"
Update-FilePackages "$salesSrc\infra" "com.njydsz.pmis.project.entity.contract" "com.njydsz.pmis.sales.domain.entity"
Update-FilePackages "$salesSrc\infra" "com.njydsz.pmis.project.dto.opportunity" "com.njydsz.pmis.sales.domain.dto"
Update-FilePackages "$salesSrc\infra" "com.njydsz.pmis.project.dto.contract" "com.njydsz.pmis.sales.domain.dto"
Update-FilePackages "$salesSrc\infra" "com.njydsz.pmis.project.enums.opportunity" "com.njydsz.pmis.sales.domain.enums"
Update-FilePackages "$salesSrc\infra" "com.njydsz.pmis.project.enums.contract" "com.njydsz.pmis.sales.domain.enums"

Write-Host "`n=== Phase 4: Sales Server Layer Migration ==="

# Sales Server: Service interfaces
New-Item -ItemType Directory -Force -Path "$salesSrc\server\service\opportunity" | Out-Null
New-Item -ItemType Directory -Force -Path "$salesSrc\server\service\contract" | Out-Null
Copy-Files "$projectSrc\service\opportunity" "$salesSrc\server\service\opportunity"
Copy-Files "$projectSrc\service\contract" "$salesSrc\server\service\contract"

# Sales Server: Service impls
New-Item -ItemType Directory -Force -Path "$salesSrc\server\service\impl\opportunity" | Out-Null
New-Item -ItemType Directory -Force -Path "$salesSrc\server\service\impl\contract" | Out-Null
Copy-Files "$projectSrc\service\impl\opportunity" "$salesSrc\server\service\impl\opportunity"
Copy-Files "$projectSrc\service\impl\contract" "$salesSrc\server\service\impl\contract"

# Sales Server: Engine
Copy-Files "$projectSrc\engine" "$salesSrc\server\engine" "WinRateEvaluator.java"
Copy-Files "$projectSrc\engine" "$salesSrc\server\engine" "ContractRiskEvaluator.java"

# Update package names in sales server files
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.service.opportunity" "com.njydsz.pmis.sales.server.service.opportunity"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.service.contract" "com.njydsz.pmis.sales.server.service.contract"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.service.impl.opportunity" "com.njydsz.pmis.sales.server.service.impl.opportunity"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.service.impl.contract" "com.njydsz.pmis.sales.server.service.impl.contract"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.mapper.opportunity" "com.njydsz.pmis.sales.infra.mapper"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.mapper.contract" "com.njydsz.pmis.sales.infra.mapper"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.entity.opportunity" "com.njydsz.pmis.sales.domain.entity"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.entity.contract" "com.njydsz.pmis.sales.domain.entity"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.dto.opportunity" "com.njydsz.pmis.sales.domain.dto"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.dto.contract" "com.njydsz.pmis.sales.domain.dto"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.enums.opportunity" "com.njydsz.pmis.sales.domain.enums"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.enums.contract" "com.njydsz.pmis.sales.domain.enums"
Update-FilePackages "$salesSrc\server" "com.njydsz.pmis.project.engine" "com.njydsz.pmis.sales.server.engine"

Write-Host "`n=== Phase 5: Sales Web Layer Migration ==="

# Sales Web: Controllers
New-Item -ItemType Directory -Force -Path "$salesSrc\web\controller" | Out-Null
Copy-Files "$projectSrc\controller\opportunity" "$salesSrc\web\controller"
Copy-Files "$projectSrc\controller\contract" "$salesSrc\web\controller"

# Update package names in sales web files
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.controller.opportunity" "com.njydsz.pmis.sales.web.controller"
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.controller.contract" "com.njydsz.pmis.sales.web.controller"
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.service.opportunity" "com.njydsz.pmis.sales.server.service.opportunity"
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.service.contract" "com.njydsz.pmis.sales.server.service.contract"
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.dto.opportunity" "com.njydsz.pmis.sales.domain.dto"
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.dto.contract" "com.njydsz.pmis.sales.domain.dto"
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.dto.common" "com.njydsz.pmis.project.dto.common"
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.dto.report" "com.njydsz.pmis.project.dto.report"
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.enums.common" "com.njydsz.pmis.project.enums.common"
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.enums.opportunity" "com.njydsz.pmis.sales.domain.enums"
Update-FilePackages "$salesSrc\web" "com.njydsz.pmis.project.enums.contract" "com.njydsz.pmis.sales.domain.enums"

Write-Host "`n=== Finance Domain Layer Migration ==="

# Finance Domain: Entity
Copy-Files "$projectSrc\entity\finance" "$financeSrc\domain\entity"

# Finance Domain: DTO
Copy-Files "$projectSrc\dto\finance" "$financeSrc\domain\dto"

# Finance Domain: Enums
Copy-Files "$projectSrc\enums\finance" "$financeSrc\domain\enums"

# Update package names in finance domain files
Update-FilePackages "$financeSrc\domain" "com.njydsz.pmis.project.entity.finance" "com.njydsz.pmis.finance.domain.entity"
Update-FilePackages "$financeSrc\domain" "com.njydsz.pmis.project.dto.finance" "com.njydsz.pmis.finance.domain.dto"
Update-FilePackages "$financeSrc\domain" "com.njydsz.pmis.project.enums.finance" "com.njydsz.pmis.finance.domain.enums"
Update-FilePackages "$financeSrc\domain" "com.njydsz.pmis.project.enums.common" "com.njydsz.pmis.project.enums.common"
Update-FilePackages "$financeSrc\domain" "com.njydsz.pmis.project.dto.common" "com.njydsz.pmis.project.dto.common"
Update-FilePackages "$financeSrc\domain" "com.njydsz.pmis.project.entity.contract" "com.njydsz.pmis.project.entity.contract"
Update-FilePackages "$financeSrc\domain" "com.njydsz.pmis.project.entity.execution" "com.njydsz.pmis.project.entity.execution"
Update-FilePackages "$financeSrc\domain" "com.njydsz.pmis.project.enums.contract" "com.njydsz.pmis.project.enums.contract"
Update-FilePackages "$financeSrc\domain" "com.njydsz.pmis.project.enums.execution" "com.njydsz.pmis.project.enums.execution"

Write-Host "`n=== Finance Infra Layer Migration ==="

# Finance Infra: Mapper
Copy-Files "$projectSrc\mapper\finance" "$financeSrc\infra\mapper"

# Finance Infra: Mapper XML
New-Item -ItemType Directory -Force -Path "$financeRes\mapper\finance" | Out-Null
Copy-Files "$projectRes\mapper\finance" "$financeRes\mapper\finance" "*.xml"

# Update package names in finance infra files
Update-FilePackages "$financeSrc\infra" "com.njydsz.pmis.project.mapper.finance" "com.njydsz.pmis.finance.infra.mapper"
Update-FilePackages "$financeSrc\infra" "com.njydsz.pmis.project.entity.finance" "com.njydsz.pmis.finance.domain.entity"
Update-FilePackages "$financeSrc\infra" "com.njydsz.pmis.project.dto.finance" "com.njydsz.pmis.finance.domain.dto"
Update-FilePackages "$financeSrc\infra" "com.njydsz.pmis.project.enums.finance" "com.njydsz.pmis.finance.domain.enums"

Write-Host "`n=== Finance Server Layer Migration ==="

# Finance Server: Service interfaces
New-Item -ItemType Directory -Force -Path "$financeSrc\server\service\finance" | Out-Null
Copy-Files "$projectSrc\service\finance" "$financeSrc\server\service\finance"

# Finance Server: Service impls
New-Item -ItemType Directory -Force -Path "$financeSrc\server\service\impl\finance" | Out-Null
Copy-Files "$projectSrc\service\impl\finance" "$financeSrc\server\service\impl\finance"

# Finance Server: Engine
$engineFiles = @("ProfitCalculator.java", "ReconcileHandler.java", "ReconcileReport.java", "ReconcileResult.java", "AlertCodeGen.java")
foreach ($ef in $engineFiles) {
    Copy-Files "$projectSrc\engine" "$financeSrc\server\engine" $ef
}

# Finance Server: Job
Copy-Files "$projectSrc\job" "$financeSrc\server\job" "DailyReconcileJobHandler.java"

# Update package names in finance server files
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.service.finance" "com.njydsz.pmis.finance.server.service.finance"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.service.impl.finance" "com.njydsz.pmis.finance.server.service.impl.finance"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.mapper.finance" "com.njydsz.pmis.finance.infra.mapper"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.entity.finance" "com.njydsz.pmis.finance.domain.entity"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.dto.finance" "com.njydsz.pmis.finance.domain.dto"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.enums.finance" "com.njydsz.pmis.finance.domain.enums"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.enums.common" "com.njydsz.pmis.project.enums.common"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.dto.common" "com.njydsz.pmis.project.dto.common"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.engine" "com.njydsz.pmis.finance.server.engine"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.entity.execution" "com.njydsz.pmis.project.entity.execution"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.entity.contract" "com.njydsz.pmis.project.entity.contract"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.enums.execution" "com.njydsz.pmis.project.enums.execution"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.enums.contract" "com.njydsz.pmis.project.enums.contract"
Update-FilePackages "$financeSrc\server" "com.njydsz.pmis.project.dto.execution" "com.njydsz.pmis.project.dto.execution"

Write-Host "`n=== Finance Web Layer Migration ==="

# Finance Web: Controllers
Copy-Files "$projectSrc\controller\finance" "$financeSrc\web\controller"

# Update package names in finance web files
Update-FilePackages "$financeSrc\web" "com.njydsz.pmis.project.controller.finance" "com.njydsz.pmis.finance.web.controller"
Update-FilePackages "$financeSrc\web" "com.njydsz.pmis.project.service.finance" "com.njydsz.pmis.finance.server.service.finance"
Update-FilePackages "$financeSrc\web" "com.njydsz.pmis.project.dto.finance" "com.njydsz.pmis.finance.domain.dto"
Update-FilePackages "$financeSrc\web" "com.njydsz.pmis.project.dto.common" "com.njydsz.pmis.project.dto.common"
Update-FilePackages "$financeSrc\web" "com.njydsz.pmis.project.enums.finance" "com.njydsz.pmis.finance.domain.enums"
Update-FilePackages "$financeSrc\web" "com.njydsz.pmis.project.enums.common" "com.njydsz.pmis.project.enums.common"
Update-FilePackages "$financeSrc\web" "com.njydsz.pmis.project.entity.finance" "com.njydsz.pmis.finance.domain.entity"

Write-Host "`n=== Migration Script Complete ==="
Write-Host "Next: Create Application classes, bootstrap.yml, Feign clients, and delete moved files from project module"
