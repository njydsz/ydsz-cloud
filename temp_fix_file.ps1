# Fix specific file-level issues in common-file module
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
$base = 'D:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-file\src\main\java\com\njydsz\pmis\common\file'

# 1. FileConfiguration.java - remove health indicator bean method
$file = Join-Path $base 'config\FileConfiguration.java'
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
# Remove the health indicator bean method block
$content = $content -replace '(?s)\s*/\*\*\s*\* 创建文件存储健康检查指示器.*?return new FileHealthIndicator\(fileStorageProvider, fileProperties\);\s*\}\s*', "`r`n    // FileHealthIndicator removed — actuator is optional`r`n"
[System.IO.File]::WriteAllText($file, $content, $utf8NoBom)
Write-Output "Fixed FileConfiguration.java"

# 2. FileDedupService.java - replace RedisStringOps with StringRedisTemplate
$file = Join-Path $base 'service\FileDedupService.java'
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
$content = $content -replace 'import org\.springframework\.data\.redis\.core\.StringRedisTemplate;', 'import org.springframework.data.redis.core.StringRedisTemplate;'
$content = $content -replace 'RedisStringOps', 'StringRedisTemplate'
$content = $content -replace 'redisStringOps\.', 'stringRedisTemplate.'
$content = $content -replace 'private final StringRedisTemplate stringRedisTemplate;', 'private final StringRedisTemplate stringRedisTemplate;'
# Fix variable name
$content = $content -replace 'private final StringRedisTemplate redisStringOps;', 'private final StringRedisTemplate stringRedisTemplate;'
$content = $content -replace 'this\.redisStringOps', 'this.stringRedisTemplate'
$content = $content -replace 'redisStringOps\.', 'stringRedisTemplate.'
[System.IO.File]::WriteAllText($file, $content, $utf8NoBom)
Write-Output "Fixed FileDedupService.java"

# 3. FileStorage.java - replace FileTypeUtils with FileUtils
$file = Join-Path $base 'domain\FileStorage.java'
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
$content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.FileTypeUtils;', 'import com.njydsz.pmis.common.util.FileUtils;'
$content = $content -replace 'FileTypeUtils\.', 'FileUtils.'
[System.IO.File]::WriteAllText($file, $content, $utf8NoBom)
Write-Output "Fixed FileStorage.java"

# 4. LocalStorage.java - fix IOUtils import
$file = Join-Path $base 'storage\platform\LocalStorage.java'
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
$content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.io\.IOUtils;', 'import com.njydsz.pmis.common.util.IOUtils;'
[System.IO.File]::WriteAllText($file, $content, $utf8NoBom)
Write-Output "Fixed LocalStorage.java"

# 5. Fix all util.io imports across the module
$javaFiles = Get-ChildItem -Path $base -Recurse -Filter '*.java'
foreach ($jf in $javaFiles) {
    $content = [System.IO.File]::ReadAllText($jf.FullName, [System.Text.Encoding]::UTF8)
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.io\.IOUtils;', 'import com.njydsz.pmis.common.util.IOUtils;'
    $content = $content -replace 'import com\.njydsz\.pmis\.common\.util\.file\.\w+;', 'import com.njydsz.pmis.common.util.FileUtils;'
    [System.IO.File]::WriteAllText($jf.FullName, $content, $utf8NoBom)
}

Write-Output "Done fixing common-file"
