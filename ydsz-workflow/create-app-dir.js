const fs = require('fs');
const path = require('path');

const base = path.join(__dirname, 'ydsz-workflow-app', 'src', 'main');
const dirs = [
  path.join(base, 'java', 'com', 'njydsz', 'workflow', 'app', 'config'),
  path.join(base, 'java', 'com', 'njydsz', 'workflow', 'app', 'health'),
  path.join(base, 'resources', 'META-INF', 'spring'),
];

dirs.forEach(d => {
  fs.mkdirSync(d, { recursive: true });
  console.log('Created: ' + d);
});

// pom.xml
const pomContent = `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-workflow</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>ydsz-workflow-app</artifactId>
    <packaging>jar</packaging>
    <name>ydsz-workflow-app</name>
    <description>App 端接口层（移动端 API 入口，条件激活）</description>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-base</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-workflow-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-workflow-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-workflow-server</artifactId>
        </dependency>
    </dependencies>
</project>
`;

fs.writeFileSync(path.join(__dirname, 'ydsz-workflow-app', 'pom.xml'), pomContent);
console.log('Created: pom.xml');

// WorkflowAppAutoConfiguration.java
const autoConfig = `package com.njydsz.workflow.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;

/**
 * 工作流 App 端自动配置。
 *
 * <p>仅在 APP 模式下激活，注册 App 端专属组件。
 *
 * <p><b>架构合规说明（v2.23 DDD 分层规范）：</b>双入口架构的 App 端入口层，
 * 通过 @ConditionalOnPlatform 控制激活条件（符合 §34.2.5）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.flow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowAppAutoConfiguration {
}
`;

fs.writeFileSync(path.join(base, 'java', 'com', 'njydsz', 'workflow', 'app', 'config', 'WorkflowAppAutoConfiguration.java'), autoConfig);
console.log('Created: WorkflowAppAutoConfiguration.java');

// WorkflowAppHealthIndicator.java
const healthIndicator = `package com.njydsz.workflow.app.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;

/**
 * 工作流 App 端健康检查指示器。
 *
 * <p><b>架构合规说明（v2.23 DDD 分层规范）：</b>App 端独立健康检查入口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConditionalOnClass(HealthIndicator.class)
public class WorkflowAppHealthIndicator extends AbstractModuleHealthIndicator {

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        // App 端健康检查逻辑（预留）
    }
}
`;

fs.writeFileSync(path.join(base, 'java', 'com', 'njydsz', 'workflow', 'app', 'health', 'WorkflowAppHealthIndicator.java'), healthIndicator);
console.log('Created: WorkflowAppHealthIndicator.java');

// AutoConfiguration.imports
const imports = `com.njydsz.workflow.app.config.WorkflowAppAutoConfiguration
`;

fs.writeFileSync(path.join(base, 'resources', 'META-INF', 'spring', 'org.springframework.boot.autoconfigure.AutoConfiguration.imports'), imports);
console.log('Created: AutoConfiguration.imports');

console.log('\\n=== ydsz-workflow-app 模块创建完成 ===');
