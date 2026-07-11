# DDD 五层拆分文件迁移脚本
# 将每个服务从单模块结构迁移到 api/domain/infra/server/web 五个子模块
$ErrorActionPreference = "Stop"

$backend = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

$services = @(
    @{name="sales"; pkg="sales"},
    @{name="finance"; pkg="finance"},
    @{name="system"; pkg="system"},
    @{name="userinfo"; pkg="userinfo"},
    @{name="cronjob"; pkg="cronjob"},
    @{name="message"; pkg="message"},
    @{name="agent"; pkg="agent"},
    @{name="project"; pkg="project"},
    @{name="literule"; pkg="literule"},
    @{name="workflow"; pkg="workflow"}
)

foreach ($svc in $services) {
    $svcName = $svc.name
    $pkgName = $svc.pkg
    $svcDir = "$backend\ydsz-pmis-$svcName"
    $srcJavaBase = "$svcDir\src\main\java\com\njydsz\pmis\$pkgName"
    $srcResources = "$svcDir\src\main\resources"
    $srcTest = "$svcDir\src\test"
    
    Write-Host "========== Processing: ydsz-pmis-$svcName =========="
    
    # Define sub-module directories
    $modules = @("api", "domain", "infra", "server", "web")
    
    # Step 1: Create sub-module directory structure
    foreach ($mod in $modules) {
        $modJavaBase = "$svcDir\ydsz-pmis-$svcName-$mod\src\main\java\com\njydsz\pmis\$pkgName"
        New-Item -ItemType Directory -Force -Path $modJavaBase | Out-Null
        if ($mod -eq "infra" -or $mod -eq "server" -or $mod -eq "web") {
            New-Item -ItemType Directory -Force -Path "$svcDir\ydsz-pmis-$svcName-$mod\src\main\resources" | Out-Null
        }
    }
    
    # Step 2: Move Java files for each DDD layer
    foreach ($mod in $modules) {
        $srcPath = "$srcJavaBase\$mod"
        $dstBase = "$svcDir\ydsz-pmis-$svcName-$mod\src\main\java\com\njydsz\pmis\$pkgName"
        
        if (Test-Path $srcPath) {
            # Copy all contents
            Get-ChildItem -Path $srcPath -Force | ForEach-Object {
                $dest = Join-Path $dstBase $_.Name
                if (Test-Path $dest) {
                    # Merge directories
                    Get-ChildItem -Path $_.FullName -Recurse -Force | ForEach-Object {
                        $relPath = $_.FullName.Substring($srcPath.Length + 1)
                        $targetPath = Join-Path $dstBase $relPath
                        $targetDir = Split-Path $targetPath -Parent
                        if (-not (Test-Path $targetDir)) {
                            New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
                        }
                        if (-not (Test-Path $targetPath)) {
                            Copy-Item -Path $_.FullName -Destination $targetPath -Force
                        }
                    }
                } else {
                    Copy-Item -Path $_.FullName -Destination $dest -Recurse -Force
                }
            }
            # Remove source after copy
            Remove-Item -Path $srcPath -Recurse -Force
        }
    }
    
    # Step 3: Move package-info.java to domain module
    $pkgInfo = "$srcJavaBase\package-info.java"
    if (Test-Path $pkgInfo) {
        $domainBase = "$svcDir\ydsz-pmis-$svcName-domain\src\main\java\com\njydsz\pmis\$pkgName"
        Copy-Item -Path $pkgInfo -Destination "$domainBase\package-info.java" -Force
        Remove-Item -Path $pkgInfo -Force
    }
    
    # Step 4: Move resources
    # 4a: mapper/ -> infra module
    $mapperSrc = "$srcResources\mapper"
    if (Test-Path $mapperSrc) {
        $mapperDst = "$svcDir\ydsz-pmis-$svcName-infra\src\main\resources\mapper"
        if (Test-Path $mapperDst) {
            Get-ChildItem -Path $mapperSrc -Recurse -Force | ForEach-Object {
                $relPath = $_.FullName.Substring($mapperSrc.Length + 1)
                $targetPath = Join-Path $mapperDst $relPath
                $targetDir = Split-Path $targetPath -Parent
                if (-not (Test-Path $targetDir)) {
                    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
                }
                if (-not (Test-Path $targetPath) -and -not $_.PSIsContainer) {
                    Copy-Item -Path $_.FullName -Destination $targetPath -Force
                }
            }
        } else {
            Copy-Item -Path $mapperSrc -Destination $mapperDst -Recurse -Force
        }
        Remove-Item -Path $mapperSrc -Recurse -Force
    }
    
    # 4b: META-INF/ -> server module
    $metaInfSrc = "$srcResources\META-INF"
    if (Test-Path $metaInfSrc) {
        $metaInfDst = "$svcDir\ydsz-pmis-$svcName-server\src\main\resources\META-INF"
        if (Test-Path $metaInfDst) {
            Get-ChildItem -Path $metaInfSrc -Recurse -Force | ForEach-Object {
                $relPath = $_.FullName.Substring($metaInfSrc.Length + 1)
                $targetPath = Join-Path $metaInfDst $relPath
                $targetDir = Split-Path $targetPath -Parent
                if (-not (Test-Path $targetDir)) {
                    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
                }
                if (-not (Test-Path $targetPath) -and -not $_.PSIsContainer) {
                    Copy-Item -Path $_.FullName -Destination $targetPath -Force
                }
            }
        } else {
            Copy-Item -Path $metaInfSrc -Destination $metaInfDst -Recurse -Force
        }
        Remove-Item -Path $metaInfSrc -Recurse -Force
    }
    
    # 4c: Everything else -> web module
    if (Test-Path $srcResources) {
        $webResources = "$svcDir\ydsz-pmis-$svcName-web\src\main\resources"
        Get-ChildItem -Path $srcResources -Force | ForEach-Object {
            $dest = Join-Path $webResources $_.Name
            if (-not (Test-Path $dest)) {
                Copy-Item -Path $_.FullName -Destination $dest -Recurse -Force
            }
        }
    }
    
    # Step 5: Move test files to web module
    if (Test-Path $srcTest) {
        $webTest = "$svcDir\ydsz-pmis-$svcName-web\src\test"
        if (-not (Test-Path $webTest)) {
            Copy-Item -Path $srcTest -Destination $webTest -Recurse -Force
        }
    }
    
    # Step 6: Clean up old src directory
    Remove-Item -Path "$svcDir\src" -Recurse -Force -ErrorAction SilentlyContinue
    
    # Step 7: Count files per sub-module
    foreach ($mod in $modules) {
        $modDir = "$svcDir\ydsz-pmis-$svcName-$mod"
        $count = (Get-ChildItem -Path $modDir -Filter "*.java" -Recurse -ErrorAction SilentlyContinue | Measure-Object).Count
        Write-Host "  $mod : $count java files"
    }
    
    Write-Host ""
}

Write-Host "========== File migration complete! =========="
