# 修复 application.yml 的脚本：处理 file-extension: yml 与缺失 namespace 的情况
$ErrorActionPreference = "Stop"

$svcs = @("gateway","auth","user","notification","workflow","project",
          "execution","agent","config","file","audit","message","scheduler")
$BasePath = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

$fixedCount = 0
foreach ($svc in $svcs) {
    $file = Join-Path $BasePath "ydsz-pmis-$svc\src\main\resources\application.yml"
    if (-not (Test-Path $file)) { continue }

    $content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
    $original = $content

    # 1) 若 file-extension 为 yml，则注入 namespace 与 group
    if ($content -notmatch '(?m)^\s*group:\s*PMIS_GROUP_') {
        # 在 file-extension: yml 行之后插入 namespace 和 group
        $content = [regex]::Replace($content,
            '^(?<indent>\s*)file-extension:\s*yml\s*$',
            ('${indent}file-extension: yml' + "`n" +
             '${indent}group: PMIS_GROUP_${spring.profiles.active}' + "`n" +
             '${indent}namespace: ${NACOS_NAMESPACE:pmis}'),
            [System.Text.RegularExpressions.RegexOptions]::Multiline)

        # 若是 yaml 但仍没有 namespace
        if ($content -notmatch '(?m)^\s*group:\s*PMIS_GROUP_') {
            $content = [regex]::Replace($content,
                '^(?<indent>\s*)file-extension:\s*yaml\s*$',
                ('${indent}file-extension: yaml' + "`n" +
                 '${indent}group: PMIS_GROUP_${spring.profiles.active}' + "`n" +
                 '${indent}namespace: ${NACOS_NAMESPACE:pmis}'),
                [System.Text.RegularExpressions.RegexOptions]::Multiline)
        }
    }

    # 2) 给 discovery 加 namespace（如果还没有）
    if ($content -match 'discovery:\s*\n\s*server-addr:' -and $content -notmatch '(?ms)discovery:.*?namespace:') {
        $content = [regex]::Replace($content,
            '(?ms)(discovery:\s*\n\s*server-addr:[^\n]+)',
            ('$1' + "`n        namespace: `${NACOS_NAMESPACE:pmis}"))
    }

    # 3) 通用 namespace: ${NACOS_NAMESPACE:...} -> ${NACOS_NAMESPACE:pmis}
    $content = [regex]::Replace($content, '\$\{NACOS_NAMESPACE(:[^}]*)?\}', '${NACOS_NAMESPACE:pmis}')

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($file, $content, [System.Text.UTF8Encoding]::new($false))
        $fixedCount++
        Write-Host "  [FIXED] $svc\application.yml" -ForegroundColor Green
    } else {
        Write-Host "  [SKIP]  $svc\application.yml (无需修改)"
    }
}
Write-Host ""
Write-Host "Fixed $fixedCount application.yml files" -ForegroundColor Cyan
