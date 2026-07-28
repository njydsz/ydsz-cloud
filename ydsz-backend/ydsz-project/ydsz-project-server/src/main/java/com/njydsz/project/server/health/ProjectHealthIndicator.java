package com.njydsz.project.server.health;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.project.domain.repository.project.IProjectInitiationRepository;
import com.njydsz.project.server.metrics.ProjectMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.health.contributor.Health;

/**
 * 项目模块健康检查。
 *
 * <p>P2-1: 继承 {@link AbstractModuleHealthIndicator} 统一基类，
 * 使用模板方法模式替代直接实现 {@code HealthIndicator}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ProjectHealthIndicator extends AbstractModuleHealthIndicator {

    private final IProjectInitiationRepository projectInitiationRepository;
    private final ProjectMetrics projectMetrics;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        checkTableProbeWithValue(builder, "projectInitiationCount",
                projectInitiationRepository::count);
        builder.withDetail("metricsRegistered", projectMetrics.isRegistered());
    }
}
