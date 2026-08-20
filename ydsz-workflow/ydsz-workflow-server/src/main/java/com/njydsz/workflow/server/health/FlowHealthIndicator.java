package com.njydsz.workflow.server.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;

/**
 * 工作流模块健康检查。
 *
 * <p>检查项：
 *
 * <ul>
 *   <li>Redis — PING 命令（可选依赖，缺失时标记为 UNKNOWN）
 *   <li>流程实例表 — 轻量探针查询运行中实例数
 *   <li>待办任务表 — 轻量探针查询待办任务数
 *   <li>SLA 超期任务 — 轻量探针查询超期待办数（P2-6 新增）
 * </ul>
 *
 * <p><b>架构合规说明（v2.23 DDD 分层规范修复）：</b>通过 domain 层 Repository 接口访问数据，
 * 禁止 server 层直接注入 infra Mapper（符合 §34.2.3）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(
    prefix = "ydsz.flow",
    name = "health-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class FlowHealthIndicator extends AbstractModuleHealthIndicator {

  private final FlowInstanceRepository instanceRepository;
  private final FlowRunTaskRepository runTaskRepository;
  private final ObjectProvider<RedisStringOps> redisServiceProvider;

  public FlowHealthIndicator(
      FlowInstanceRepository instanceRepository,
      FlowRunTaskRepository runTaskRepository,
      ObjectProvider<RedisStringOps> redisServiceProvider) {
    this.instanceRepository = instanceRepository;
    this.runTaskRepository = runTaskRepository;
    this.redisServiceProvider = redisServiceProvider;
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    // Redis 可选
    RedisStringOps redisService = redisServiceProvider.getIfAvailable();
    if (redisService != null) {
      checkRedis(
          builder,
          () -> {
            redisService.hasKey("__flow_health_check__");
            return "PONG";
          });
    } else {
      checkRedisNotConfigured(builder);
    }

    // 流程实例探针 — 通过 Repository 获取运行中实例数（符合 §34.2.3，禁止直接注入 Mapper）
    checkTableProbeWithValue(
        builder,
        "FlowInstance",
        () -> {
          Long runningCount = instanceRepository.countByStatus("RUNNING");
          return "running: " + runningCount;
        });

    // 待办任务探针 — 通过 Repository 获取待办任务数
    checkTableProbeWithValue(
        builder,
        "flowTask",
        () -> {
          Long pendingCount = runTaskRepository.countPending();
          return "pending: " + pendingCount;
        });

    // P2-6: SLA 超期任务探针（超过 SLA 时限仍未处理的待办）
    checkTableProbeWithValue(
        builder,
        "slaOverdue",
        () -> {
          try {
            Long overdueCount = runTaskRepository.countOverdue();
            return "overdue: " + (overdueCount == null ? 0 : overdueCount);
          } catch (Exception e) {
            log.debug("[FlowHealth] SLA 超期查询失败: {}", e.getMessage());
            return "overdue: N/A";
          }
        });
  }
}
