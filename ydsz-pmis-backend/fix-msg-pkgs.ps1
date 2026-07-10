# 修复 message/service/impl/core 目录中文件的 package 声明
$files = @(
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message\service\impl\core\DedupServiceImpl.java"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message\service\impl\core\DeliveryTimeOptimizerImpl.java"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message\service\impl\core\MsgLogArchiveServiceImpl.java"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message\service\impl\core\RateLimitServiceImpl.java"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message\service\impl\core\ReachStrategyServiceImpl.java"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message\service\impl\core\RealtimeStatsService.java"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message\service\impl\core\RetryScanner.java"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message\service\impl\core\ScheduledMessageScanner.java"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message\service\impl\core\SmsProviderStrategyServiceImpl.java"
)

foreach ($path in $files) {
    if (!(Test-Path $path)) { Write-Host "SKIP: $path"; continue }
    $content = [System.IO.File]::ReadAllText($path)
    $content = $content.Replace("package com.njydsz.pmis.message.service.impl;", "package com.njydsz.pmis.message.service.impl.core;")
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "  Fixed: $(Split-Path $path -Leaf)"
}

# Also check other modules for the same issue
$allImplDirs = @(
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-message\src\main\java\com\njydsz\pmis\message\service\impl"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-userinfo\src\main\java\com\njydsz\pmis\userinfo\service\impl"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-cronjob\src\main\java\com\njydsz\pmis\cronjob\service\impl"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-agent\src\main\java\com\njydsz\pmis\agent\service\impl"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-system\src\main\java\com\njydsz\pmis\system\service\impl"
    "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-workflow\src\main\java\com\njydsz\pmis\workflow\service\impl"
)

$extraFixed = 0
foreach ($implDir in $allImplDirs) {
    if (!(Test-Path $implDir)) { continue }
    # Find module short name from path
    $modMatch = [regex]::Match($implDir, 'pmis\\([a-z]+)\\service')
    if (!$modMatch.Success) { continue }
    $short = $modMatch.Groups[1].Value
    
    $subDirs = Get-ChildItem -Path $implDir -Directory
    foreach ($subDir in $subDirs) {
        $domain = $subDir.Name
        $files = Get-ChildItem -Path $subDir.FullName -Filter "*.java"
        foreach ($f in $files) {
            $content = [System.IO.File]::ReadAllText($f.FullName)
            $oldPkg = "package com.njydsz.pmis.$short.service.impl;"
            $newPkg = "package com.njydsz.pmis.$short.service.impl.$domain;"
            if ($content.Contains($oldPkg)) {
                $content = $content.Replace($oldPkg, $newPkg)
                [System.IO.File]::WriteAllText($f.FullName, $content, (New-Object System.Text.UTF8Encoding($false)))
                $extraFixed++
                Write-Host "  Extra fix: $($f.Name) [$short/$domain]"
            }
        }
    }
}
Write-Host "`nExtra package fixes: $extraFixed"
