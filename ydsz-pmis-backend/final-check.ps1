# 最终检查: 所有模块中还有哪些文件在旧目录中
$modules = @(
    @{base="d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-workflow\src\main\java\com\njydsz\pmis\workflow"; short="workflow"}
    @{base="d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message"; short="message"}
    @{base="d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-userinfo\src\main\java\com\njydsz\pmis\userinfo"; short="userinfo"}
    @{base="d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-cronjob\src\main\java\com\njydsz\pmis\cronjob"; short="cronjob"}
    @{base="d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-agent\src\main\java\com\njydsz\pmis\agent"; short="agent"}
    @{base="d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-system\src\main\java\com\njydsz\pmis\system"; short="system"}
)
$layers = @('controller', 'service', 'entity', 'dto', 'mapper', 'enums')
$total = 0
foreach ($mod in $modules) {
    foreach ($layer in $layers) {
        $dir = Join-Path $mod.base $layer
        if (!(Test-Path $dir)) { continue }
        $files = Get-ChildItem -Path $dir -Filter "*.java" | Where-Object { $_.Name -ne 'package-info.java' }
        if ($files.Count -gt 0) {
            Write-Host "[$($mod.short)/$layer] Leftover:"
            foreach ($f in $files) { Write-Host "  $($f.Name)"; $total++ }
        }
    }
    $implDir = Join-Path $mod.base "service\impl"
    if (Test-Path $implDir) {
        $files = Get-ChildItem -Path $implDir -Filter "*.java" | Where-Object { $_.Name -ne 'package-info.java' }
        if ($files.Count -gt 0) {
            Write-Host "[$($mod.short)/service/impl] Leftover:"
            foreach ($f in $files) { Write-Host "  $($f.Name)"; $total++ }
        }
    }
}
Write-Host "`nTotal leftover files: $total"
