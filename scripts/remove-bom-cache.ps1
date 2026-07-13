# Remove BOM from a specific file
 param(
     [string]$FilePath
 )
 $content = [System.IO.File]::ReadAllText($FilePath)
 if ($content.Length -gt 0 -and $content[0] -eq [char]0xFEFF) {
     $content = $content.Substring(1)
     [System.IO.File]::WriteAllText($FilePath, $content, [System.Text.UTF8Encoding]::new($false))
     Write-Host "BOM removed from: $FilePath"
 } else {
     Write-Host "No BOM found in: $FilePath"
 }
