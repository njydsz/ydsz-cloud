$file = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-execution\src\test\java\com\njydsz\pmis\execution\service\impl\CockpitReportServiceImplTest.java'
$content = Get-Content $file -Raw
$content = $content -replace 'com\.njydsz\.pmis\.execution\.dto\.CockpitAlertSummaryVO', 'CockpitAlertSummaryVO'
$content = $content -replace 'com\.njydsz\.pmis\.execution\.dto\.ProjectGroupKpiDTO', 'ProjectGroupKpiDTO'
$content = $content -replace 'com\.njydsz\.pmis\.execution\.dto\.ExecutiveOverviewVO', 'ExecutiveOverviewVO'
$content = $content -replace 'com\.njydsz\.pmis\.execution\.dto\.KpiTrendVO', 'KpiTrendVO'
Set-Content -Path $file -Value $content -NoNewline
Write-Output "Done"
