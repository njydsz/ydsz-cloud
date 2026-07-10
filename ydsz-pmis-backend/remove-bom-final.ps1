# 移除所有重构模块中的 UTF-8 BOM
$modules = @(
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-workflow\src"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-userinfo\src"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-cronjob\src"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-agent\src"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-system\src"
)
$count = 0
foreach ($path in $modules) {
    if (!(Test-Path $path)) { continue }
    $files = Get-ChildItem -Path $path -Recurse -Filter "*.java"
    foreach ($f in $files) {
        $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
        if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
            $content = [System.IO.File]::ReadAllText($f.FullName)
            if ($content.Length -gt 0 -and $content[0] -eq [char]0xFEFF) {
                $content = $content.Substring(1)
            }
            [System.IO.File]::WriteAllText($f.FullName, $content, (New-Object System.Text.UTF8Encoding($false)))
            $count++
            Write-Host "  Removed BOM: $($f.FullName.Substring($path.Length))"
        }
    }
}
Write-Host "`nTotal BOM removed: $count"
