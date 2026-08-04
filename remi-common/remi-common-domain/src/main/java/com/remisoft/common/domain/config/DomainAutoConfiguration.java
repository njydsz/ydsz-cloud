package com.remisoft.common.domain.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Domain 模块自动配置
 *
 * <p>激活领域模型层的配置属性绑定。
 *
 * <p><b>v1.4.0</b>：SpEL 条件评估器（DAG）与健康指标（DomainHealthIndicator）
 * 已随 DAG 引擎迁移至 remi-cronjob 模块，本配置不再注册相关 Bean。
 *
 * @author remi-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "remi.domain", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DomainProperties.class)
public class DomainAutoConfiguration {
}
