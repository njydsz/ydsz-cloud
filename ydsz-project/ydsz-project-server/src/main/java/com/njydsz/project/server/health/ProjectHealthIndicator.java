package com.njydsz.project.server.health;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.project.domain.repository.project.IProjectInitiationRepository;
import com.njydsz.project.server.metrics.ProjectMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.health.contributor.Health;

/**
 * 项目模块健康检查 Indicator
 *
 * <p>Spring Boot Actuator 的 {@link org.springframework.boot.health.contributor.HealthIndicator} 实现，
 * 承载 {@code ydsz-project} 微服务的健康检查能力。
 * P2-1: 继承 {@link AbstractModuleHealthIndicator} 统一基类，使用模板方法模式替代直接实现。
 *
 * <p><b>检查项：</b>
 * <ul>
 *   <li><b>项目立项表可达性</b> — 通过 {@code projectInitiationRepository.count()} 探针验证 {@code ydsz_project_initiation} 表可读</li>
 *   <li><b>指标注册状态</b> — 检查 {@link ProjectMetrics#isRegistered()} 确认 Micrometer 指标 Bean 已完成初始化</li>
 * </ul>
 *
 * <p><b>访问端点：</b>{@code GET /actuator/health/project}（由 Actuator 自动暴露）
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AbstractModuleHealthIndicator 通用健康检查基类
 * @see ProjectMetrics 项目模块指标采集器
 */
@Slf4j
@RequiredArgsConstructor
public class ProjectHealthIndicator extends AbstractModuleHealthIndicator {

    private final IProjectInitiationRepository projectInitiationRepository;
    private final ProjectMetrics projectMetrics;

    /**
     * 执行项目模块健康检查
     *
     * <p>按顺序检查：① 项目立项表可达性（count 探针） → ② Micrometer 指标注册状态。
     * 任意一项失败，整体健康状态降级为 {@code DOWN}，但<b>不会中断</b>后续检查项。
     */
    @Override
    protected void doHealthCheck(Health.Builder builder) {
        checkTableProbeWithValue(builder, "projectInitiationCount",
                projectInitiationRepository::count);
        builder.withDetail("metricsRegistered", projectMetrics.isRegistered());
    }
}
