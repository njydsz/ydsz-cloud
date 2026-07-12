<#
.SYNOPSIS
  修复所有业务模块的 DDD 分层 pom 依赖
.DESCRIPTION
  domain: common-web → common-core, 移除 mybatis-plus-jsqlparser
  infra: 添加 common-jdbc, 移除直接声明的 mybatis-plus/postgresql/druid
  api: common-web → common-core + common-feign, 移除直接声明的 openfeign
#>

$backendRoot = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"
$modules = @("userinfo","workflow","project","sales","system","message","agent","literule","cronjob")

$count = 0
foreach ($mod in $modules) {
    # === domain pom ===
    $domainPom = Join-Path $backendRoot "ydsz-pmis-$mod\ydsz-pmis-$mod-domain\pom.xml"
    if (Test-Path $domainPom) {
        $content = Get-Content $domainPom -Raw -Encoding UTF8
        # common-web → common-core
        $content = $content -replace '<artifactId>ydsz-pmis-common-web</artifactId>', '<artifactId>ydsz-pmis-common-core</artifactId>'
        # 移除 mybatis-plus-jsqlparser 依赖行（domain 不需要 SQL 解析器）
        $content = $content -replace '\s*<dependency><groupId>com\.baomidou</groupId><artifactId>mybatis-plus-jsqlparser</artifactId></dependency>', ''
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($domainPom, $content, $utf8NoBom)
        Write-Host "  Fixed domain: ydsz-pmis-$mod"
        $count++
    }

    # === infra pom ===
    $infraPom = Join-Path $backendRoot "ydsz-pmis-$mod\ydsz-pmis-$mod-infra\pom.xml"
    if (Test-Path $infraPom) {
        $content = Get-Content $infraPom -Raw -Encoding UTF8
        # 在 domain 依赖后添加 common-jdbc
        if ($content -notmatch 'ydsz-pmis-common-jdbc') {
            $content = $content -replace '(<dependency><groupId>com\.njydsz\.pmis</groupId><artifactId>ydsz-pmis-' + $mod + '-domain</artifactId></dependency>)', "`$1`n        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-common-jdbc</artifactId></dependency>"
        }
        # 移除直接声明的 mybatis-plus-spring-boot4-starter
        $content = $content -replace '\s*<dependency><groupId>com\.baomidou</groupId><artifactId>mybatis-plus-spring-boot4-starter</artifactId></dependency>', ''
        # 移除直接声明的 mybatis-plus-jsqlparser
        $content = $content -replace '\s*<dependency><groupId>com\.baomidou</groupId><artifactId>mybatis-plus-jsqlparser</artifactId></dependency>', ''
        # 移除直接声明的 postgresql
        $content = $content -replace '\s*<dependency><groupId>org\.postgresql</groupId><artifactId>postgresql</artifactId></dependency>', ''
        # 移除直接声明的 druid
        $content = $content -replace '\s*<dependency><groupId>com\.alibaba</groupId><artifactId>druid-spring-boot-3-starter</artifactId></dependency>', ''
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($infraPom, $content, $utf8NoBom)
        Write-Host "  Fixed infra: ydsz-pmis-$mod"
        $count++
    }

    # === api pom ===
    $apiPom = Join-Path $backendRoot "ydsz-pmis-$mod\ydsz-pmis-$mod-api\pom.xml"
    if (Test-Path $apiPom) {
        $content = Get-Content $apiPom -Raw -Encoding UTF8
        # common-web → common-core + common-feign
        $content = $content -replace '<artifactId>ydsz-pmis-common-web</artifactId>', '<artifactId>ydsz-pmis-common-core</artifactId>'
        # 在 common-core 依赖后添加 common-feign
        if ($content -notmatch 'ydsz-pmis-common-feign') {
            $content = $content -replace '(<dependency><groupId>com\.njydsz\.pmis</groupId><artifactId>ydsz-pmis-common-core</artifactId></dependency>)', "`$1`n        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-common-feign</artifactId></dependency>"
        }
        # 移除直接声明的 spring-cloud-starter-openfeign
        $content = $content -replace '\s*<dependency><groupId>org\.springframework\.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>', ''
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($apiPom, $content, $utf8NoBom)
        Write-Host "  Fixed api: ydsz-pmis-$mod"
        $count++
    }
}

Write-Host "`nDone! Fixed $count pom files."
