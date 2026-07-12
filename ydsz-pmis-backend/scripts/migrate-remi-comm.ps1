<#
.SYNOPSIS
  将 remi-comm 所有子模块（排除 gateway/job）一比一迁移到 ydsz-pmis-common。
.DESCRIPTION
  1. 复制 src/ 目录（排除 target/）
  2. 重命名 Java 目录结构: com/remisoft/comm/ -> com/njydsz/pmis/common/
  3. 重命名 native-image 目录
  4. 文本替换所有文件内容
  5. 生成新 pom.xml
#>

$ErrorActionPreference = "Stop"

$SRC_BASE = "D:\Code\remi\vip\platform\remi-comm"
$DST_BASE = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common"

# 需要迁移的子模块列表（排除 gateway 和 job）
$MODULES = @(
    "core",
    "util",
    "domain",
    "exception",
    "jdbc",
    "lock",
    "redis",
    "auth",
    "safe",
    "feign",
    "audit",
    "file",
    "notify",
    "queue",
    "doc",
    "base",
    "web",
    "app"
)

function Replace-FileContent {
    param([string]$filePath)

    $content = Get-Content $filePath -Raw -Encoding UTF8
    if ($null -eq $content) { return }

    $original = $content

    # 1. Java 包名替换（最优先，避免被后续替换破坏）
    $content = $content -replace 'com\.remisoft\.comm', 'com.njydsz.pmis.common'

    # 2. native-image 路径中的 com.remisoft.remi-comm-xxx
    $content = $content -replace 'com\.remisoft\.remi-comm', 'com.njydsz.pmis.ydsz-pmis-common'

    # 3. groupId 替换
    $content = $content -replace '<groupId>com\.remisoft</groupId>', '<groupId>com.njydsz.pmis</groupId>'

    # 4. artifactId 替换（remi-comm-xxx -> ydsz-pmis-common-xxx）
    $content = $content -replace '<artifactId>remi-comm-', '<artifactId>ydsz-pmis-common-'

    # 5. parent artifactId 替换（remi-comm -> ydsz-pmis-common）
    $content = $content -replace '<artifactId>remi-comm</artifactId>', '<artifactId>ydsz-pmis-common</artifactId>'

    # 6. 版本替换
    $content = $content -replace '4\.1\.0-SNAPSHOT', '1.0.0-SNAPSHOT'

    # 7. 描述/注释中的引用替换（remi-comm-xxx -> ydsz-pmis-common-xxx）
    $content = $content -replace 'remi-comm-', 'ydsz-pmis-common-'

    # 8. 描述中的 "remi-comm" 残留（如 "remi-comm 是..." -> "ydsz-pmis-common 是..."）
    $content = $content -replace 'remi-comm', 'ydsz-pmis-common'

    # 9. 瑞米 -> PMIS（描述文本）
    $content = $content -replace '瑞米软件', 'PMIS'
    $content = $content -replace '瑞米公共依赖', 'PMIS公共依赖'
    $content = $content -replace '瑞米', 'PMIS'

    # 10. RemiComm 类名前缀 -> PmisCommon（如果有）
    $content = $content -replace 'RemiComm', 'PmisCommon'

    # 11. Remi 前缀类名 -> Pmis（针对 RemiSchedulingAutoConfiguration 等）
    $content = $content -replace 'RemiScheduling', 'PmisScheduling'
    $content = $content -replace 'RemiGateway', 'PmisGateway'

    # 12. com.remisoft 残留（groupId 在 pom.xml 中的其他引用）
    $content = $content -replace 'com\.remisoft', 'com.njydsz.pmis'

    if ($content -ne $original) {
        Set-Content $filePath -Value $content -NoNewline -Encoding UTF8
    }
}

function Copy-ModuleSource {
    param([string]$moduleName)

    $srcModule = "$SRC_BASE\remi-comm-$moduleName"
    $dstModule = "$DST_BASE\ydsz-pmis-common-$moduleName"

    Write-Host "=== Migrating remi-comm-$moduleName -> ydsz-pmis-common-$moduleName ===" -ForegroundColor Cyan

    # 创建目标目录
    if (Test-Path $dstModule) {
        Remove-Item $dstModule -Recurse -Force
    }
    New-Item -ItemType Directory -Path $dstModule -Force | Out-Null

    # 复制 src/ 目录（排除 target/）
    $srcDir = "$srcModule\src"
    if (-not (Test-Path $srcDir)) {
        Write-Host "  [WARN] No src/ directory found in remi-comm-$moduleName, skipping source copy." -ForegroundColor Yellow
        return
    }

    # 递归复制 src/ 目录
    $dstSrc = "$dstModule\src"
    Copy-Item -Path $srcDir -Destination $dstSrc -Recurse -Force

    # 删除 target/ 目录（如果被复制）
    $targetDir = "$dstSrc\target"
    if (Test-Path $targetDir) {
        Remove-Item $targetDir -Recurse -Force
    }

    # 重命名 Java 目录结构: com/remisoft/comm/ -> com/njydsz/pmis/common/
    $javaBase = "$dstSrc\main\java"
    if (Test-Path "$javaBase\com\remisoft\comm") {
        # 创建新目录结构
        $newBase = "$javaBase\com\njydsz\pmis\common"
        New-Item -ItemType Directory -Path $newBase -Force | Out-Null

        # 移动所有子目录和文件
        $oldBase = "$javaBase\com\remisoft\comm"
        Get-ChildItem -Path $oldBase -Force | ForEach-Object {
            Move-Item $_.FullName "$newBase\" -Force
        }

        # 删除旧的空目录 com/remisoft/
        Remove-Item "$javaBase\com\remisoft" -Recurse -Force -ErrorAction SilentlyContinue
    }

    # 处理 test 目录
    $testBase = "$dstSrc\test\java"
    if (Test-Path "$testBase\com\remisoft\comm") {
        $newTestBase = "$testBase\com\njydsz\pmis\common"
        New-Item -ItemType Directory -Path $newTestBase -Force | Out-Null

        $oldTestBase = "$testBase\com\remisoft\comm"
        Get-ChildItem -Path $oldTestBase -Force | ForEach-Object {
            Move-Item $_.FullName "$newTestBase\" -Force
        }

        Remove-Item "$testBase\com\remisoft" -Recurse -Force -ErrorAction SilentlyContinue
    }

    # 重命名 native-image 目录
    # com.remisoft\remi-comm-core -> com.njydsz.pmis\ydsz-pmis-common-core
    Get-ChildItem -Path "$dstSrc" -Recurse -Directory -Filter "com.remisoft*" | ForEach-Object {
        $newName = $_.Name -replace 'com\.remisoft\.remi-comm', 'com.njydsz.pmis.ydsz-pmis-common' `
                              -replace 'com\.remisoft\.comm', 'com.njydsz.pmis.common' `
                              -replace 'com\.remisoft', 'com.njydsz.pmis'
        $newPath = Join-Path $_.Parent.FullName $newName
        Move-Item $_.FullName $newPath -Force
    }

    # 重命名 native-image 目录中的 remi-comm-* 子目录
    Get-ChildItem -Path "$dstSrc" -Recurse -Directory -Filter "remi-comm-*" | ForEach-Object {
        $newName = $_.Name -replace 'remi-comm-', 'ydsz-pmis-common-'
        $newPath = Join-Path $_.Parent.FullName $newName
        Move-Item $_.FullName $newPath -Force
    }

    # 替换所有文本文件内容
    $files = Get-ChildItem -Path $dstSrc -Recurse -File
    foreach ($file in $files) {
        Replace-FileContent $file.FullName
    }

    Write-Host "  Migrated $($files.Count) files." -ForegroundColor Green
}

# 执行迁移
foreach ($module in $MODULES) {
    Copy-ModuleSource $module
}

Write-Host "`n=== Migration complete! ===" -ForegroundColor Green
