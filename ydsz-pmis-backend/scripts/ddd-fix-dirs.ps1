# Fix directory structure: move files from .../{service}/ to .../{service}/{module}/
$ErrorActionPreference = "Stop"
$backend = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

$services = @("sales","finance","system","userinfo","cronjob","message","agent","project","literule","workflow")
$modules = @("api","domain","infra","server","web")

Write-Host "========== Fixing directory structure =========="

foreach ($svcName in $services) {
    foreach ($mod in $modules) {
        $modBase = "$backend\ydsz-pmis-$svcName\ydsz-pmis-$svcName-$mod\src\main\java\com\njydsz\pmis\$svcName"
        $targetDir = "$modBase\$mod"

        if (-not (Test-Path $modBase)) {
            continue
        }

        # Get all items directly under {service}/ (not in {module}/ subdirectory)
        $items = Get-ChildItem -Path $modBase -Force | Where-Object { $_.Name -ne $mod -and $_.Name -ne "package-info.java" }

        if (-not $items) {
            # Check for package-info.java
            $pkgInfo = "$modBase\package-info.java"
            if (Test-Path $pkgInfo) {
                $items = @([System.IO.FileInfo]::new($pkgInfo))
            } else {
                continue
            }
        }

        # Create target directory
        if (-not (Test-Path $targetDir)) {
            New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
        }

        $movedCount = 0
        foreach ($item in $items) {
            $dest = Join-Path $targetDir $item.Name
            if (Test-Path $dest) {
                # Merge - copy contents
                if ($item.PSIsContainer) {
                    Get-ChildItem -Path $item.FullName -Recurse -Force | ForEach-Object {
                        $relPath = $_.FullName.Substring($item.FullName.Length + 1)
                        $targetPath = Join-Path $dest $relPath
                        $targetDir2 = Split-Path $targetPath -Parent
                        if (-not (Test-Path $targetDir2)) {
                            New-Item -ItemType Directory -Force -Path $targetDir2 | Out-Null
                        }
                        if (-not $_.PSIsContainer -and -not (Test-Path $targetPath)) {
                            Copy-Item -Path $_.FullName -Destination $targetPath -Force
                        }
                    }
                } else {
                    Copy-Item -Path $item.FullName -Destination $dest -Force
                }
                Remove-Item -Path $item.FullName -Recurse -Force
            } else {
                Move-Item -Path $item.FullName -Destination $dest -Force
            }
            $movedCount++
        }

        if ($movedCount -gt 0) {
            Write-Host "  $svcName/$mod : moved $movedCount items"
        }
    }
}

Write-Host "`n========== Done! =========="
