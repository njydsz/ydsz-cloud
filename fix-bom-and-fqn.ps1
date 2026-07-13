# fix-bom-and-remaining-fqn.ps1
# 1. Remove BOM from all Java files in ydsz-pmis-backend
# 2. Fix remaining FQN violations

$ErrorActionPreference = "Stop"

# Step 1: Remove BOM from all Java files
Write-Host "=== Step 1: Removing BOM from Java files ==="
$bomCount = 0
Get-ChildItem -Path ydsz-pmis-backend -Recurse -Filter *.java | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        [System.IO.File]::WriteAllBytes($_.FullName, $bytes[3..($bytes.Length - 1)])
        $bomCount++
    }
}
Write-Host "Removed BOM from $bomCount files"

# Step 2: Fix remaining FQN violations
Write-Host "`n=== Step 2: Fixing remaining FQN violations ==="

# Define fixes as: file -> @{ fqn -> simpleName }
# These are FQNs that are already imported but still appear as FQN in the file
$fixes = @(
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-audit\src\main\java\com\njydsz\pmis\common\audit\annotation\EnableYdszAudit.java"; replacements = @("com.njydsz.pmis.common.audit.config.AuditAutoConfiguration=AuditAutoConfiguration") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-audit\src\main\java\com\njydsz\pmis\common\audit\core\AsyncAuditRecorder.java"; replacements = @("org.springframework.jdbc.core.JdbcTemplate=JdbcTemplate") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-audit\src\main\java\com\njydsz\pmis\common\audit\core\DisruptorAuditRecorder.java"; replacements = @("org.springframework.jdbc.core.JdbcTemplate=JdbcTemplate") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\auth\aspect\AuthColPermissionAspect.java"; replacements = @("com.njydsz.pmis.common.util.auth.RequestHolder=RequestHolder", "com.njydsz.pmis.common.util.auth.AuthInfoUtils=AuthInfoUtils") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\auth\context\ColumnPermissionContext.java"; replacements = @("com.njydsz.pmis.common.auth.model.ColumnPermissionInfo=ColumnPermissionInfo") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\auth\service\ColumnPermissionResolver.java"; replacements = @("com.njydsz.pmis.common.auth.model.ColumnScopeInfo=ColumnScopeInfo") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\auth\service\DataPermissionResolver.java"; replacements = @("com.njydsz.pmis.common.auth.model.DataScopeInfo=DataScopeInfo") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\auth\service\RbacUserInfoService.java"; replacements = @("com.njydsz.pmis.common.auth.model.UserInfo=UserInfo") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\auth\service\RolePermissionLoader.java"; replacements = @("com.njydsz.pmis.common.auth.model.RolePermissions=RolePermissions") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\auth\service\impl\RedisRoleDataPermissionResolver.java"; replacements = @("com.njydsz.pmis.common.auth.model.DataScopeInfo=DataScopeInfo") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-exception\src\main\java\com\njydsz\pmis\common\exception\custom\ValidationException.java"; replacements = @("com.njydsz.pmis.common.exception.code.UnifiedExceptionCode=UnifiedExceptionCode") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-exception\src\main\java\com\njydsz\pmis\common\exception\custom\YdszSecurityException.java"; replacements = @("com.njydsz.pmis.common.exception.code.UnifiedExceptionCode=UnifiedExceptionCode") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-exception\src\main\java\com\njydsz\pmis\common\exception\custom\YdszTimeoutException.java"; replacements = @("com.njydsz.pmis.common.exception.code.UnifiedExceptionCode=UnifiedExceptionCode") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-notify\src\main\java\com\njydsz\pmis\common\notify\annotation\EnableYdszNotify.java"; replacements = @("com.njydsz.pmis.common.notify.config.NotifyConfiguration=NotifyConfiguration") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-redis\src\main\java\com\njydsz\pmis\common\redis\service\RedisBloomFilter.java"; replacements = @("org.springframework.data.redis.core.RedisCallback=RedisCallback") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-safe\src\main\java\com\njydsz\pmis\common\safe\filter\CsrfFilter.java"; replacements = @("jakarta.servlet.http.Cookie=Cookie") }
    @{ file = "ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-socket\src\main\java\com\njydsz\pmis\common\socket\config\WebSocketAutoConfiguration.java"; replacements = @("com.njydsz.pmis.common.socket.cluster.WebSocketClusterPublisher=WebSocketClusterPublisher") }
)

foreach ($fix in $fixes) {
    $file = $fix.file
    $fullPath = Join-Path "d:\Code\ydsz\ydsz-pmis" $file
    if (-not (Test-Path $fullPath)) {
        Write-Host "NOT FOUND: $file"
        continue
    }
    $content = [System.IO.File]::ReadAllText($fullPath)
    $changed = $false
    foreach ($r in $fix.replacements) {
        $parts = $r -split '='
        $fqn = $parts[0]
        $simple = $parts[1]
        # Replace FQN with simple name, but not in import lines or string literals
        $lines = $content -split "`n"
        $newLines = @()
        foreach ($line in $lines) {
            if ($line -match '^\s*import\s+') {
                $newLines += $line
                continue
            }
            # Split by quotes to avoid replacing in string literals
            $parts2 = $line -split '(")'
            $inString = $false
            for ($j = 0; $j -lt $parts2.Count; $j++) {
                if ($parts2[$j] -eq '"') { $inString = -not $inString; continue }
                if (-not $inString) {
                    $newPart = $parts2[$j] -replace [regex]::Escape($fqn), $simple
                    if ($newPart -ne $parts2[$j]) { $changed = $true }
                    $parts2[$j] = $newPart
                }
            }
            $newLines += ($parts2 -join '')
        }
        $content = $newLines -join "`n"
    }
    if ($changed) {
        [System.IO.File]::WriteAllText($fullPath, $content, [System.Text.UTF8Encoding]::new($false))
        Write-Host "FIXED: $file"
    } else {
        Write-Host "NO CHANGE: $file"
    }
}

# Step 3: Fix SmartRoutingSelector.java (name conflict - add FQN-OK comment)
Write-Host "`n=== Step 3: Fix SmartRoutingSelector (name conflict) ==="
$smartFile = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-cronjob\ydsz-pmis-cronjob-server\src\main\java\com\njydsz\pmis\cronjob\server\core\SmartRoutingSelector.java"
if (Test-Path $smartFile) {
    $content = [System.IO.File]::ReadAllText($smartFile)
    # Check if FQN-OK comments already exist
    if ($content -notmatch 'FQN-OK') {
        $content = $content -replace 'com\.sun\.management\.OperatingSystemMXBean osBean =', 'com.sun.management.OperatingSystemMXBean osBean = // FQN-OK: name conflict with java.lang.management.OperatingSystemMXBean'
        $content = $content -replace '\(com\.sun\.management\.OperatingSystemMXBean\)', '(com.sun.management.OperatingSystemMXBean) // FQN-OK: name conflict with java.lang.management.OperatingSystemMXBean'
        [System.IO.File]::WriteAllText($smartFile, $content, [System.Text.UTF8Encoding]::new($false))
        Write-Host "FIXED: SmartRoutingSelector.java (added FQN-OK comments)"
    } else {
        Write-Host "ALREADY FIXED: SmartRoutingSelector.java"
    }
}

Write-Host "`n=== Done ==="
