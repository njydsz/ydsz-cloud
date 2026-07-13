# fix-fqn.ps1 — 批量修复行内 FQN 违规
# 读取 fqn-results.txt，对每个文件添加 import 并替换 FQN 为简单类名

$ErrorActionPreference = "Stop"

# 读取违规列表
$violationLines = Get-Content "fqn-results.txt" -Encoding utf8 | Where-Object { $_ -match '\.java:\d+:' }

# 按文件分组
$fileMap = @{}
foreach ($line in $violationLines) {
    if ($line -match '^(.+\.java):\d+:\s*(.*)$') {
        $file = $matches[1].Trim()
        $content = $matches[2]
        if (-not $fileMap[$file]) { $fileMap[$file] = @() }
        $fileMap[$file] += $content
    }
}

Write-Host "Found $($fileMap.Count) files with FQN violations"

# 特殊处理的文件（有名称冲突等）
$skipFiles = @(
    "SmartRoutingSelector.java"  # OperatingSystemMXBean 名称冲突，需要 FQN-OK
)

$fixedCount = 0
$skippedCount = 0

foreach ($file in $fileMap.Keys | Sort-Object) {
    $skip = $false
    foreach ($sf in $skipFiles) {
        if ($file -like "*$sf") { $skip = $true; break }
    }
    if ($skip) {
        Write-Host "SKIP (name conflict): $file"
        $skippedCount++
        continue
    }

    $lines = Get-Content $file -Encoding utf8
    if (-not $lines) { continue }

    # 收集需要导入的 FQN
    $importsToAdd = @{}  # FQN -> simpleName
    foreach ($content in $fileMap[$file]) {
        $fqnMatches = [regex]::Matches($content, '(com|org|java|javax|jakarta|net|io)\.[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*\.[A-Z][a-zA-Z0-9_]*')
        foreach ($m in $fqnMatches) {
            $fqn = $m.Value
            $simpleName = $fqn.Split('.')[-1]
            # 检查是否已 import
            $alreadyImported = $false
            foreach ($l in $lines) {
                if ($l -match "^import\s+$fqn\s*;") { $alreadyImported = $true; break }
            }
            if (-not $alreadyImported) {
                $importsToAdd[$fqn] = $simpleName
            }
        }
    }

    if ($importsToAdd.Count -eq 0) {
        # FQN 可能已经被 import 了，只需要替换
    }

    # 构建新文件内容
    $newLines = [System.Collections.ArrayList]::new()
    $importSectionEnd = -1
    $packageLine = -1

    # 找到 import 区域的末尾
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^package\s+') { $packageLine = $i }
        if ($lines[$i] -match '^import\s+') { $importSectionEnd = $i }
    }

    # 先处理替换，再添加 import
    $content = $lines -join "`n"

    # 对每个 FQN，替换为简单类名（不在 import 行和字符串字面量中）
    foreach ($fqn in $importsToAdd.Keys) {
        $simpleName = $importsToAdd[$fqn]
        # 替换 FQN 为简单类名，但不在 import 语句和字符串字面量中
        # 使用正则：匹配不在引号内且不在 import 行的 FQN
        $newLines = [System.Collections.ArrayList]::new()
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i]
            if ($line -match '^import\s+') {
                $newLines.Add($line)
                continue
            }
            # 移除字符串字面量，替换 FQN，再放回
            # 简单方法：按引号分段处理
            $parts = $line -split '(")'
            $inString = $false
            for ($j = 0; $j -lt $parts.Count; $j++) {
                if ($parts[$j] -eq '"') {
                    $inString = -not $inString
                    continue
                }
                if (-not $inString) {
                    # 在非字符串部分替换 FQN
                    $parts[$j] = $parts[$j] -replace [regex]::Escape($fqn), $simpleName
                }
            }
            $newLines.Add(($parts -join ''))
        }
        $lines = $newLines.ToArray()
    }

    # 重新查找 import 区域末尾（因为内容可能已变化）
    $importSectionEnd = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^import\s+') { $importSectionEnd = $i }
    }

    # 添加新的 import 语句
    $newImportLines = @()
    foreach ($fqn in ($importsToAdd.Keys | Sort-Object)) {
        $newImportLines += "import $fqn;"
    }

    if ($newImportLines.Count -gt 0 -and $importSectionEnd -ge 0) {
        # 在最后一个 import 之后添加
        $result = [System.Collections.ArrayList]::new()
        for ($i = 0; $i -le $importSectionEnd; $i++) {
            $result.Add($lines[$i])
        }
        foreach ($imp in $newImportLines) {
            $result.Add($imp)
        }
        for ($i = $importSectionEnd + 1; $i -lt $lines.Count; $i++) {
            $result.Add($lines[$i])
        }
        $lines = $result.ToArray()
    } elseif ($newImportLines.Count -gt 0 -and $packageLine -ge 0) {
        # 没有 import 区域，在 package 行之后添加
        $result = [System.Collections.ArrayList]::new()
        for ($i = 0; $i -le $packageLine; $i++) {
            $result.Add($lines[$i])
        }
        $result.Add("")  # 空行
        foreach ($imp in $newImportLines) {
            $result.Add($imp)
        }
        for ($i = $packageLine + 1; $i -lt $lines.Count; $i++) {
            $result.Add($lines[$i])
        }
        $lines = $result.ToArray()
    }

    # 写回文件
    $lines | Set-Content $file -Encoding utf8
    $fixedCount++
    Write-Host "FIXED: $file (added $($importsToAdd.Count) imports)"
}

Write-Host "`nSummary: $fixedCount files fixed, $skippedCount files skipped"
