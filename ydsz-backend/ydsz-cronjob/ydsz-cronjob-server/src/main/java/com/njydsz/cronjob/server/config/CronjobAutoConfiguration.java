package com.njydsz.cronjob.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务调度引擎自动配置类。
 *
 * <p>注册调度引擎核心组件，启用 @Scheduled 定时任务支持。
 * 通过 {@code ydsz.cronjob.enabled=true}（默认启用）控制是否加载。
 *
 * <h3>对标</h3>
 * <p>对标 XXL-Job 的 XxlJobAdminConfig 和 PowerJob 的 PowerJobAutoConfiguration，
 * 提供标准 Spring Boot 自动配置能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.cronjob", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CronjobAutoConfiguration {
}
