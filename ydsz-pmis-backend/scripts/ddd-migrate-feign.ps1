# Feign client migration: common/feign -> {service}/api/{client,fallback,dto}
$ErrorActionPreference = "Stop"
$backend = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$commonFeign = "$backend\ydsz-pmis-common\src\main\java\com\njydsz\pmis\common\feign"

# Mapping: filename -> @{service; subdir; newPackage; imports}
$migrations = @(
    # sales
    @{file="SalesDataClient.java"; svc="sales"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="SalesDataClientFallback.java"; svc="sales"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    # finance
    @{file="FinanceDataClient.java"; svc="finance"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="FinanceDataClientFallback.java"; svc="finance"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    # userinfo
    @{file="UserServiceClient.java"; svc="userinfo"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="UserServiceClientFallback.java"; svc="userinfo"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="BenchResourceClient.java"; svc="userinfo"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="BenchResourceClientFallback.java"; svc="userinfo"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="OrgQueryClient.java"; svc="userinfo"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="OrgQueryClientFallbackFactory.java"; svc="userinfo"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    # system
    @{file="ConfigClient.java"; svc="system"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="ConfigClientFallback.java"; svc="system"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    # project
    @{file="ProjectServiceClient.java"; svc="project"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="ProjectServiceClientFallback.java"; svc="project"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="ExecutionClient.java"; svc="project"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="ExecutionClientFallback.java"; svc="project"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="InitiationFeignClient.java"; svc="project"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="InitiationFeignClientFallbackFactory.java"; svc="project"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    # message
    @{file="MessageServiceClient.java"; svc="message"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="MessageServiceClientFallback.java"; svc="message"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="NotificationClient.java"; svc="message"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="NotificationClientFallback.java"; svc="message"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="MessageRequest.java"; svc="message"; subdir="dto"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="MessageResult.java"; svc="message"; subdir="dto"; oldPkg="com.njydsz.pmis.common.feign"}
    # message dto sub-package
    @{file="NotificationFeignDTO.java"; svc="message"; subdir="dto"; oldPkg="com.njydsz.pmis.common.feign.dto"}
    @{file="RealtimePushDTO.java"; svc="message"; subdir="dto"; oldPkg="com.njydsz.pmis.common.feign.dto"}
    # agent
    @{file="AgentClient.java"; svc="agent"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="AgentClientFallbackFactory.java"; svc="agent"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
    # workflow
    @{file="WorkflowServiceClient.java"; svc="workflow"; subdir="client"; oldPkg="com.njydsz.pmis.common.feign"}
    @{file="WorkflowServiceClientFallback.java"; svc="workflow"; subdir="fallback"; oldPkg="com.njydsz.pmis.common.feign"}
)

# Class name -> (service, subdir) for cross-references
$classMap = @{}
foreach ($m in $migrations) {
    $className = $m.file -replace '\.java$', ''
    $classMap[$className] = @{svc=$m.svc; subdir=$m.subdir}
}

Write-Host "========== Migrating Feign clients =========="

foreach ($m in $migrations) {
    $fileName = $m.file
    $svc = $m.svc
    $subdir = $m.subdir
    $oldPkg = $m.oldPkg
    $newPkg = "com.njydsz.pmis.$svc.api.$subdir"
    $className = $fileName -replace '\.java$', ''

    $srcPath = "$commonFeign\$fileName"
    if ($m.oldPkg -eq "com.njydsz.pmis.common.feign.dto") {
        $srcPath = "$commonFeign\dto\$fileName"
    }

    if (-not (Test-Path $srcPath)) {
        Write-Host "  SKIP (not found): $fileName"
        continue
    }

    $content = Get-Content $srcPath -Raw -Encoding UTF8

    # 1. Change package declaration
    $content = $content -replace "package $oldPkg;", "package $newPkg;"

    # 2. Add FeignClientConstants import (for Feign client interfaces and fallbacks that reference it)
    if ($content -match "FeignClientConstants") {
        if ($content -notmatch "import com\.njydsz\.pmis\.common\.feign\.FeignClientConstants;") {
            # Add after package line
            $content = $content -replace "(package $newPkg;)", "`$1`nimport com.njydsz.pmis.common.feign.FeignClientConstants;"
        }
    }

    # 3. For fallback classes, add import of the client interface
    if ($subdir -eq "fallback") {
        # Find which client interface this fallback implements/references
        # Pattern: implements XxxClient or fallbackFactory = XxxClient
        $clientClassName = $null
        if ($content -match "implements\s+(\w+Client)") {
            $clientClassName = $Matches[1]
        } elseif ($content -match "implements\s+(\w+FeignClient)") {
            $clientClassName = $Matches[1]
        } elseif ($className -match "(.+)Fallback") {
            $clientClassName = $Matches[1]
        } elseif ($className -match "(.+)FallbackFactory") {
            $clientClassName = $Matches[1]
        }

        if ($clientClassName -and $classMap.ContainsKey($clientClassName)) {
            $clientPkg = "com.njydsz.pmis.$($classMap[$clientClassName].svc).api.$($classMap[$clientClassName].subdir)"
            $importLine = "import $clientPkg.$clientClassName;"
            if ($content -notmatch [regex]::Escape($importLine)) {
                # Add after package line (or after FeignClientConstants import)
                if ($content -match "import com\.njydsz\.pmis\.common\.feign\.FeignClientConstants;") {
                    $content = $content -replace "(import com\.njydsz\.pmis\.common\.feign\.FeignClientConstants;)", "`$1`n$importLine"
                } else {
                    $content = $content -replace "(package $newPkg;)", "`$1`n$importLine"
                }
            }
        }
    }

    # 4. For client interfaces, add import of the fallback class
    if ($subdir -eq "client") {
        $fallbackClassName = $null
        if ($content -match "fallbackFactory\s*=\s*(\w+)\.class") {
            $fallbackClassName = $Matches[1]
        }

        if ($fallbackClassName -and $classMap.ContainsKey($fallbackClassName)) {
            $fbPkg = "com.njydsz.pmis.$($classMap[$fallbackClassName].svc).api.$($classMap[$fallbackClassName].subdir)"
            $importLine = "import $fbPkg.$fallbackClassName;"
            if ($content -notmatch [regex]::Escape($importLine)) {
                # Add after package line (or after FeignClientConstants import)
                if ($content -match "import com\.njydsz\.pmis\.common\.feign\.FeignClientConstants;") {
                    $content = $content -replace "(import com\.njydsz\.pmis\.common\.feign\.FeignClientConstants;)", "`$1`n$importLine"
                } else {
                    $content = $content -replace "(package $newPkg;)", "`$1`n$importLine"
                }
            }
        }
    }

    # 5. For NotificationClient/Fallback, update dto imports
    if ($className -match "Notification") {
        $content = $content -replace "import com\.njydsz\.pmis\.common\.feign\.dto\.NotificationFeignDTO;", "import com.njydsz.pmis.message.api.dto.NotificationFeignDTO;"
        $content = $content -replace "import com\.njydsz\.pmis\.common\.feign\.dto\.RealtimePushDTO;", "import com.njydsz.pmis.message.api.dto.RealtimePushDTO;"
    }

    # 6. Write to new location
    $dstDir = "$backend\ydsz-pmis-$svc\ydsz-pmis-$svc-api\src\main\java\com\njydsz\pmis\$svc\api\$subdir"
    if (-not (Test-Path $dstDir)) {
        New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
    }
    $dstPath = "$dstDir\$fileName"
    Set-Content -Path $dstPath -Value $content -Encoding UTF8 -NoNewline

    # 7. Delete original
    Remove-Item -Path $srcPath -Force

    Write-Host "  Migrated: $fileName -> $svc/api/$subdir"
}

# Delete empty dto directory
$dtoDir = "$commonFeign\dto"
if (Test-Path $dtoDir) {
    $remaining = Get-ChildItem $dtoDir -Filter "*.java" -ErrorAction SilentlyContinue
    if (-not $remaining) {
        Remove-Item $dtoDir -Recurse -Force
        Write-Host "  Removed empty dto directory"
    }
}

Write-Host "`n========== Updating imports across codebase =========="

# Build replacement map: old import -> new import
$importReplacements = @(
    @{old="com.njydsz.pmis.common.feign.SalesDataClient"; new="com.njydsz.pmis.sales.api.client.SalesDataClient"; cls="SalesDataClient"}
    @{old="com.njydsz.pmis.common.feign.SalesDataClientFallback"; new="com.njydsz.pmis.sales.api.fallback.SalesDataClientFallback"; cls="SalesDataClientFallback"}
    @{old="com.njydsz.pmis.common.feign.FinanceDataClient"; new="com.njydsz.pmis.finance.api.client.FinanceDataClient"; cls="FinanceDataClient"}
    @{old="com.njydsz.pmis.common.feign.FinanceDataClientFallback"; new="com.njydsz.pmis.finance.api.fallback.FinanceDataClientFallback"; cls="FinanceDataClientFallback"}
    @{old="com.njydsz.pmis.common.feign.UserServiceClient"; new="com.njydsz.pmis.userinfo.api.client.UserServiceClient"; cls="UserServiceClient"}
    @{old="com.njydsz.pmis.common.feign.UserServiceClientFallback"; new="com.njydsz.pmis.userinfo.api.fallback.UserServiceClientFallback"; cls="UserServiceClientFallback"}
    @{old="com.njydsz.pmis.common.feign.BenchResourceClient"; new="com.njydsz.pmis.userinfo.api.client.BenchResourceClient"; cls="BenchResourceClient"}
    @{old="com.njydsz.pmis.common.feign.BenchResourceClientFallback"; new="com.njydsz.pmis.userinfo.api.fallback.BenchResourceClientFallback"; cls="BenchResourceClientFallback"}
    @{old="com.njydsz.pmis.common.feign.OrgQueryClient"; new="com.njydsz.pmis.userinfo.api.client.OrgQueryClient"; cls="OrgQueryClient"}
    @{old="com.njydsz.pmis.common.feign.OrgQueryClientFallbackFactory"; new="com.njydsz.pmis.userinfo.api.fallback.OrgQueryClientFallbackFactory"; cls="OrgQueryClientFallbackFactory"}
    @{old="com.njydsz.pmis.common.feign.ConfigClient"; new="com.njydsz.pmis.system.api.client.ConfigClient"; cls="ConfigClient"}
    @{old="com.njydsz.pmis.common.feign.ConfigClientFallback"; new="com.njydsz.pmis.system.api.fallback.ConfigClientFallback"; cls="ConfigClientFallback"}
    @{old="com.njydsz.pmis.common.feign.ProjectServiceClient"; new="com.njydsz.pmis.project.api.client.ProjectServiceClient"; cls="ProjectServiceClient"}
    @{old="com.njydsz.pmis.common.feign.ProjectServiceClientFallback"; new="com.njydsz.pmis.project.api.fallback.ProjectServiceClientFallback"; cls="ProjectServiceClientFallback"}
    @{old="com.njydsz.pmis.common.feign.ExecutionClient"; new="com.njydsz.pmis.project.api.client.ExecutionClient"; cls="ExecutionClient"}
    @{old="com.njydsz.pmis.common.feign.ExecutionClientFallback"; new="com.njydsz.pmis.project.api.fallback.ExecutionClientFallback"; cls="ExecutionClientFallback"}
    @{old="com.njydsz.pmis.common.feign.InitiationFeignClient"; new="com.njydsz.pmis.project.api.client.InitiationFeignClient"; cls="InitiationFeignClient"}
    @{old="com.njydsz.pmis.common.feign.InitiationFeignClientFallbackFactory"; new="com.njydsz.pmis.project.api.fallback.InitiationFeignClientFallbackFactory"; cls="InitiationFeignClientFallbackFactory"}
    @{old="com.njydsz.pmis.common.feign.MessageServiceClient"; new="com.njydsz.pmis.message.api.client.MessageServiceClient"; cls="MessageServiceClient"}
    @{old="com.njydsz.pmis.common.feign.MessageServiceClientFallback"; new="com.njydsz.pmis.message.api.fallback.MessageServiceClientFallback"; cls="MessageServiceClientFallback"}
    @{old="com.njydsz.pmis.common.feign.NotificationClient"; new="com.njydsz.pmis.message.api.client.NotificationClient"; cls="NotificationClient"}
    @{old="com.njydsz.pmis.common.feign.NotificationClientFallback"; new="com.njydsz.pmis.message.api.fallback.NotificationClientFallback"; cls="NotificationClientFallback"}
    @{old="com.njydsz.pmis.common.feign.MessageRequest"; new="com.njydsz.pmis.message.api.dto.MessageRequest"; cls="MessageRequest"}
    @{old="com.njydsz.pmis.common.feign.MessageResult"; new="com.njydsz.pmis.message.api.dto.MessageResult"; cls="MessageResult"}
    @{old="com.njydsz.pmis.common.feign.dto.NotificationFeignDTO"; new="com.njydsz.pmis.message.api.dto.NotificationFeignDTO"; cls="NotificationFeignDTO"}
    @{old="com.njydsz.pmis.common.feign.dto.RealtimePushDTO"; new="com.njydsz.pmis.message.api.dto.RealtimePushDTO"; cls="RealtimePushDTO"}
    @{old="com.njydsz.pmis.common.feign.AgentClient"; new="com.njydsz.pmis.agent.api.client.AgentClient"; cls="AgentClient"}
    @{old="com.njydsz.pmis.common.feign.AgentClientFallbackFactory"; new="com.njydsz.pmis.agent.api.fallback.AgentClientFallbackFactory"; cls="AgentClientFallbackFactory"}
    @{old="com.njydsz.pmis.common.feign.WorkflowServiceClient"; new="com.njydsz.pmis.workflow.api.client.WorkflowServiceClient"; cls="WorkflowServiceClient"}
    @{old="com.njydsz.pmis.common.feign.WorkflowServiceClientFallback"; new="com.njydsz.pmis.workflow.api.fallback.WorkflowServiceClientFallback"; cls="WorkflowServiceClientFallback"}
)

# Find all Java files and update imports
$javaFiles = Get-ChildItem -Path $backend -Filter "*.java" -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notlike "*\target\*" }
$updatedCount = 0

foreach ($jf in $javaFiles) {
    $content = Get-Content $jf.FullName -Raw -Encoding UTF8
    $original = $content
    $changed = $false

    foreach ($rep in $importReplacements) {
        $oldPattern = "import $($rep.old);"
        $newPattern = "import $($rep.new);"
        if ($content -match [regex]::Escape($oldPattern)) {
            $content = $content -replace ([regex]::Escape($oldPattern)), $newPattern
            $changed = $true
        }
        # Also update javadoc {@link com.njydsz.pmis.common.feign.XxxClient}
        $oldLink = "com.njydsz.pmis.common.feign.$($rep.cls)"
        $newLink = $rep.new
        if ($content -match [regex]::Escape($oldLink)) {
            $content = $content -replace ([regex]::Escape($oldLink)), $newLink
            $changed = $true
        }
    }

    if ($changed) {
        Set-Content -Path $jf.FullName -Value $content -Encoding UTF8 -NoNewline
        $updatedCount++
    }
}

Write-Host "  Updated $updatedCount Java files with new imports"

# Verify remaining references
Write-Host "`n========== Verifying no stale references =========="
$staleCount = 0
foreach ($rep in $importReplacements) {
    $matches = Select-String -Path "$backend\**\*.java" -Pattern $rep.old -ErrorAction SilentlyContinue | Where-Object { $_.Path -notlike "*\target\*" }
    if ($matches) {
        foreach ($m in $matches) {
            Write-Host "  STALE: $($m.Path):$($m.LineNumber): $($m.Line.Trim())"
            $staleCount++
        }
    }
}
if ($staleCount -eq 0) {
    Write-Host "  No stale references found!"
} else {
    Write-Host "  Found $staleCount stale references (may be in comments/javadoc)"
}

Write-Host "`n========== Feign client migration complete! =========="
