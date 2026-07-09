$srcDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-frontend\src"
$files = Get-ChildItem -Path $srcDir -Recurse -Include "*.ts","*.vue" | Select-Object -ExpandProperty FullName

# Order matters: longer/more specific patterns first to avoid substring conflicts
$replacements = @(
    @('/execution/billable-utilization', '/resource/utilization'),
    @('/execution/profit-simulation', '/finance/profit-simulation'),
    @('/execution/daily-reconcile', '/finance/daily-reconcile'),
    @('/execution/advanced-report', '/report/advanced'),
    @('/execution/report-export', '/report/export'),
    @('/execution/cockpit', '/report/cockpit'),
    @('/execution/report', '/report'),
    @('/execution/export', '/report/async-export'),
    @('/execution/rules/dashboard', '/rule-engine/dashboard'),
    @('/execution/rules/cep', '/rule-engine/cep'),
    @('/execution/rules/breakpoints', '/rule-engine/breakpoints'),
    @('/execution/rule-variables', '/rule-engine/variables'),
    @('/execution/rules', '/rule-engine/rules'),
    @('/execution/rate-card', '/resource/rate-card'),
    @('/execution/rate-internal', '/resource/rate-internal'),
    @('/execution/revenue', '/finance/revenue'),
    @('/execution/expense', '/finance/expense'),
    @('/execution/invoice', '/finance/invoice'),
    @('/execution/payment', '/finance/payment'),
    @('/execution/profit', '/finance/profit'),
    @('/execution/reconcile', '/finance/reconcile'),
    @('/execution/credit', '/finance/credit'),
    @('/execution/closure', '/closure'),
    @('/execution/warranty', '/after-sales/warranty'),
    @('/execution/ops-ticket', '/after-sales/ops-ticket'),
    @('/execution/satisfaction', '/after-sales/satisfaction'),
    @('/execution/search', '/search'),
    @('/execution/alert-dispatch', '/alert-dispatch'),
    @('/execution/aggregate', '/aggregate'),
    @('/project/opportunity', '/opportunity'),
    @('/project/initiation', '/initiation'),
    @('/project/change', '/initiation/change'),
    @('/project/contract', '/contract')
)

$updatedCount = 0
foreach ($file in $files) {
    $content = Get-Content $file -Raw -Encoding UTF8
    $original = $content
    foreach ($r in $replacements) {
        $content = $content.Replace($r[0], $r[1])
    }
    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($file, $content, [System.Text.Encoding]::UTF8)
        Write-Host "Updated: $file"
        $updatedCount++
    }
}
Write-Host "Total files updated: $updatedCount"
