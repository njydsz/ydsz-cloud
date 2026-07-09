$srcDir = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-frontend\src"
$files = Get-ChildItem -Path $srcDir -Recurse -Include "*.ts","*.vue" | Select-Object -ExpandProperty FullName

# Fix import paths to match new directory structure
# These are import path fixes (not URL fixes) - must be precise to avoid breaking other strings
$importFixes = @(
    # rule-engine moved from execution/ to top-level
    @("@/api/execution/rule-engine", "@/api/rule-engine"),
    # utilization moved from execution/ to resource/
    @("@/api/execution/utilization", "@/api/resource/utilization"),
    # customer-credit moved from execution/ to finance/credit
    @("@/api/execution/customer-credit", "@/api/finance/credit"),
    # aftersales moved from execution/ to after-sales
    @("@/api/execution/aftersales", "@/api/after-sales"),
    # alert moved from execution/ to top-level
    @("@/api/execution/alert", "@/api/alert"),
    # report API moved from execution/report to report/base
    # The first script changed @/api/execution/report to @/api/report
    # Now we need to fix it to @/api/report/base (but not @/api/report/cockpit)
    @("from '@/api/report'", "from '@/api/report/base'"),
    @("from '@/api/report/types'", "from '@/api/report/base/types'"),
    # project/change moved to initiation/change
    @("@/api/project/change", "@/api/initiation/change")
)

$updatedCount = 0
foreach ($file in $files) {
    $content = Get-Content $file -Raw -Encoding UTF8
    $original = $content
    foreach ($r in $importFixes) {
        $content = $content.Replace($r[0], $r[1])
    }
    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($file, $content, [System.Text.Encoding]::UTF8)
        Write-Host "Fixed: $file"
        $updatedCount++
    }
}
Write-Host "Total files fixed: $updatedCount"
