package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.ai.LlmClientAutoConfiguration;
import com.njydsz.pmis.common.chaos.ChaosAutoConfiguration;
import com.njydsz.pmis.common.featureflag.FeatureFlagAutoConfiguration;
import com.njydsz.pmis.common.sentry.SentryConfig;
import com.njydsz.pmis.common.tracing.TracingSamplingConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 基础设施层自动配置
 *
 * <p>聚合 infra 模块所有配置类，通过 Spring Boot 3 自动装配机制注册。
 * 引入 {@code ydsz-pmis-common-infra} 依赖后自动生效。
 *
 * <p>包含：
 * <ul>
 *   <li>{@link AsyncAutoConfiguration} - 异步线程池 + MDC 传递</li>
 *   <li>{@link AsyncThreadPoolConfig} - 可选线程池配置</li>
 *   <li>{@link SentinelAutoConfiguration} - Sentinel 限流/熔断</li>
 *   <li>{@link SeataAutoConfiguration} - Seata 分布式事务</li>
 *   <li>{@link Resilience4jConfig} - Resilience4j 熔断器</li>
 *   <li>MinIO 对象存储（由 ydsz-pmis-common-file 模块提供，此处不再重复导入）</li>
 *   <li>{@link ChaosAutoConfiguration} - 混沌工程</li>
 *   <li>{@link FeatureFlagAutoConfiguration} - 功能开关</li>
 *   <li>{@link LlmClientAutoConfiguration} - LLM 客户端</li>
 *   <li>{@link SentryConfig} - Sentry 异常上报</li>
 *   <li>{@link TracingSamplingConfig} - 链路追踪采样</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Configuration
@Import({
    AsyncAutoConfiguration.class,
    AsyncThreadPoolConfig.class,
    SentinelAutoConfiguration.class,
    SeataAutoConfiguration.class,
    Resilience4jConfig.class,
    ChaosAutoConfiguration.class,
    FeatureFlagAutoConfiguration.class,
    LlmClientAutoConfiguration.class,
    SentryConfig.class,
    TracingSamplingConfig.class
})
public class InfraAutoConfiguration {
}
