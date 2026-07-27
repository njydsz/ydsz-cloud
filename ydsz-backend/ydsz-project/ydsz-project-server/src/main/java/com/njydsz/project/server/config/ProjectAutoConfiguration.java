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
 * 项目模块自动配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.project", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProjectAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProjectMetrics projectMetrics(MeterRegistry meterRegistry) {
        return new ProjectMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProjectHealthIndicator projectHealthIndicator(
            IProjectInitiationRepository projectInitiationRepository,
            ProjectMetrics projectMetrics) {
        return new ProjectHealthIndicator(projectInitiationRepository, projectMetrics);
    }
}
