# Move config classes from web module to server module
# Changes package from {service}.web.config to {service}.server.config
$ErrorActionPreference = "Stop"
$backend = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

$services = @("agent","cronjob","message","workflow","project","sales","finance","system","userinfo","literule")

Write-Host "========== Moving config files from web to server =========="

foreach ($svcName in $services) {
    $webConfigDir = "$backend\ydsz-pmis-$svcName\ydsz-pmis-$svcName-web\src\main\java\com\njydsz\pmis\$svcName\web\config"
    
    # Also check non-web/config path (due to migration script behavior)
    $altConfigDir = "$backend\ydsz-pmis-$svcName\ydsz-pmis-$svcName-web\src\main\java\com\njydsz\pmis\$svcName\config"
    
    $configDir = $null
    if (Test-Path $webConfigDir) {
        $configDir = $webConfigDir
    } elseif (Test-Path $altConfigDir) {
        $configDir = $altConfigDir
    }
    
    if (-not $configDir) {
        continue
    }
    
    $configFiles = Get-ChildItem $configDir -Filter "*.java" -ErrorAction SilentlyContinue
    if (-not $configFiles) {
        continue
    }
    
    Write-Host "  $svcName : Found $($configFiles.Count) config files"
    
    # Target directory in server module
    $serverConfigDir = "$backend\ydsz-pmis-$svcName\ydsz-pmis-$svcName-server\src\main\java\com\njydsz\pmis\$svcName\server\config"
    if (-not (Test-Path $serverConfigDir)) {
        New-Item -ItemType Directory -Force -Path $serverConfigDir | Out-Null
    }
    
    $oldPkg = "com.njydsz.pmis.$svcName.web.config"
    $newPkg = "com.njydsz.pmis.$svcName.server.config"
    
    foreach ($cf in $configFiles) {
        $content = [System.IO.File]::ReadAllText($cf.FullName)
        # Change package declaration
        $content = $content -replace "package $oldPkg;", "package $newPkg;"
        # Write to server module
        $dstPath = "$serverConfigDir\$($cf.Name)"
        [System.IO.File]::WriteAllText($dstPath, $content, [System.Text.UTF8Encoding]::new($false))
        # Delete from web module
        Remove-Item $cf.FullName -Force
        Write-Host "    Moved: $($cf.Name)"
    }
    
    # Clean up empty config directory
    $remaining = Get-ChildItem $configDir -ErrorAction SilentlyContinue
    if (-not $remaining) {
        Remove-Item $configDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    
    # Also clean up empty web directory if it exists
    $webDir = "$backend\ydsz-pmis-$svcName\ydsz-pmis-$svcName-web\src\main\java\com\njydsz\pmis\$svcName\web"
    if (Test-Path $webDir) {
        $webRemaining = Get-ChildItem $webDir -Recurse -ErrorAction SilentlyContinue
        if (-not $webRemaining) {
            Remove-Item $webDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

# Update imports across codebase
Write-Host "`n========== Updating imports =========="
$importReplacements = @()
foreach ($svcName in $services) {
    $oldPkg = "com.njydsz.pmis.$svcName.web.config"
    $newPkg = "com.njydsz.pmis.$svcName.server.config"
    $importReplacements += @{old=$oldPkg; new=$newPkg}
}

$javaFiles = Get-ChildItem -Path $backend -Filter "*.java" -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notlike "*\target\*" }
$updatedCount = 0

foreach ($jf in $javaFiles) {
    $content = [System.IO.File]::ReadAllText($jf.FullName)
    $original = $content
    $changed = $false

    foreach ($rep in $importReplacements) {
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
