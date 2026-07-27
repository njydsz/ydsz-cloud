package com.njydsz.project.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.project.server.metrics.ProjectMetrics;
import com.njydsz.project.domain.repository.project.IProjectInitiationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.health.Health;
import org.springframework.boot.health.HealthIndicator;

/**
 * 项目模块健康检查。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ProjectHealthIndicator implements HealthIndicator {

    private final IProjectInitiationRepository projectInitiationRepository;
    private final ProjectMetrics projectMetrics;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            long projectCount = projectInitiationRepository.count();
            details.put("projectInitiationCount", projectCount);
            details.put("metricsRegistered", projectMetrics.isRegistered());
            return Health.up().withDetails(details).build();
        } catch (Exception e) {
            log.warn("Project health check failed", e);
            details.put("error", e.getMessage());
            return Health.down().withDetails(details).build();
        }
    }
}
