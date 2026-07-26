package com.njydsz.workflow.server.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 工作流模块健康检查。
 *
 * <p>检查项：
 * <ul>
 *   <li>Redis — PING 命令（可选依赖，缺失时标记为 UNKNOWN）</li>
 *   <li>流程实例表 — 轻量探针查询运行中实例数</li>
 *   <li>待办任务表 — 轻量探针查询待办任务数</li>
 *   <li>SLA 超期任务 — 轻量探针查询超期待办数（P2-6 新增）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.flow", name = "health-enabled", havingValue = "true", matchIfMissing = true)
public class FlowHealthIndicator extends AbstractModuleHealthIndicator {

    private final FlowInstanceMapper instanceMapper;
    private final FlowRunTaskMapper runTaskMapper;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public FlowHealthIndicator(FlowInstanceMapper instanceMapper,
                                FlowRunTaskMapper runTaskMapper,
                                ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.instanceMapper = instanceMapper;
        this.runTaskMapper = runTaskMapper;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        // Redis 可选
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            checkRedis(builder, () -> redisTemplate.execute(conn -> conn.ping(), true));
        } else {
            checkRedisNotConfigured(builder);
        }

        // 流程实例探针
        checkTableProbeWithValue(builder, "flowInstance", () -> {
            Long runningCount = instanceMapper.selectCount(
                    new LambdaQueryWrapper<FlowInstanceDO>()
                            .eq(FlowInstanceDO::getFlowStatus, FlowInstanceStatus.RUNNING.name()));
            return "running: " + runningCount;
        });

        // 待办任务探针
        checkTableProbeWithValue(builder, "flowTask", () -> {
            Long pendingCount = runTaskMapper.selectCount(
                    new LambdaQueryWrapper<FlowRunTaskDO>()
                            .eq(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.PENDING.name()));
            return "pending: " + pendingCount;
        });

        // P2-6: SLA 超期任务探针（超过 SLA 时限仍未处理的待办）
        checkTableProbeWithValue(builder, "slaOverdue", () -> {
            try {
                Long overdueCount = runTaskMapper.countOverdue(null, null);
                return "overdue: " + (overdueCount == null ? 0 : overdueCount);
            } catch (Exception e) {
                log.debug("[FlowHealth] SLA 超期查询失败: {}", e.getMessage());
                return "overdue: N/A";
            }
        });
    }
}
