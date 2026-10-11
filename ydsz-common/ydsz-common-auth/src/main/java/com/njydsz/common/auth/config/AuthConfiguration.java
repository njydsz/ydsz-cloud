package com.njydsz.common.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 认证授权模块主配置（自动配置入口）。
 *
 * <p>通过 {@link Import} 聚合 4 个子配置类，按职责分层装配：
 *
 * <ul>
 *   <li>{@link JwtConfiguration} — JWT Token 生命周期（签发/验证/黑名单）
 *   <li>{@link SecurityConfiguration} — 安全纵深（CSRF 防护）
 *   <li>{@link HealthMetricsConfiguration} — 可观测性（Micrometer 指标/HealthIndicator）
 *   <li>{@link HealthScheduleConfiguration} — 定时任务（健康检查/跨实例缓存失效）
 * </ul>
 *
 * <p>{@link RbacConfiguration} 在 {@code AutoConfiguration.imports} 中独立注册，
 * 配合 {@code @AutoConfigureAfter(RedisConfiguration)} 确保 Redis Bean 就绪后再评估
 * {@code @ConditionalOnBean(RedisHashOps/RedisStringOps)} 条件，避免组件扫描期加载顺序导致的 Bean 缺失。
 *
 * <p><b>激活条件：</b>{@code ydsz.auth.enabled=true}（默认启用）。
 *
 * <p><b>扩展点：</b>任意 Bean 可通过 {@code @Bean} + {@code @Primary} 或 {@code @ConditionalOnMissingBean} 覆盖。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@AutoConfiguration
@AutoConfigureAfter(RbacConfiguration.class)
@ConditionalOnProperty(
    prefix = "ydsz.auth",
    name = {"enabled", "is-enabled"},
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties({
  AuthProperties.class,
  KeyspaceNotificationProperties.class
})
@Import({
  JwtConfiguration.class,
  SecurityConfiguration.class,
  HealthMetricsConfiguration.class,
  HealthScheduleConfiguration.class
})
public class AuthConfiguration {
  // 仅作为自动配置入口，具体 Bean 装配由各子配置类负责
}
