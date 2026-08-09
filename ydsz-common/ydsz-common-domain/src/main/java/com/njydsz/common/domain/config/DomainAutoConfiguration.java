package com.njydsz.common.domain.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Domain 模块自动配置
 *
 * <p>激活领域模型层的配置属性绑定（{@link DomainProperties}）。
 *
 * <p><b>v1.8.0</b>：移除失效的 PageQueryFactory 运行时注入，PageQuery 改为无状态承载对象，
 * 深度分页阈值通过 {@link DomainProperties} 直接注入消费方（如 SafeQueryInnerInterceptor）。
 * <p><b>v1.4.0</b>：SpEL 条件评估器（DAG）与健康指标（DomainHealthIndicator）
 * 已随 DAG 引擎迁移至 ydsz-cronjob 模块，本配置不再注册相关 Bean。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(DomainProperties.class)
public class DomainAutoConfiguration {
    // 配置绑定由 @EnableConfigurationProperties 激活
}
