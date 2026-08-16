package com.njydsz.common.sentry.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Sentry 可观测性模块自动配置（总入口）。
 *
 * <p>通过 {@link Import} 引入各子配置类，按职责拆分为：
 * <ul>
 *   <li>{@link MetricsAutoConfiguration}：指标采集 + 熔断器</li>
 *   <li>{@link LoggingAutoConfiguration}：日志发布（ELK/Loki/双发/异步）</li>
 *   <li>{@link TracingAutoConfiguration}：链路追踪 + 慢请求检测</li>
 *   <li>{@link AlertingAutoConfiguration}：告警收敛 + IM 通知</li>
 *   <li>{@link SlaAutoConfiguration}：SLA 指标采集 + AOP 切面</li>
 *   <li>{@link SelfMonitorAutoConfiguration}：自监控指标上报</li>
 *   <li>{@link HealthIndicatorAutoConfiguration}：Actuator 健康探针</li>
 *   <li>{@link OtelAutoConfiguration}：OpenTelemetry SDK 增强</li>
 * </ul>
 *
 * <p>{@code ydsz.sentry.enabled=true}（默认）时装配全部能力；
 * {@code ydsz.sentry.enabled=false} 时整个可观测性模块不生效。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SentryProperties
 */
@AutoConfiguration
@EnableConfigurationProperties(SentryProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.sentry", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        MetricsAutoConfiguration.class,
        LoggingAutoConfiguration.class,
        TracingAutoConfiguration.class,
        AlertingAutoConfiguration.class,
        SlaAutoConfiguration.class,
        SelfMonitorAutoConfiguration.class,
        HealthIndicatorAutoConfiguration.class,
        OtelAutoConfiguration.class
})
public class SentryAutoConfiguration {
    // 子配置类通过 @Import 引入，本类仅作为统一入口
}
