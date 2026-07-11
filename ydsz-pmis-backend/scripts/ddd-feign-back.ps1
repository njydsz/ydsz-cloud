# Move Feign clients that common needs back to common
$ErrorActionPreference = "Stop"
$backend = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$commonFeign = "$backend\ydsz-pmis-common\src\main\java\com\njydsz\pmis\common\feign"

# Files to move back: (sourcePath, destPath, oldPackage, newPackage, className, fallbackClassName)
$moveBack = @(
    # message clients
    @{src="$backend\ydsz-pmis-message\ydsz-pmis-message-api\src\main\java\com\njydsz\pmis\message\api\client\MessageServiceClient.java"; dst="$commonFeign\MessageServiceClient.java"; oldPkg="com.njydsz.pmis.message.api.client"; newPkg="com.njydsz.pmis.common.feign"}
    @{src="$backend\ydsz-pmis-message\ydsz-pmis-message-api\src\main\java\com\njydsz\pmis\message\api\fallback\MessageServiceClientFallback.java"; dst="$commonFeign\MessageServiceClientFallback.java"; oldPkg="com.njydsz.pmis.message.api.fallback"; newPkg="com.njydsz.pmis.common.feign"}
    @{src="$backend\ydsz-pmis-message\ydsz-pmis-message-api\src\main\java\com\njydsz\pmis\message\api\client\NotificationClient.java"; dst="$commonFeign\NotificationClient.java"; oldPkg="com.njydsz.pmis.message.api.client"; newPkg="com.njydsz.pmis.common.feign"}
    @{src="$backend\ydsz-pmis-message\ydsz-pmis-message-api\src\main\java\com\njydsz\pmis\message\api\fallback\NotificationClientFallback.java"; dst="$commonFeign\NotificationClientFallback.java"; oldPkg="com.njydsz.pmis.message.api.fallback"; newPkg="com.njydsz.pmis.common.feign"}
    @{src="$backend\ydsz-pmis-message\ydsz-pmis-message-api\src\main\java\com\njydsz\pmis\message\api\dto\MessageRequest.java"; dst="$commonFeign\MessageRequest.java"; oldPkg="com.njydsz.pmis.message.api.dto"; newPkg="com.njydsz.pmis.common.feign"}
    @{src="$backend\ydsz-pmis-message\ydsz-pmis-message-api\src\main\java\com\njydsz\pmis\message\api\dto\MessageResult.java"; dst="$commonFeign\MessageResult.java"; oldPkg="com.njydsz.pmis.message.api.dto"; newPkg="com.njydsz.pmis.common.feign"}
    @{src="$backend\ydsz-pmis-message\ydsz-pmis-message-api\src\main\java\com\njydsz\pmis\message\api\dto\NotificationFeignDTO.java"; dst="$commonFeign\dto\NotificationFeignDTO.java"; oldPkg="com.njydsz.pmis.message.api.dto"; newPkg="com.njydsz.pmis.common.feign.dto"}
    @{src="$backend\ydsz-pmis-message\ydsz-pmis-message-api\src\main\java\com\njydsz\pmis\message\api\dto\RealtimePushDTO.java"; dst="$commonFeign\dto\RealtimePushDTO.java"; oldPkg="com.njydsz.pmis.message.api.dto"; newPkg="com.njydsz.pmis.common.feign.dto"}
    # system clients
    @{src="$backend\ydsz-pmis-system\ydsz-pmis-system-api\src\main\java\com\njydsz\pmis\system\api\client\ConfigClient.java"; dst="$commonFeign\ConfigClient.java"; oldPkg="com.njydsz.pmis.system.api.client"; newPkg="com.njydsz.pmis.common.feign"}
    @{src="$backend\ydsz-pmis-system\ydsz-pmis-system-api\src\main\java\com\njydsz\pmis\system\api\fallback\ConfigClientFallback.java"; dst="$commonFeign\ConfigClientFallback.java"; oldPkg="com.njydsz.pmis.system.api.fallback"; newPkg="com.njydsz.pmis.common.feign"}
)

Write-Host "========== Moving Feign clients back to common =========="

foreach ($m in $moveBack) {
    if (-not (Test-Path $m.src)) {
        Write-Host "  SKIP (not found): $($m.dst | Split-Path -Leaf)"
        continue
    }

    $content = [System.IO.File]::ReadAllText($m.src)

    # Change package
    $content = $content -replace "package $($m.oldPkg);", "package $($m.newPkg);"

    # Remove FeignClientConstants import (now same package)
    $content = $content -replace "import com\.njydsz\.pmis\.common\.feign\.FeignClientConstants;\r?\n", ""

    # Remove cross-package imports for fallback/client
    $content = $content -replace "import com\.njydsz\.pmis\.\w+\.api\.(client|fallback|dto)\.\w+;\r?\n", ""

    # Fix NotificationFeignDTO/RealtimePushDTO imports in NotificationClient/Fallback
    $content = $content -replace "import com\.njydsz\.pmis\.message\.api\.dto\.NotificationFeignDTO;", "import com.njydsz.pmis.common.feign.dto.NotificationFeignDTO;"
    $content = $content -replace "import com\.njydsz\.pmis\.message\.api\.dto\.RealtimePushDTO;", "import com.njydsz.pmis.common.feign.dto.RealtimePushDTO;"

    # Fix MessageRequest/MessageResult imports in MessageServiceClient/Fallback
    $content = $content -replace "import com\.njydsz\.pmis\.message\.api\.dto\.MessageRequest;", "import com.njydsz.pmis.common.feign.MessageRequest;"
    $content = $content -replace "import com\.njydsz\.pmis\.message\.api\.dto\.MessageResult;", "import com.njydsz.pmis.common.feign.MessageResult;"

    # Ensure dto directory exists
    $dstDir = Split-Path $m.dst -Parent
    if (-not (Test-Path $dstDir)) {
        New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
    }

    [System.IO.File]::WriteAllText($m.dst, $content, [System.Text.UTF8Encoding]::new($false))
    Remove-Item $m.src -Force
    Write-Host "  Moved back: $(Split-Path $m.dst -Leaf)"
}

# Restore dto/package-info.java
$dtoPkgInfo = "$commonFeign\dto\package-info.java"
if (-not (Test-Path $dtoPkgInfo)) {
    $pkgInfoContent = "package com.njydsz.pmis.common.feign.dto;"
    [System.IO.File]::WriteAllText($dtoPkgInfo, $pkgInfoContent, [System.Text.UTF8Encoding]::new($false))
}

# Update imports across codebase: revert message/system api imports back to common.feign
$revertMap = @(
    @{old="com.njydsz.pmis.message.api.client.MessageServiceClient"; new="com.njydsz.pmis.common.feign.MessageServiceClient"}
    @{old="com.njydsz.pmis.message.api.fallback.MessageServiceClientFallback"; new="com.njydsz.pmis.common.feign.MessageServiceClientFallback"}
    @{old="com.njydsz.pmis.message.api.client.NotificationClient"; new="com.njydsz.pmis.common.feign.NotificationClient"}
    @{old="com.njydsz.pmis.message.api.fallback.NotificationClientFallback"; new="com.njydsz.pmis.common.feign.NotificationClientFallback"}
    @{old="com.njydsz.pmis.message.api.dto.MessageRequest"; new="com.njydsz.pmis.common.feign.MessageRequest"}
    @{old="com.njydsz.pmis.message.api.dto.MessageResult"; new="com.njydsz.pmis.common.feign.MessageResult"}
    @{old="com.njydsz.pmis.message.api.dto.NotificationFeignDTO"; new="com.njydsz.pmis.common.feign.dto.NotificationFeignDTO"}
    @{old="com.njydsz.pmis.message.api.dto.RealtimePushDTO"; new="com.njydsz.pmis.common.feign.dto.RealtimePushDTO"}
    @{old="com.njydsz.pmis.system.api.client.ConfigClient"; new="com.njydsz.pmis.common.feign.ConfigClient"}
    @{old="com.njydsz.pmis.system.api.fallback.ConfigClientFallback"; new="com.njydsz.pmis.common.feign.ConfigClientFallback"}
)

Write-Host "`n========== Reverting imports across codebase =========="
$javaFiles = Get-ChildItem -Path $backend -Filter "*.java" -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notlike "*\target\*" }
$updatedCount = 0

foreach ($jf in $javaFiles) {
    $content = [System.IO.File]::ReadAllText($jf.FullName)
    $original = $content
    $changed = $false

    foreach ($rep in $revertMap) {
        $oldPattern = "import $($rep.old);"
        $newPattern = "import $($rep.new);"
        if ($content -match [regex]::Escape($oldPattern)) {
            $content = $content -replace ([regex]::Escape($oldPattern)), $newPattern
            $changed = $true
        }
        # Also update javadoc references
        if ($content -match [regex]::Escape($rep.old)) {
            $content = $content -replace ([regex]::Escape($rep.old)), $rep.new
            $changed = $true
        }
    }

    if ($changed) {
        [System.IO.File]::WriteAllText($jf.FullName, $content, [System.Text.UTF8Encoding]::new($false))
        $updatedCount++
    }
}

Write-Host "  Updated $updatedCount Java files"
Write-Host "`n========== Done! =========="
