$srcBase = "D:\Code\remi\org\platform\remi-json-dev"
$dstBase = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-json"

# Source directories to process (excluding benchmarks)
$sourceDirs = @(
    @{Path="$srcBase\remi-json-annotations\src\main\java\com\remisoft\json"; IsTest=$false},
    @{Path="$srcBase\remi-json-core\src\main\java\com\remisoft\json"; IsTest=$false},
    @{Path="$srcBase\remi-json-core\src\test\java\com\remisoft\json"; IsTest=$true},
    @{Path="$srcBase\remi-json-spring\src\main\java\com\remisoft\json\spring"; IsTest=$false; SubDir="spring"},
    @{Path="$srcBase\remi-json-spring-boot-starter\src\main\java\com\remisoft\json\spring\boot"; IsTest=$false; SubDir="spring\boot"}
)

# Also copy the autoconfiguration imports file
$autoConfigSrc = "$srcBase\remi-json-spring-boot-starter\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports"
if (Test-Path $autoConfigSrc) {
    $autoConfigContent = Get-Content $autoConfigSrc -Raw -Encoding UTF8
    $autoConfigContent = $autoConfigContent -replace 'com\.remisoft\.json', 'com.njydsz.pmis.common.json'
    $autoConfigContent = $autoConfigContent -replace 'RemiJson', 'YdszJson'
    $autoConfigDst = "$dstBase\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports"
    Set-Content -Path $autoConfigDst -Value $autoConfigContent -Encoding UTF8 -NoNewline
    Write-Output "Copied autoconfiguration imports file"
}

$fileCount = 0
foreach ($src in $sourceDirs) {
    $srcPath = $src.Path
    if (-not (Test-Path $srcPath)) { continue }
    
    $files = Get-ChildItem -Path $srcPath -Recurse -Filter "*.java"
    foreach ($file in $files) {
        $relPath = $file.FullName.Substring($srcPath.Length).TrimStart('\')
        $testDir = if ($src.IsTest) { 'test' } else { 'main' }
        if ($src.ContainsKey("SubDir")) {
            $targetPath = Join-Path $dstBase "src\$testDir\java\com\njydsz\pmis\common\json\$($src.SubDir)\$relPath"
        } else {
            $targetPath = Join-Path $dstBase "src\$testDir\java\com\njydsz\pmis\common\json\$relPath"
        }
        
        # Create target directory
        $targetDir = Split-Path $targetPath -Parent
        New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
        
        # Read content
        $content = Get-Content $file.FullName -Raw -Encoding UTF8
        
        # Apply replacements
        # 1. Package name
        $content = $content -replace 'com\.remisoft\.json', 'com.njydsz.pmis.common.json'
        # 2. Class name prefix: RemiJson -> YdszJson
        $content = $content -replace 'RemiJson', 'YdszJson'
        # 3. RemiSerializer -> YdszSerializer
        $content = $content -replace 'RemiSerializer', 'YdszSerializer'
        # 4. RemiDeserializer -> YdszDeserializer
        $content = $content -replace 'RemiDeserializer', 'YdszDeserializer'
        # 5. RemiSerialization -> YdszSerialization
        $content = $content -replace 'RemiSerialization', 'YdszSerialization'
        # 6. RemiDeserialization -> YdszDeserialization
        $content = $content -replace 'RemiDeserialization', 'YdszDeserialization'
        # 7. Config prefix remi.json -> pmis.json (in strings)
        $content = $content -replace '"remi\.json', '"pmis.json'
        $content = $content -replace "'remi\.json", "'pmis.json"
        # 8. System property remi.json -> pmis.json
        $content = $content -replace 'remi\.json\.monitoring', 'pmis.json.monitoring'
        # 9. Brand references
        $content = $content -replace 'remi-json', 'ydsz-json'
        $content = $content -replace 'RemiJson v3', 'YdszJson v3'
        
        # Rename file if needed
        $fileName = $file.Name -replace 'RemiJson', 'YdszJson'
        $fileName = $fileName -replace 'RemiSerializer', 'YdszSerializer'
        $fileName = $fileName -replace 'RemiDeserializer', 'YdszDeserializer'
        $fileName = $fileName -replace 'RemiSerialization', 'YdszSerialization'
        $fileName = $fileName -replace 'RemiDeserialization', 'YdszDeserialization'
        $targetPath = Join-Path $targetDir $fileName
        
        # Write content
        Set-Content -Path $targetPath -Value $content -Encoding UTF8 -NoNewline
        $fileCount++
    }
}
Write-Output "Copied and transformed $fileCount Java files"
