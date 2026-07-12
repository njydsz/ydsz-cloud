<#
.SYNOPSIS
  清理 server 模块中已在 Common 层声明的重复第三方依赖
#>

$backendRoot = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

# 获取所有 server 模块 pom
$poms = Get-ChildItem -Path $backendRoot -Recurse -Filter "pom.xml" |
    Where-Object { $_.FullName -match "\\ydsz-pmis-.*-server\\pom\.xml" }

$count = 0
foreach ($pom in $poms) {
    $content = Get-Content $pom.FullName -Raw -Encoding UTF8
    $modified = $false

    # 移除 spring-boot-starter-data-redis（由 common-redis 传递）
    if ($content -match '<dependency><groupId>org\.springframework\.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>') {
        $content = $content -replace '\s*<dependency><groupId>org\.springframework\.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>', ''
        $modified = $true
    }

    # 移除 redisson-spring-boot-starter（由 common-redis 传递）
    if ($content -match '<dependency><groupId>org\.redisson</groupId><artifactId>redisson-spring-boot-starter</artifactId>') {
        # 处理带 optional 和不带的两种情况
        $content = $content -replace '\s*<dependency><groupId>org\.redisson</groupId><artifactId>redisson-spring-boot-starter</artifactId><optional>true</optional></dependency>', ''
        $content = $content -replace '\s*<dependency><groupId>org\.redisson</groupId><artifactId>redisson-spring-boot-starter</artifactId></dependency>', ''
        $modified = $true
    }

    # 移除 jackson-databind（由 spring-boot-starter-web 传递）
    if ($content -match '<dependency><groupId>com\.fasterxml\.jackson\.core</groupId><artifactId>jackson-databind</artifactId></dependency>') {
        $content = $content -replace '\s*<dependency><groupId>com\.fasterxml\.jackson\.core</groupId><artifactId>jackson-databind</artifactId></dependency>', ''
        $modified = $true
    }

    # 移除 lombok（由父 POM dependencyManagement 统一提供）
    if ($content -match '<dependency><groupId>org\.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>') {
        $content = $content -replace '\s*<dependency><groupId>org\.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>', ''
        $modified = $true
    }

    if ($modified) {
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($pom.FullName, $content, $utf8NoBom)
        $name = $pom.FullName.Substring($backendRoot.Length + 1)
        Write-Host "  Cleaned: $name"
        $count++
    }
}

Write-Host "`nDone! Cleaned $count pom files."
