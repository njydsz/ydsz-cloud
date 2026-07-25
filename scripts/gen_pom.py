#!/usr/bin/env python3
"""Generate 10 pom.xml files for ydsz-userinfo and ydsz-system DDD 5-layer modules."""
import os

BASE = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

POM_HEADER = '''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>'''

POM_FOOTER = '''</project>'''


def gen_pom(parent_artifact, artifact_id, name, desc, deps_xml):
    """Generate a pom.xml content string."""
    return f'''{POM_HEADER}
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>{parent_artifact}</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>{artifact_id}</artifactId>
    <name>{name}</name>
    <description>{desc}</description>
    <dependencies>
{deps_xml}
    </dependencies>
{POM_FOOTER}
'''


# ---- ydsz-userinfo pom.xml contents ----

USERINFO_API_DEPS = '''        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-feign</artifactId>
        </dependency>
        <dependency>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations-jakarta</artifactId>
        </dependency>'''

USERINFO_DOMAIN_DEPS = '''        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-safe</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-annotation</artifactId>
        </dependency>
        <dependency>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations-jakarta</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>'''

USERINFO_INFRA_DEPS = '''        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-userinfo-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        </dependency>'''

USERINFO_SERVER_DEPS = '''        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-userinfo-infra</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-lock</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-feign</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-audit</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-cache</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-json</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-auth</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-excel</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>dynamic-datasource-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.github.whvcse</groupId>
            <artifactId>easy-captcha</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-health</artifactId>
        </dependency>'''

USERINFO_WEB_DEPS = '''        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-userinfo-server</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-userinfo-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>'''

USERINFO_WEB_BUILD = '''
    <build>
        <finalName>${project.artifactId}</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>repackage</id>
                        <configuration>
                            <classifier>exec</classifier>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
'''


# ---- ydsz-system pom.xml contents ----

SYSTEM_API_DEPS = USERINFO_API_DEPS  # same structure

SYSTEM_DOMAIN_DEPS = USERINFO_DOMAIN_DEPS  # same structure

SYSTEM_INFRA_DEPS = '''        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-system-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        </dependency>'''

SYSTEM_SERVER_DEPS = '''        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-system-infra</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-lock</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-feign</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-audit</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-cache</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-json</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-config</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>dynamic-datasource-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-health</artifactId>
        </dependency>'''

SYSTEM_WEB_DEPS = '''        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-system-server</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-system-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>'''


def write_pom(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f'Written: {path}')


def main():
    # ydsz-userinfo pom.xml files
    ui = 'ydsz-userinfo'
    write_pom(f'{BASE}/{ui}/{ui}-api/pom.xml',
              gen_pom(ui, f'{ui}-api', f'{ui}-api', 'API Layer - Feign Client + DTO/VO + Fallback', USERINFO_API_DEPS))
    write_pom(f'{BASE}/{ui}/{ui}-domain/pom.xml',
              gen_pom(ui, f'{ui}-domain', f'{ui}-domain', 'Domain Layer - Entity/DTO/VO/Query/Enum/Converter', USERINFO_DOMAIN_DEPS))
    write_pom(f'{BASE}/{ui}/{ui}-infra/pom.xml',
              gen_pom(ui, f'{ui}-infra', f'{ui}-infra', 'Infrastructure Layer - MyBatis Mapper + Repository', USERINFO_INFRA_DEPS))
    write_pom(f'{BASE}/{ui}/{ui}-server/pom.xml',
              gen_pom(ui, f'{ui}-server', f'{ui}-server', 'Application Layer - Service + AuthService + OAuth2', USERINFO_SERVER_DEPS))
    # web layer has extra build section
    web_content = gen_pom(ui, f'{ui}-web', f'{ui}-web', 'Web Layer - REST Controller + Bootstrap', USERINFO_WEB_DEPS)
    # Insert build section before closing </project>
    web_content = web_content.replace('</project>', f'{USERINFO_WEB_BUILD}</project>')
    write_pom(f'{BASE}/{ui}/{ui}-web/pom.xml', web_content)

    # ydsz-system pom.xml files
    sy = 'ydsz-system'
    write_pom(f'{BASE}/{sy}/{sy}-api/pom.xml',
              gen_pom(sy, f'{sy}-api', f'{sy}-api', 'API Layer - Feign Client + DTO/VO + Fallback', SYSTEM_API_DEPS))
    write_pom(f'{BASE}/{sy}/{sy}-domain/pom.xml',
              gen_pom(sy, f'{sy}-domain', f'{sy}-domain', 'Domain Layer - Entity/DTO/VO/Query/Enum/Converter', SYSTEM_DOMAIN_DEPS))
    write_pom(f'{BASE}/{sy}/{sy}-infra/pom.xml',
              gen_pom(sy, f'{sy}-infra', f'{sy}-infra', 'Infrastructure Layer - MyBatis Mapper + Repository', SYSTEM_INFRA_DEPS))
    write_pom(f'{BASE}/{sy}/{sy}-server/pom.xml',
              gen_pom(sy, f'{sy}-server', f'{sy}-server', 'Application Layer - Service + Config + AppInfo', SYSTEM_SERVER_DEPS))
    web_content2 = gen_pom(sy, f'{sy}-web', f'{sy}-web', 'Web Layer - REST Controller + Bootstrap', SYSTEM_WEB_DEPS)
    web_content2 = web_content2.replace('</project>', f'{USERINFO_WEB_BUILD}</project>')
    write_pom(f'{BASE}/{sy}/{sy}-web/pom.xml', web_content2)

    print('\nDone: 10 pom.xml files generated.')


if __name__ == '__main__':
    main()
