# Add missing dependencies to all domain module pom.xml files
$ErrorActionPreference = "Stop"
$backend = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

$services = @("sales","finance","system","userinfo","cronjob","message","agent","project","literule","workflow")

$domainDeps = @(
    '        <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-jsqlparser</artifactId></dependency>',
    '        <dependency><groupId>io.swagger.core.v3</groupId><artifactId>swagger-annotations</artifactId></dependency>',
    '        <dependency><groupId>jakarta.validation</groupId><artifactId>jakarta.validation-api</artifactId></dependency>'
) -join "`n"

foreach ($svcName in $services) {
    $pomPath = "$backend\ydsz-pmis-$svcName\ydsz-pmis-$svcName-domain\pom.xml"
    if (-not (Test-Path $pomPath)) { continue }

    $content = [System.IO.File]::ReadAllText($pomPath)

    # Check if already has mybatis-plus
    if ($content -match "mybatis-plus") {
        Write-Host "  $svcName-domain: already has mybatis-plus, skipping"
        continue
    }

    # Add dependencies before </dependencies>
    $content = $content -replace "    </dependencies>", "$domainDeps`n    </dependencies>"
    [System.IO.File]::WriteAllText($pomPath, $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "  $svcName-domain: added mybatis-plus, swagger-annotations, validation-api"
}

# Also add swagger-annotations to root pom dependencyManagement if not present
$rootPom = "$backend\pom.xml"
$rootContent = [System.IO.File]::ReadAllText($rootPom)
if ($rootContent -notmatch "swagger-annotations") {
    # Add after springdoc dependency
    $rootContent = $rootContent -replace '(<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>\s*<version>\$\{springdoc\.version\}</version>\s*</dependency>)', "`$1`n            <dependency>`n                <groupId>io.swagger.core.v3</groupId>`n                <artifactId>swagger-annotations</artifactId>`n                <version>2.2.25</version>`n            </dependency>"
    [System.IO.File]::WriteAllText($rootPom, $rootContent, [System.Text.UTF8Encoding]::new($false))
    Write-Host "`nAdded swagger-annotations to root pom dependencyManagement"
}

Write-Host "`n========== Done! =========="
