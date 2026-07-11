# Fix all domain pom.xml files with garbled descriptions and common dependency
$ErrorActionPreference = "Stop"
$backend = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

$services = @("sales","finance","system","userinfo","cronjob","message","agent","project","literule","workflow")

# Fix domain pom.xml files
foreach ($svcName in $services) {
    $pomPath = "$backend\ydsz-pmis-$svcName\ydsz-pmis-$svcName-domain\pom.xml"
    if (-not (Test-Path $pomPath)) { continue }
    
    # Read as bytes and decode ignoring bad chars
    $bytes = [System.IO.File]::ReadAllBytes($pomPath)
    $content = [System.Text.Encoding]::UTF8.GetString($bytes)
    
    # Replace everything between <description> and </description> (or up to the next tag)
    $content = $content -replace '<description>[^<]*(?:<(?!/description>)[^<]*)*/description>', '<description>Domain Layer</description>'
    # Also handle case where </description> is missing (garbled)
    $content = $content -replace '<description>[^\n]*\n', "<description>Domain Layer</description>`n"
    
    # Replace ydsz-pmis-common with ydsz-pmis-common-all (if not the parent artifactId)
    $content = $content -replace '<artifactId>ydsz-pmis-common</artifactId></dependency>', '<artifactId>ydsz-pmis-common-all</artifactId></dependency>'
    
    [System.IO.File]::WriteAllText($pomPath, $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "  Fixed: $svcName-domain"
}

# Fix all other pom.xml files: replace ydsz-pmis-common with ydsz-pmis-common-all
$allPoms = Get-ChildItem -Path $backend -Filter "pom.xml" -Recurse | Where-Object { $_.FullName -notlike "*\target\*" -and $_.FullName -notlike "*\ydsz-pmis-common\*" }
foreach ($f in $allPoms) {
    $content = [System.IO.File]::ReadAllText($f.FullName)
    $original = $content
    # Replace ydsz-pmis-common dependency (not ydsz-pmis-common-* sub-modules)
    $content = $content -replace '<artifactId>ydsz-pmis-common</artifactId></dependency>', '<artifactId>ydsz-pmis-common-all</artifactId></dependency>'
    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($f.FullName, $content, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  Fixed common->common-all: $($f.Name)"
    }
}

Write-Host "`n========== Done! =========="
