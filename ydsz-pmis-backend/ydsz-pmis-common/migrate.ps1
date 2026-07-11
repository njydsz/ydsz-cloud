$ErrorActionPreference = "Continue"
$base = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common"
$src = "$base\src\main\java\com\njydsz\pmis\common"

function CopyPkg($pkgName, $destModule) {
    $srcPath = "$src\$pkgName"
    $destPath = "$base\$destModule\src\main\java\com\njydsz\pmis\common\$pkgName"
    if (Test-Path $srcPath) {
        New-Item -ItemType Directory -Force -Path $destPath | Out-Null
        Get-ChildItem -Path $srcPath -Recurse | ForEach-Object {
            $rel = $_.FullName.Substring($srcPath.Length + 1)
            if ($_.PSIsContainer) {
                New-Item -ItemType Directory -Force -Path "$destPath\$rel" | Out-Null
            } else {
                $targetDir = Split-Path "$destPath\$rel" -Parent
                New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
                Copy-Item -Path $_.FullName -Destination "$destPath\$rel" -Force
            }
        }
        Write-Output "  Copied $pkgName -> $destModule"
    } else {
        Write-Output "  SKIP $pkgName (not found)"
    }
}

# Core: api, constant, enums, exception, dto, search
Write-Output "=== Core ==="
CopyPkg "api" "ydsz-pmis-common-core"
CopyPkg "constant" "ydsz-pmis-common-core"
CopyPkg "enums" "ydsz-pmis-common-core"
CopyPkg "exception" "ydsz-pmis-common-core"
CopyPkg "dto" "ydsz-pmis-common-core"
CopyPkg "search" "ydsz-pmis-common-core"

# Util
Write-Output "=== Util ==="
CopyPkg "util" "ydsz-pmis-common-util"

# Domain: entity, mapper, imports
Write-Output "=== Domain ==="
CopyPkg "entity" "ydsz-pmis-common-domain"
CopyPkg "mapper" "ydsz-pmis-common-domain"
CopyPkg "imports" "ydsz-pmis-common-domain"

# Data: datasource, service, config, aspect(DistributedLock)
Write-Output "=== Data ==="
CopyPkg "datasource" "ydsz-pmis-common-data"
CopyPkg "service" "ydsz-pmis-common-data"
CopyPkg "config" "ydsz-pmis-common-data"
CopyPkg "aspect" "ydsz-pmis-common-data"

# Security: security, sensitive, token, permission, filter, interceptor, annotation, aspect
Write-Output "=== Security ==="
CopyPkg "security" "ydsz-pmis-common-security"
CopyPkg "sensitive" "ydsz-pmis-common-security"
CopyPkg "token" "ydsz-pmis-common-security"
CopyPkg "permission" "ydsz-pmis-common-security"
CopyPkg "filter" "ydsz-pmis-common-security"
CopyPkg "interceptor" "ydsz-pmis-common-security"
CopyPkg "annotation" "ydsz-pmis-common-security"
CopyPkg "aspect" "ydsz-pmis-common-security"

# Feign
Write-Output "=== Feign ==="
CopyPkg "feign" "ydsz-pmis-common-feign"

# Web: excel, health, log, annotation, aspect, config
Write-Output "=== Web ==="
CopyPkg "excel" "ydsz-pmis-common-web"
CopyPkg "health" "ydsz-pmis-common-web"
CopyPkg "log" "ydsz-pmis-common-web"
CopyPkg "annotation" "ydsz-pmis-common-web"
CopyPkg "aspect" "ydsz-pmis-common-web"
CopyPkg "config" "ydsz-pmis-common-web"

# Infra: ai, alert, canary, chaos, dag, event, featureflag, job, kms, migration, reconcile, sentry, tracing, tx, webhook, toggle, metrics, config
Write-Output "=== Infra ==="
CopyPkg "ai" "ydsz-pmis-common-infra"
CopyPkg "alert" "ydsz-pmis-common-infra"
CopyPkg "canary" "ydsz-pmis-common-infra"
CopyPkg "chaos" "ydsz-pmis-common-infra"
CopyPkg "dag" "ydsz-pmis-common-infra"
CopyPkg "event" "ydsz-pmis-common-infra"
CopyPkg "featureflag" "ydsz-pmis-common-infra"
CopyPkg "job" "ydsz-pmis-common-infra"
CopyPkg "kms" "ydsz-pmis-common-infra"
CopyPkg "migration" "ydsz-pmis-common-infra"
CopyPkg "reconcile" "ydsz-pmis-common-infra"
CopyPkg "sentry" "ydsz-pmis-common-infra"
CopyPkg "tracing" "ydsz-pmis-common-infra"
CopyPkg "tx" "ydsz-pmis-common-infra"
CopyPkg "webhook" "ydsz-pmis-common-infra"
CopyPkg "toggle" "ydsz-pmis-common-infra"
CopyPkg "metrics" "ydsz-pmis-common-infra"
CopyPkg "config" "ydsz-pmis-common-infra"
CopyPkg "annotation" "ydsz-pmis-common-infra"
CopyPkg "aspect" "ydsz-pmis-common-infra"

# CommonApplication to all
Write-Output "=== All ==="
$allBase = "$base\ydsz-pmis-common-all\src\main\java\com\njydsz\pmis\common"
New-Item -ItemType Directory -Force -Path $allBase | Out-Null
$appFile = "$src\CommonApplication.java"
if (Test-Path $appFile) {
    Copy-Item $appFile "$allBase\CommonApplication.java" -Force
    Write-Output "  Copied CommonApplication.java"
}

# Resources
Write-Output "=== Resources ==="
$resSrc = "$base\src\main\resources"
$allRes = "$base\ydsz-pmis-common-all\src\main\resources"
if (Test-Path $resSrc) {
    Get-ChildItem -Path $resSrc -Recurse | ForEach-Object {
        $rel = $_.FullName.Substring($resSrc.Length + 1)
        if ($_.PSIsContainer) {
            New-Item -ItemType Directory -Force -Path "$allRes\$rel" | Out-Null
        } else {
            $targetDir = Split-Path "$allRes\$rel" -Parent
            New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
            Copy-Item -Path $_.FullName -Destination "$allRes\$rel" -Force
        }
    }
    Write-Output "  Resources copied to common-all"
}

# Count
Write-Output "=== Summary ==="
$modules = @("ydsz-pmis-common-core","ydsz-pmis-common-util","ydsz-pmis-common-domain","ydsz-pmis-common-data","ydsz-pmis-common-security","ydsz-pmis-common-feign","ydsz-pmis-common-web","ydsz-pmis-common-infra","ydsz-pmis-common-all")
foreach($m in $modules){
    $count = (Get-ChildItem -Path "$base\$m\src" -Recurse -Filter "*.java" -ErrorAction SilentlyContinue | Measure-Object).Count
    Write-Output "  $m = $count java files"
}
