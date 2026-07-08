$dir = 'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-agent\src'
$bad = @()
Get-ChildItem -Path $dir -Recurse -Filter '*.java' | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    for ($i = 0; $i -lt $bytes.Length; $i++) {
        if ($bytes[$i] -eq 0x1A) {
            $bad += $_.FullName
            break
        }
    }
}
$bad | ForEach-Object { Write-Host $_ }
Write-Host ('Total: ' + $bad.Count)
