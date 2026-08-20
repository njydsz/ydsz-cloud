/**
 * 创建 ydzs-workflow-app 模块完整目录结构。
 * 在项目根目录运行: node ydzs-workflow/create-app-module.js
 */
const fs = require('fs');
const path = require('path');

const moduleDir = path.join(__dirname, 'ydsz-workflow-app');

const files = {
  'pom.xml': `<?xml version="1.0" encoding="UTF-8"?>
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
    <name>ydsz-workflow-app</name>
    <description>App 端接口层（移动端 API 入口，条件激活）</description>
    <dependencies>
        <!-- 本模块服务层（必须，复用 server 层业务逻辑） -->
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-workflow-server</artifactId>
        </dependency>
        <!-- 公共平台基座（ConditionalOnPlatform 条件注解支持） -->
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-base</artifactId>
        </dependency>
        <!-- 公共 Web 健康检查基类（AbstractModuleHealthIndicator） -->
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-web</artifactId>
        </dependency>
        <!-- 本模块 API 契约（必须） -->
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-workflow-api</artifactId>
        </dependency>
        <!-- 本模块领域层（必须） -->
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-workflow-domain</artifactId>
        </dependency>
    </dependencies>
</project>
`,
  'src/main/java/com/njydsz/workflow/app/config/WorkflowAppAutoConfiguration.java': `package com.njydsz.workflow.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

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
`,
  'src/main/java/com/njydsz/workflow/app/health/WorkflowAppHealthIndicator.java': `package com.njydsz.workflow.app.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;

/**
 * 工作流 App 端健康检查指示器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConditionalOnClass(HealthIndicator.class)
public class WorkflowAppHealthIndicator extends AbstractModuleHealthIndicator {

  @Override
  protected void doHealthCheck(org.springframework.boot.health.contributor.Health.Builder builder) {
    // App 端健康检查逻辑（预留）
  }
}
`,
  'src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports': `com.njydsz.workflow.app.config.WorkflowAppAutoConfiguration
`
};

let created = 0;
for (const [relPath, content] of Object.entries(files)) {
  const fullPath = path.join(moduleDir, relPath);
  const dir = path.dirname(fullPath);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(fullPath, content, { encoding: 'utf8' });
  console.log('  Created: ' + fullPath);
  created++;
}

console.log('\nDone! Created ' + created + ' files in ' + moduleDir);
