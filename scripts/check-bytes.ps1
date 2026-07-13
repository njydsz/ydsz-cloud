# Check and fix BOM in Java files
 param([string]$Dir)
 Get-ChildItem -Path $Dir -Recurse -Filter '*.java' | ForEach-Object {
     $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
     if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
         $content = [System.IO.File]::ReadAllText($_.FullName)
         $content = $content.Substring(1)
         [System.IO.File]::WriteAllText($_.FullName, $content, [System.Text.UTF8Encoding]::new($false))
         Write-Host "BOM removed: $($_.FullName)"
     }
 }
 Write-Host "Done"
