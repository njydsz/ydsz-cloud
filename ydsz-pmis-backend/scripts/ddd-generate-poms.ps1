# DDD pom.xml generator - uses simple string concatenation
$ErrorActionPreference = "Stop"
$backend = "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

function Write-Pom($path, $artifactId, $parentArtifactId, $packaging, $name, $desc, $depsXml, $buildXml, $propsXml) {
    $lines = @(
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<project xmlns="http://maven.apache.org/POM/4.0.0"'
        '         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'
        '         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">'
        '    <modelVersion>4.0.0</modelVersion>'
        '    <parent>'
        '        <groupId>com.njydsz.pmis</groupId>'
        "        <artifactId>$parentArtifactId</artifactId>"
        '        <version>1.0.0-SNAPSHOT</version>'
        '    </parent>'
        "    <artifactId>$artifactId</artifactId>"
    )
    if ($packaging) { $lines += "    <packaging>$packaging</packaging>" }
    $lines += "    <name>$name</name>"
    $lines += "    <description>$desc</description>"
    if ($propsXml) { $lines += "    <properties>"; $lines += $propsXml; $lines += "    </properties>" }
    if ($depsXml) { $lines += "    <dependencies>"; $lines += $depsXml; $lines += "    </dependencies>" }
    if ($buildXml) { $lines += "    <build>"; $lines += $buildXml; $lines += "    </build>" }
    $lines += '</project>'
    $content = $lines -join "`n"
    Set-Content -Path $path -Value $content -Encoding UTF8
}

$services = @("sales","finance","system","userinfo","cronjob","message","agent","project","literule","workflow")

foreach ($svcName in $services) {
    $svcDir = "$backend\ydsz-pmis-$svcName"
    Write-Host "========== $svcName =========="

    # --- Determine service-specific config ---
    $descMap = @{
        sales = 'Sales Service: Opportunity/Contract/Change/Template (DDD 5-Layer)'
        finance = 'Finance Service: Invoice/Payment/Expense/Revenue/Profit (DDD 5-Layer)'
        system = 'System Service: Config/File/Audit/OperationLog/FeatureFlag (DDD 5-Layer)'
        userinfo = 'User Center: User/Role/Permission/Org/Attendance/Bench (DDD 5-Layer)'
        cronjob = 'CronJob Service: DAG/Alert/Stats/GlueCode (DDD 5-Layer)'
        message = 'Message Center: Notification/SMS/Email/Webhook/Template/Route (DDD 5-Layer)'
        agent = 'AI Agent: Orchestration/Tool/KnowledgeBase/RAG/MCP/HITL (DDD 5-Layer)'
        project = 'Project Execution: Initiation/WBS/EVM/Risk/Purchase/Report (DDD 5-Layer)'
        literule = 'LiteRule Engine: Expression/DynamicConfig/HotReload/Version (DDD 5-Layer, Library)'
        workflow = 'Workflow Engine: Definition/Instance/Approval/DMN/Delegate (DDD 5-Layer)'
    }

    $useLiterule = @("sales","finance","project","workflow")
    $isLibrary = ($svcName -eq "literule")

    # Feign api deps (cross-service)
    $feignDepsMap = @{
        cronjob = @("message-api")
        message = @("message-api")
        agent = @("project-api")
        project = @("finance-api","userinfo-api","workflow-api","agent-api")
        workflow = @("project-api","message-api","agent-api","userinfo-api")
    }

    # --- Parent pom ---
    $modules = @(
        "        <module>ydsz-pmis-$svcName-api</module>"
        "        <module>ydsz-pmis-$svcName-domain</module>"
        "        <module>ydsz-pmis-$svcName-infra</module>"
        "        <module>ydsz-pmis-$svcName-server</module>"
        "        <module>ydsz-pmis-$svcName-web</module>"
    ) -join "`n"

    $propsXml = $null
    if ($svcName -eq "literule") {
        $propsXml = @(
            "        <aviator.version>5.4.3</aviator.version>"
            "        <groovy.version>4.0.22</groovy.version>"
            "        <jmh.version>1.37</jmh.version>"
            "        <nashorn.version>15.4</nashorn.version>"
            "        <qlexpress.version>3.3.1</qlexpress.version>"
        ) -join "`n"
    }

    $parentLines = @(
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'
        '         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">'
        '    <modelVersion>4.0.0</modelVersion>'
        '    <parent>'
        '        <groupId>com.njydsz.pmis</groupId>'
        '        <artifactId>ydsz-pmis-parent</artifactId>'
        '        <version>1.0.0-SNAPSHOT</version>'
        '    </parent>'
        "    <artifactId>ydsz-pmis-$svcName</artifactId>"
        '    <packaging>pom</packaging>'
        "    <name>ydsz-pmis-$svcName</name>"
        "    <description>$($descMap[$svcName])</description>"
    )
    if ($propsXml) {
        $parentLines += '    <properties>'
        $parentLines += $propsXml.Split("`n")
        $parentLines += '    </properties>'
    }
    $parentLines += '    <modules>'
    $parentLines += $modules.Split("`n")
    $parentLines += '    </modules>'
    $parentLines += '</project>'
    Set-Content -Path "$svcDir\pom.xml" -Value ($parentLines -join "`n") -Encoding UTF8

    # --- API pom ---
    $apiDeps = @(
        '        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-common</artifactId></dependency>'
        '        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>'
    ) -join "`n"
    Write-Pom "$svcDir\ydsz-pmis-$svcName-api\pom.xml" "ydsz-pmis-$svcName-api" "ydsz-pmis-$svcName" "" "ydsz-pmis-$svcName-api" "Feign 门面接口 + DTO" $apiDeps "" ""

    # --- Domain pom ---
    $domainDeps = '        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-common</artifactId></dependency>'
    Write-Pom "$svcDir\ydsz-pmis-$svcName-domain\pom.xml" "ydsz-pmis-$svcName-domain" "ydsz-pmis-$svcName" "" "ydsz-pmis-$svcName-domain" "领域层：实体/值对象/聚合/事件" $domainDeps "" ""

    # --- Infra pom ---
    $infraDeps = @(
        '        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-' + $svcName + '-domain</artifactId></dependency>'
        '        <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-spring-boot4-starter</artifactId></dependency>'
        '        <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-jsqlparser</artifactId></dependency>'
        '        <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>'
        '        <dependency><groupId>com.alibaba</groupId><artifactId>druid-spring-boot-3-starter</artifactId></dependency>'
    ) -join "`n"
    Write-Pom "$svcDir\ydsz-pmis-$svcName-infra\pom.xml" "ydsz-pmis-$svcName-infra" "ydsz-pmis-$svcName" "" "ydsz-pmis-$svcName-infra" "基础设施层：持久化/Mapper" $infraDeps "" ""

    # --- Server pom ---
    $serverDeps = @(
        '        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-' + $svcName + '-infra</artifactId></dependency>'
        '        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-common</artifactId></dependency>'
    )
    if ($useLiterule -contains $svcName) {
        $serverDeps += '        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-literule-server</artifactId><version>${project.version}</version></dependency>'
    }
    if ($feignDepsMap[$svcName]) {
        foreach ($fd in $feignDepsMap[$svcName]) {
            if ($fd -ne "$svcName-api") {
                $serverDeps += "        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-$fd</artifactId><version>`${project.version}</version></dependency>"
            }
        }
        # Self api dependency
        if ($feignDepsMap[$svcName] -contains "$svcName-api") {
            $serverDeps += "        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-$svcName-api</artifactId><version>`${project.version}</version></dependency>"
        }
    }

    # Service-specific server deps
    Switch ($svcName) {
        "sales" {
            $serverDeps += '        <dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>cn.hutool</groupId><artifactId>hutool-all</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>com.alibaba</groupId><artifactId>easyexcel</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.commons</groupId><artifactId>commons-csv</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.seata</groupId><artifactId>seata-spring-boot-starter</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId><optional>true</optional></dependency>'
            $serverDeps += '        <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId></dependency>'
        }
        "finance" {
            $serverDeps += '        <dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>cn.hutool</groupId><artifactId>hutool-all</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>com.alibaba</groupId><artifactId>easyexcel</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.commons</groupId><artifactId>commons-csv</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.seata</groupId><artifactId>seata-spring-boot-starter</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId><optional>true</optional></dependency>'
            $serverDeps += '        <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId></dependency>'
        }
        "system" {
            $serverDeps += '        <dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>io.minio</groupId><artifactId>minio</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId><optional>true</optional></dependency>'
        }
        "userinfo" {
            $serverDeps += '        <dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>cn.hutool</groupId><artifactId>hutool-all</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.redisson</groupId><artifactId>redisson-spring-boot-starter</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-cache</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><scope>runtime</scope></dependency>'
            $serverDeps += '        <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><scope>runtime</scope></dependency>'
            $serverDeps += '        <dependency><groupId>com.github.whvcse</groupId><artifactId>easy-captcha</artifactId></dependency>'
        }
        "cronjob" {
            $serverDeps += '        <dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>com.alibaba</groupId><artifactId>easyexcel</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>io.minio</groupId><artifactId>minio</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.groovy</groupId><artifactId>groovy</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>'
        }
        "message" {
            $serverDeps += '        <dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-mail</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.redisson</groupId><artifactId>redisson-spring-boot-starter</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.rocketmq</groupId><artifactId>rocketmq-spring-boot-starter</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-spring-boot3</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId><optional>true</optional></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>'
        }
        "agent" {
            $serverDeps += '        <dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>cn.hutool</groupId><artifactId>hutool-all</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-health</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-circuitbreaker</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.ai</groupId><artifactId>spring-ai-openai-spring-boot-starter</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>com.knuddels</groupId><artifactId>jtokkit</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>'
        }
        "project" {
            $serverDeps += '        <dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>cn.hutool</groupId><artifactId>hutool-all</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>com.alibaba</groupId><artifactId>easyexcel</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.commons</groupId><artifactId>commons-csv</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>io.minio</groupId><artifactId>minio</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.seata</groupId><artifactId>seata-spring-boot-starter</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId><optional>true</optional></dependency>'
            $serverDeps += '        <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>'
        }
        "literule" {
            $serverDeps += '        <dependency><groupId>com.googlecode.aviator</groupId><artifactId>aviator</artifactId><version>${aviator.version}</version></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.groovy</groupId><artifactId>groovy-jsr223</artifactId><version>${groovy.version}</version></dependency>'
            $serverDeps += '        <dependency><groupId>org.openjdk.nashorn</groupId><artifactId>nashorn-core</artifactId><version>${nashorn.version}</version></dependency>'
            $serverDeps += '        <dependency><groupId>com.alibaba</groupId><artifactId>QLExpress</artifactId><version>${qlexpress.version}</version></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-autoconfigure</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId><optional>true</optional></dependency>'
            $serverDeps += '        <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId><optional>true</optional></dependency>'
            $serverDeps += '        <dependency><groupId>org.redisson</groupId><artifactId>redisson-spring-boot-starter</artifactId><optional>true</optional></dependency>'
            $serverDeps += '        <dependency><groupId>cn.hutool</groupId><artifactId>hutool-all</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.yaml</groupId><artifactId>snakeyaml</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId><optional>true</optional></dependency>'
            # Test deps
            $serverDeps += '        <dependency><groupId>org.openjdk.jmh</groupId><artifactId>jmh-core</artifactId><version>${jmh.version}</version><scope>test</scope></dependency>'
            $serverDeps += '        <dependency><groupId>org.openjdk.jmh</groupId><artifactId>jmh-generator-annprocess</artifactId><version>${jmh.version}</version><scope>test</scope></dependency>'
            $serverDeps += '        <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>'
            $serverDeps += '        <dependency><groupId>org.junit.platform</groupId><artifactId>junit-platform-launcher</artifactId><scope>test</scope></dependency>'
        }
        "workflow" {
            $serverDeps += '        <dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>'
            $serverDeps += '        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>'
        }
    }
    $serverDepsXml = $serverDeps -join "`n"
    Write-Pom "$svcDir\ydsz-pmis-$svcName-server\pom.xml" "ydsz-pmis-$svcName-server" "ydsz-pmis-$svcName" "" "ydsz-pmis-$svcName-server" "业务服务层：Service/Engine/Listener/Job" $serverDepsXml "" ""

    # --- Web pom ---
    $webDeps = @(
        "        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-$svcName-server</artifactId></dependency>"
        "        <dependency><groupId>com.njydsz.pmis</groupId><artifactId>ydsz-pmis-$svcName-api</artifactId></dependency>"
        '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>'
        '        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-bootstrap</artifactId></dependency>'
        '        <dependency><groupId>com.alibaba.cloud</groupId><artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId></dependency>'
        '        <dependency><groupId>com.alibaba.cloud</groupId><artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId></dependency>'
        '        <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId></dependency>'
    )
    # Web extra deps
    Switch ($svcName) {
        { @("sales","finance","project","agent","userinfo","message") -contains $_ } {
            $webDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>'
        }
        "message" {
            $webDeps += '        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-websocket</artifactId></dependency>'
        }
        "agent" {
            $webDeps += '        <dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers</artifactId><scope>test</scope></dependency>'
            $webDeps += '        <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>'
            $webDeps += '        <dependency><groupId>com.redis</groupId><artifactId>testcontainers-redis</artifactId><scope>test</scope></dependency>'
            $webDeps += '        <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>'
        }
    }
    $webDepsXml = $webDeps -join "`n"

    $buildXml = $null
    if ($isLibrary) {
        $buildXml = @(
            '        <plugins>'
            '            <plugin>'
            '                <groupId>org.springframework.boot</groupId>'
            '                <artifactId>spring-boot-maven-plugin</artifactId>'
            '                <configuration><skip>true</skip></configuration>'
            '            </plugin>'
            '        </plugins>'
        ) -join "`n"
    } else {
        $buildXml = @(
            '        <finalName>${project.artifactId}</finalName>'
            '        <plugins>'
            '            <plugin>'
            '                <groupId>org.springframework.boot</groupId>'
            '                <artifactId>spring-boot-maven-plugin</artifactId>'
            '                <executions>'
            '                    <execution>'
            '                        <id>repackage</id>'
            '                        <configuration><classifier>exec</classifier></configuration>'
            '                    </execution>'
            '                </executions>'
            '            </plugin>'
            '        </plugins>'
        ) -join "`n"
    }
    Write-Pom "$svcDir\ydsz-pmis-$svcName-web\pom.xml" "ydsz-pmis-$svcName-web" "ydsz-pmis-$svcName" "" "ydsz-pmis-$svcName-web" "B端入口层：Controller/Config/Application" $webDepsXml $buildXml ""

    Write-Host "  Generated 6 pom.xml files"
}

Write-Host "`n========== All pom.xml files generated! =========="
