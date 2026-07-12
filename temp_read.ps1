param([string]$module)
$basePath = "D:\Code\remi\vip\platform\remi-comm\$module\src\main"
$output = ""

# Read Java files
$javaPath = Join-Path $basePath "java"
if (Test-Path $javaPath) {
    $files = Get-ChildItem -Path $javaPath -Recurse -Filter '*.java'
    foreach ($f in $files) {
        $rel = $f.FullName.Substring($javaPath.Length + 1)
        $output += "=== FILE: $rel ===" + [Environment]::NewLine
        $output += Get-Content $f.FullName -Raw
        $output += [Environment]::NewLine + [Environment]::NewLine
    }
}

# Read resource files
$resPath = Join-Path $basePath "resources"
if (Test-Path $resPath) {
    $resFiles = Get-ChildItem -Path $resPath -Recurse -File
    foreach ($f in $resFiles) {
        $rel = $f.FullName.Substring($resPath.Length + 1)
        $output += "=== RESOURCE: $rel ===" + [Environment]::NewLine
        $output += Get-Content $f.FullName -Raw
        $output += [Environment]::NewLine + [Environment]::NewLine
    }
}

$outFile = "d:\Code\ydsz\ydsz-pmis\temp_remi_$($module -replace 'remi-comm-','').txt"
Set-Content -Path $outFile -Value $output -Encoding UTF8
Write-Output "Written to $outFile"
