package com.njydsz.project.server.config;

import com.njydsz.project.domain.repository.project.IProjectInitiationRepository;
import com.njydsz.project.server.health.ProjectHealthIndicator;
import com.njydsz.project.server.metrics.ProjectMetrics;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 项目管理模块自动配置
 *
 * <p>封装 ydsz-project 服务的核心 Bean 注册：Micrometer 指标采集器和健康检查 Indicator。
 * 通过 {@code @ConditionalOnProperty(prefix = "ydsz.project", name = "enabled")} 门控，
 * 默认启用（{@code matchIfMissing = true}）。
 *
 * <p><b>注册 Bean：</b>
 * <ul>
 *   <li>{@link ProjectMetrics} — Prometheus 指标采集（依赖 {@link MeterRegistry}）</li>
 *   <li>{@link ProjectHealthIndicator} — Actuator 健康检查（依赖 Repository + Metrics）</li>
 * </ul>
 *
 * <p><b>装配顺序：</b>先注册 {@link ProjectMetrics}，再注册 {@link ProjectHealthIndicator}
 * （后者依赖前者）。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectMetrics 项目模块指标采集器
 * @see ProjectHealthIndicator 项目模块健康检查
 * @see ProjectProperties 项目模块配置属性
 */

@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.project", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProjectAutoConfiguration {

    /**
     * 注册项目模块 Micrometer 指标采集器
     *
     * <p>通过 {@code @ConditionalOnMissingBean} 确保业务方可覆盖默认实现。
     *
     * @param meterRegistry Micrometer 注册中心
     * @return {@link ProjectMetrics} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ProjectMetrics projectMetrics(MeterRegistry meterRegistry) {
        return new ProjectMetrics(meterRegistry);
    }

    /**
     * 注册项目模块健康检查 Indicator
     *
     * <p>通过 {@code @ConditionalOnMissingBean} 确保业务方可覆盖默认实现。
     * 依赖 {@link ProjectMetrics} Bean（由上面的 {@code projectMetrics} 方法注册）。
     *
     * @param projectInitiationRepository 项目立项 Repository
     * @param projectMetrics 项目模块指标采集器
     * @return {@link ProjectHealthIndicator} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ProjectHealthIndicator projectHealthIndicator(
            IProjectInitiationRepository projectInitiationRepository,
            ProjectMetrics projectMetrics) {
        return new ProjectHealthIndicator(projectInitiationRepository, projectMetrics);
    }
}
