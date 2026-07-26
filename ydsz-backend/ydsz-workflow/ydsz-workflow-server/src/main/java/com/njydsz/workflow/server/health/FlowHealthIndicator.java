package com.njydsz.workflow.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import com.njydsz.common.redis.service.RedisService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.flow", name = "health-enabled", havingValue = "true", matchIfMissing = true)
public class FlowHealthIndicator implements HealthIndicator {

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
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // 检查 Redis 连通性（可选依赖）
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                String ping = redisTemplate.execute(conn -> conn.ping(), true);
                details.put("redis", "UP - " + ping);
            } catch (Exception e) {
                details.put("redis", "DOWN - " + e.getMessage());
                return Health.down().withDetails(details).build();
            }
        } else {
            details.put("redis", "UNKNOWN - not configured");
        }

        // 检查流程实例表可达性 + 运行中实例数
        try {
            Long runningCount = instanceMapper.selectCount(
                    new LambdaQueryWrapper<FlowInstanceDO>()
                            .eq(FlowInstanceDO::getFlowStatus, FlowInstanceStatus.RUNNING.name()));
            details.put("flowInstance", "UP - running: " + runningCount);
        } catch (Exception e) {
            details.put("flowInstance", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // 检查待办任务表可达性 + 待办数
        try {
            Long pendingCount = runTaskMapper.selectCount(
                    new LambdaQueryWrapper<FlowRunTaskDO>()
                            .eq(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.PENDING.name()));
            details.put("flowTask", "UP - pending: " + pendingCount);
        } catch (Exception e) {
            details.put("flowTask", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        return Health.up().withDetails(details).build();
    }
}
