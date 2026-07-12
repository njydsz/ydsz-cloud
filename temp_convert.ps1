param(
    [Parameter(Mandatory=$true)]
    [string]$sourceModule,
    
    [Parameter(Mandatory=$true)]
    [string]$targetModule,
    
    [Parameter(Mandatory=$true)]
    [string]$packageMapping
)

# Source and target base paths
$sourceBase = "D:\Code\remi\vip\platform\remi-comm\$sourceModule\src\main"
$targetBase = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\$targetModule\src\main"

# Parse package mapping (format: "oldPkg1=newPkg1;oldPkg2=newPkg2")
$mappings = @{}
$packageMapping -split ';' | ForEach-Object {
    $parts = $_ -split '='
    if ($parts.Count -eq 2) {
        $mappings[$parts[0].Trim()] = $parts[1].Trim()
    }
}

# Additional text replacements
$textReplacements = @(
    @{ old = 'remi.notify'; new = 'pmis.notify' },
    @{ old = 'remi.file'; new = 'pmis.file' },
    @{ old = 'remi.mq'; new = 'pmis.mq' },
    @{ old = 'remi.queue'; new = 'pmis.queue' },
    @{ old = 'remi.lock'; new = 'pmis.lock' },
    @{ old = 'remi.redis'; new = 'pmis.redis' },
    @{ old = 'remi.jdbc'; new = 'pmis.jdbc' },
    @{ old = 'remi.audit'; new = 'pmis.audit' },
    @{ old = 'remi.auth'; new = 'pmis.auth' },
    @{ old = 'remi.safe'; new = 'pmis.safe' },
    @{ old = 'remi.feign'; new = 'pmis.feign' },
    @{ old = 'remi.doc'; new = 'pmis.doc' },
    @{ old = 'remi.base'; new = 'pmis.base' },
    @{ old = 'remi.web'; new = 'pmis.web' },
    @{ old = 'remi.app'; new = 'pmis.app' },
    @{ old = 'BaseResponse'; new = 'Result' },
    @{ old = 'JsonUtils.getMapper()'; new = 'JsonUtils.getObjectMapper()' },
    @{ old = '@author Marvin Lee'; new = '@author ydsz-pmis-team' },
    @{ old = '@email limw1888@126.com'; new = '' },
    @{ old = '@version 3.5.0'; new = '@since 1.0.0' },
    @{ old = '@version 4.0.0'; new = '@since 1.0.0' },
    @{ old = '@since 3.5.0'; new = '@since 1.0.0' },
    @{ old = 'com.remisoft.comm.util.auth.RemiAuthInfo'; new = 'com.njydsz.pmis.common.base.auth.BaseAuthInfo' },
    @{ old = 'com.remisoft.comm.util.auth.RequestHolder'; new = 'com.njydsz.pmis.common.context.RequestContext' },
    @{ old = 'RequestHolder.set'; new = 'RequestContext.set' },
    @{ old = 'RequestHolder.get'; new = 'RequestContext.get' },
    @{ old = 'RequestHolder.clear'; new = 'RequestContext.clear' },
    @{ old = 'RequestHolder.remove'; new = 'RequestContext.clear' },
    @{ old = 'com.remisoft.comm.core.trace.TraceIdGenerator'; new = 'com.njydsz.pmis.common.util.TraceIdUtil' },
    @{ old = 'TraceIdGenerator.generate()'; new = 'TraceIdUtil.generate()' },
    @{ old = 'com.remisoft.comm.core.context.RequestContext'; new = 'com.njydsz.pmis.common.context.RequestContext' },
    @{ old = 'com.remisoft.comm.core.constant.HeaderConstants'; new = 'com.njydsz.pmis.common.constant.HeaderConstants' },
    @{ old = 'com.remisoft.comm.util.url.UrlPathUtils'; new = 'com.njydsz.pmis.common.util.UrlPathUtils' },
    @{ old = 'UrlPathUtils.matchAny'; new = 'PathMatcherUtil.matchAny' },
    @{ old = 'com.remisoft.comm.util.http.ServletUtils'; new = 'com.njydsz.pmis.common.util.HttpUtils' },
    @{ old = 'com.remisoft.comm.util.string.StringUtils'; new = 'com.njydsz.pmis.common.util.StringUtils' },
    @{ old = 'com.remisoft.comm.util.json.JsonUtils'; new = 'com.njydsz.pmis.common.util.JsonUtils' },
    @{ old = 'EnableRemiNotify'; new = 'EnablePmisNotify' },
    @{ old = 'EnableRemiFile'; new = 'EnablePmisFile' },
    @{ old = 'EnableRemiMq'; new = 'EnablePmisMq' }
)

# UTF8 without BOM
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# Counter
$fileCount = 0

# Process Java files
$javaPath = Join-Path $sourceBase "java"
if (Test-Path $javaPath) {
    $files = Get-ChildItem -Path $javaPath -Recurse -Filter '*.java'
    foreach ($f in $files) {
        $content = Get-Content $f.FullName -Raw -Encoding UTF8
        
        # Remove BOM if present
        if ($content.StartsWith([char]0xFEFF)) {
            $content = $content.Substring(1)
        }
        
        # Apply package mapping
        foreach ($key in $mappings.Keys) {
            $content = $content -replace [regex]::Escape($key), $mappings[$key]
        }
        
        # Apply text replacements
        foreach ($r in $textReplacements) {
            $content = $content -replace [regex]::Escape($r.old), $r.new
        }
        
        # Calculate target path - map the directory structure too
        $relPath = $f.FullName.Substring($javaPath.Length + 1)
        
        # Replace path segments based on package mapping
        $mappedPath = $relPath
        foreach ($key in $mappings.Keys) {
            $oldPath = $key -replace '\.', '\'
            $newPath = $mappings[$key] -replace '\.', '\'
            $mappedPath = $mappedPath -replace [regex]::Escape($oldPath), $newPath
        }
        
        $targetPath = Join-Path (Join-Path $targetBase "java") $mappedPath
        
        # Create directory if needed
        $targetDir = Split-Path $targetPath -Parent
        if (!(Test-Path $targetDir)) {
            New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        }
        
        # Write file without BOM
        [System.IO.File]::WriteAllText($targetPath, $content, $utf8NoBom)
        $fileCount++
    }
}

# Process resource files
$resPath = Join-Path $sourceBase "resources"
if (Test-Path $resPath) {
    $resFiles = Get-ChildItem -Path $resPath -Recurse -File
    foreach ($f in $resFiles) {
        $content = Get-Content $f.FullName -Raw -Encoding UTF8
        
        # Remove BOM if present
        if ($content -and $content.StartsWith([char]0xFEFF)) {
            $content = $content.Substring(1)
        }
        
        # Apply package mapping
        foreach ($key in $mappings.Keys) {
            $content = $content -replace [regex]::Escape($key), $mappings[$key]
        }
        
        # Apply text replacements for config files
        foreach ($r in $textReplacements) {
            $content = $content -replace [regex]::Escape($r.old), $r.new
        }
        
        # Calculate target path
        $relPath = $f.FullName.Substring($resPath.Length + 1)
        $mappedPath = $relPath
        foreach ($key in $mappings.Keys) {
            $oldPath = $key -replace '\.', '\'
            $newPath = $mappings[$key] -replace '\.', '\'
            $mappedPath = $mappedPath -replace [regex]::Escape($oldPath), $newPath
        }
        
        $targetPath = Join-Path (Join-Path $targetBase "resources") $mappedPath
        
        # Create directory if needed
        $targetDir = Split-Path $targetPath -Parent
        if (!(Test-Path $targetDir)) {
            New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        }
        
        # Write file without BOM
        [System.IO.File]::WriteAllText($targetPath, $content, $utf8NoBom)
        $fileCount++
    }
}

Write-Output "Converted $fileCount files from $sourceModule to $targetModule"
