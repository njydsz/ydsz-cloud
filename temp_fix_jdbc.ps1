# Fix package paths in common-jdbc module
$targetRoot = "D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-jdbc\src\main\java"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$files = Get-ChildItem -Path $targetRoot -Recurse -Filter '*.java'
$fixCount = 0

foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $modified = $false

    # Fix 1: domain.entity -> entity (BaseEntity, BaseAuditEntity, BaseIdEntity)
    if ($content -match 'com\.njydsz\.pmis\.common\.domain\.entity') {
        $content = $content -replace 'com\.njydsz\.pmis\.common\.domain\.entity', 'com.njydsz.pmis.common.entity'
        $modified = $true
    }

    # Fix 2: RemiSecurityException -> SecurityException
    if ($content -match 'RemiSecurityException') {
        $content = $content -replace 'RemiSecurityException', 'SecurityException'
        $modified = $true
    }

    # Fix 3: exception.core.ExceptionInfo -> exception.ExceptionInfo
    if ($content -match 'com\.njydsz\.pmis\.common\.exception\.core\.ExceptionInfo') {
        $content = $content -replace 'com\.njydsz\.pmis\.common\.exception\.core\.ExceptionInfo', 'com.njydsz.pmis.common.exception.ExceptionInfo'
        $modified = $true
    }

    # Fix 4: core.response.BaseResponse -> api.Result
    if ($content -match 'com\.njydsz\.pmis\.common\.core\.response\.BaseResponse') {
        $content = $content -replace 'import com\.njydsz\.pmis\.common\.core\.response\.BaseResponse;', 'import com.njydsz.pmis.common.api.Result;'
        $content = $content -replace '\bBaseResponse\b', 'Result'
        $modified = $true
    }

    # Fix 5: util.auth -> security (AuthInfo, AuthInfoUtils, RequestHolder)
    if ($content -match 'com\.njydsz\.pmis\.common\.util\.auth\.') {
        $content = $content -replace 'com\.njydsz\.pmis\.common\.util\.auth\.', 'com.njydsz.pmis.common.security.'
        $modified = $true
    }

    # Fix 6: core.constant -> constant (HeaderConstants)
    if ($content -match 'com\.njydsz\.pmis\.common\.core\.constant\.') {
        $content = $content -replace 'com\.njydsz\.pmis\.common\.core\.constant\.', 'com.njydsz.pmis.common.constant.'
        $modified = $true
    }

    # Fix 7: core.enums -> enums (DataScopeType, IdentityType, etc.)
    if ($content -match 'com\.njydsz\.pmis\.common\.core\.enums\.') {
        $content = $content -replace 'com\.njydsz\.pmis\.common\.core\.enums\.', 'com.njydsz.pmis.common.enums.'
        $modified = $true
    }

    # Fix 8: util.http.ServletUtils -> util.ServletUtils
    if ($content -match 'com\.njydsz\.pmis\.common\.util\.http\.ServletUtils') {
        $content = $content -replace 'com\.njydsz\.pmis\.common\.util\.http\.ServletUtils', 'com.njydsz.pmis.common.util.ServletUtils'
        $modified = $true
    }

    # Fix 9: exception.core -> exception (for any remaining core references)
    if ($content -match 'com\.njydsz\.pmis\.common\.exception\.core\.') {
        $content = $content -replace 'com\.njydsz\.pmis\.common\.exception\.core\.', 'com.njydsz.pmis.common.exception.'
        $modified = $true
    }

    # Fix 10: Remove HealthIndicator files (actuator optional)
    if ($file.Name -match 'HealthIndicator') {
        Remove-Item $file.FullName -Force
        Write-Output "  Removed: $($file.Name) (actuator dependency)"
        continue
    }

    if ($modified) {
        [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
        $fixCount++
        Write-Output "Fixed: $($file.Name)"
    }
}

Write-Output "Total files fixed: $fixCount"
