# Post-conversion fix-up script for all converted modules
# Fixes common issues across all modules

$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# Find all Java files in common-* modules
$modulesRoot = 'D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common'
$javaFiles = Get-ChildItem -Path $modulesRoot -Recurse -Filter '*.java' -ErrorAction SilentlyContinue

$fixCount = 0

foreach ($file in $javaFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $original = $content
    
    # 1. Fix ExceptionCode import: exception.enums.ExceptionCode -> exception.code.ExceptionCode
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.exception\.enums\.ExceptionCode;', 'import com.njydsz.pmis.common.exception.code.ExceptionCode;'
    
    # 2. Fix BusinessException import: exception.custom.BusinessException -> exception.BizException
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.exception\.custom\.BusinessException;', 'import com.njydsz.pmis.common.exception.BizException;'
    $content = $content -replace 'BusinessException', 'BizException'
    
    # 3. Fix util sub-package imports
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.json\.JsonUtils;', 'import com.njydsz.pmis.common.util.JsonUtils;'
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.concurrent\.ExecutorUtils;', 'import com.njydsz.pmis.common.util.ExecutorUtils;'
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.string\.StringUtils;', 'import com.njydsz.pmis.common.util.StringUtils;'
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.file\.\w+;', 'import com.njydsz.pmis.common.util.FileUtils;'
    
    # 4. Remove health indicator imports (since we removed those files)
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.\w+\.health\.\w+HealthIndicator;\r?\n', ''
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.\w+\.health\.\w+StorageHealthIndicator;\r?\n', ''
    
    # 5. Comment out health indicator bean registrations
    # (These need manual fixing - just remove the import for now)
    
    # 6. Fix redis.service.ops imports - replace with StringRedisTemplate
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.redis\.service\.ops\.\w+;', 'import org.springframework.data.redis.core.StringRedisTemplate;'
    
    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
        $fixCount++
    }
}

Write-Output "Fixed $fixCount files"

# Rename EnableRemi*.java files to Enable*.java
$enableRemiFiles = Get-ChildItem -Path $modulesRoot -Recurse -Filter 'EnableRemi*.java' -ErrorAction SilentlyContinue
foreach ($ef in $enableRemiFiles) {
    $newName = $ef.Name -replace 'EnableRemi', 'Enable'
    $newPath = Join-Path $ef.Directory.FullName $newName
    $content = [System.IO.File]::ReadAllText($ef.FullName, [System.Text.Encoding]::UTF8)
    [System.IO.File]::WriteAllText($newPath, $content, $utf8NoBom)
    Remove-Item $ef.FullName -Force
    Write-Output "Renamed: $($ef.Name) -> $newName"
}

Write-Output "`nDone!"
