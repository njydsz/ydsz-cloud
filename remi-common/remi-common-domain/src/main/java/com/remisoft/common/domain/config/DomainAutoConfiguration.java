package com.remisoft.common.domain.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Domain 模块自动配置
 *
 * <p>激活领域模型层的配置属性绑定。配置通过 {@link com.remisoft.common.domain.query.PageQueryFactory}
 * 工厂类在创建 PageQuery 时实例级注入，不再使用静态全局注入。
 *
 * <p><b>v1.7.0</b>：移除 PageQuery.initProperties() 静态注入，改为 PageQueryFactory 工厂模式。
 * <p><b>v1.4.0</b>：SpEL 条件评估器（DAG）与健康指标（DomainHealthIndicator）
 * 已随 DAG 引擎迁移至 remi-cronjob 模块，本配置不再注册相关 Bean。
 *
 * @author remi-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(DomainProperties.class)
public class DomainAutoConfiguration {
    // 配置绑定由 @EnableConfigurationProperties 激活
    // 运行时注入由 PageQueryFactory 完成
}
