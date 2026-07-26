package com.njydsz.workflow.server.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 工作流模块自动配置。
 *
 * <p>注册到 {@code AutoConfiguration.imports}，由 Spring Boot 自动装配机制加载。
 * 可通过 {@code ydsz.flow.enabled=false} 禁用整个工作流模块。
 *
 * <p>启用调度支持（SLA 扫描、自动催办等定时任务）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.flow", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@EnableConfigurationProperties({FlowProperties.class, FlowHistoryProperties.class})
public class FlowAutoConfiguration {
}
