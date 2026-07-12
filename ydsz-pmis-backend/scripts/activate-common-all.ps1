<#
.SYNOPSIS
  将 server 模块的 common-web 依赖替换为 common-all，激活全部公共能力
#>

$backendRoot = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

$poms = Get-ChildItem -Path $backendRoot -Recurse -Filter "pom.xml" |
    Where-Object { $_.FullName -match "\\ydsz-pmis-.*-server\\pom\.xml" }

$count = 0
foreach ($pom in $poms) {
    $content = Get-Content $pom.FullName -Raw -Encoding UTF8
    $modified = $false

    if ($content -match 'ydsz-pmis-common-web') {
        $content = $content -replace '<artifactId>ydsz-pmis-common-web</artifactId>', '<artifactId>ydsz-pmis-common-all</artifactId>'
        $modified = $true
    }

    if ($modified) {
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($pom.FullName, $content, $utf8NoBom)
        $name = $pom.FullName.Substring($backendRoot.Length + 1)
        Write-Host "  Updated: $name"
        $count++
    }
}

Write-Host "`nDone! Updated $count pom files."
