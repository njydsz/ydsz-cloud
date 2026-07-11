# Add jackson-annotations to all domain modules
# Add jackson-databind to all server modules (if not already present)
$ErrorActionPreference = "Stop"
$backend = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

$services = @("sales","finance","system","userinfo","cronjob","message","agent","project","literule","workflow")

# Add jackson-annotations to domain modules
foreach ($svcName in $services) {
    $pomPath = "$backend\ydsz-pmis-$svcName\ydsz-pmis-$svcName-domain\pom.xml"
    if (-not (Test-Path $pomPath)) { continue }
    $content = [System.IO.File]::ReadAllText($pomPath)
    if ($content -match "jackson-annotations") { continue }
    $dep = '        <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-annotations</artifactId></dependency>'
    $content = $content -replace "    </dependencies>", "$dep`n    </dependencies>"
    [System.IO.File]::WriteAllText($pomPath, $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "  $svcName-domain: added jackson-annotations"
}

# Add jackson-databind to server modules if not present
foreach ($svcName in $services) {
    $pomPath = "$backend\ydsz-pmis-$svcName\ydsz-pmis-$svcName-server\pom.xml"
    if (-not (Test-Path $pomPath)) { continue }
    $content = [System.IO.File]::ReadAllText($pomPath)
    if ($content -match "jackson-databind") { continue }
    # Check if server has redis or web which brings jackson transitively
    if ($content -match "spring-boot-starter-data-redis|spring-boot-starter-web") { continue }
    $dep = '        <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId></dependency>'
    $content = $content -replace "    </dependencies>", "$dep`n    </dependencies>"
    [System.IO.File]::WriteAllText($pomPath, $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "  $svcName-server: added jackson-databind"
}

Write-Host "`n========== Done! =========="
