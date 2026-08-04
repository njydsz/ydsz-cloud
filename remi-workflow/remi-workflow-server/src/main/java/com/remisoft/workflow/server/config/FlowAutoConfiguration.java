package com.remisoft.workflow.server.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.remisoft.common.redis.service.RedisService;
import com.remisoft.workflow.infra.mapper.FlowCcMapper;
import com.remisoft.workflow.infra.mapper.FlowInstanceMapper;
import com.remisoft.workflow.infra.mapper.FlowRunTaskMapper;
import com.remisoft.workflow.server.health.FlowHealthIndicator;
import com.remisoft.workflow.server.metrics.FlowMetrics;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 工作流模块自动配置。
 *
 * <p>注册到 {@code AutoConfiguration.imports}，由 Spring Boot 自动装配机制加载。
 * 可通过 {@code remi.flow.enabled=false} 禁用整个工作流模块。
 *
 * <p>启用调度支持（SLA 扫描、自动催办等定时任务）。
 *
 * <p>P0-2: FlowHealthIndicator 和 FlowMetrics 从 @Component 改为在此处 @Bean 注册，
 * 统一模块 Bean 装配入口，与项目其他模块保持一致。
 *
 * @author remi-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "remi.flow", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@EnableConfigurationProperties({FlowProperties.class, FlowHistoryProperties.class})
public class FlowAutoConfiguration {

    /**
     * 工作流健康检查 Bean
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnProperty(prefix = "remi.flow", name = "health-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(FlowHealthIndicator.class)
    public FlowHealthIndicator flowHealthIndicator(FlowInstanceMapper instanceMapper,
                                                    FlowRunTaskMapper runTaskMapper,
                                                    ObjectProvider<RedisService> redisServiceProvider) {
        return new FlowHealthIndicator(instanceMapper, runTaskMapper, redisServiceProvider);
    }

    /**
     * 工作流 Prometheus 指标收集器 Bean
     */
    @Bean
    @ConditionalOnMissingBean(FlowMetrics.class)
    public FlowMetrics flowMetrics(MeterRegistry registry,
                                    ObjectProvider<FlowInstanceMapper> instanceMapperProvider,
                                    ObjectProvider<FlowRunTaskMapper> taskMapperProvider,
                                    ObjectProvider<FlowCcMapper> ccMapperProvider) {
        return new FlowMetrics(registry, instanceMapperProvider, taskMapperProvider, ccMapperProvider);
    }

    // P0-1: flowQueueExecutor 线程池已迁移到 remi-common-thread 统一管理
    // 配置项: remi.thread.pools.flowQueue.* (见 application.yml)
    // Bean 名称: flowQueueExecutor（key + "Executor"）
}
