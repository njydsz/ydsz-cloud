package com.njydsz.agent.infra.checkpoint;

import java.time.Duration;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.agent.DagCheckpoint;
import com.njydsz.agent.domain.gateway.DagCheckpointStore;
import com.njydsz.agent.domain.state.AgentStateKey;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 基于 Redis 的 DAG 检查点存储
 *
 * <p>将检查点序列化为 JSON 存入 Redis，键由统一的 {@link AgentStateKey} 生成：
 * {@code ydsz:agent:checkpoint:default:default:{executionId}}，与运行时会话、工作区
 * 共用同一套分区命名规则（此前为独立的 {@code agent:dag:checkpoint:} 前缀）。
 *
 * <p>TTL 由 ydsz.agent.dag.checkpoint-ttl-hours 配置（默认 24 小时）：覆盖绝大多数续跑窗口（原执行超时后通常数分钟内触发续跑），过期自动清理避免 Redis 内存无界增长。
 * 键格式迁移期间，旧前缀下的检查点将在其 TTL 到期后自然失效（最长 24 小时内的续跑能力降级为重新执行）。
 *
 * <p>降级策略：Redis 不可用时静默跳过（不中断主流程），续跑能力暂时失效但编排本身仍可执行。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class RedisDagCheckpointStore implements DagCheckpointStore {

  /** 检查点 TTL（小时），默认 24 小时，覆盖绝大多数续跑窗口 */
  @Value("${ydsz.agent.dag.checkpoint-ttl-hours:24}")
  private long checkpointTtlHours;

  private final RedisStringOps redisStringOps;

  public RedisDagCheckpointStore(RedisStringOps redisStringOps) {
    this.redisStringOps = redisStringOps;
  }

  /** {@inheritDoc} */
  @Override
  public void save(DagCheckpoint checkpoint) {
    if (checkpoint == null) {
      return;
    }
    String key = buildKey(checkpoint.getExecutionId());
    try {
      String json = YdszJson.toJson(checkpoint);
      redisStringOps.set(key, json, Duration.ofHours(checkpointTtlHours));
      log.debug("[DagCheckpoint] 保存检查点: executionId={}", checkpoint.getExecutionId());
    } catch (Exception e) {
      log.warn("[DagCheckpoint] Redis 保存检查点失败，续跑能力降级: executionId={}, err={}",
          checkpoint.getExecutionId(), e.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public Optional<DagCheckpoint> load(String executionId) {
    if (executionId == null || executionId.isBlank()) {
      return Optional.empty();
    }
    String key = buildKey(executionId);
    try {
      String json = redisStringOps.get(key, String.class);
      if (json == null || json.isBlank()) {
        return Optional.empty();
      }
      DagCheckpoint checkpoint = YdszJson.fromJson(json, DagCheckpoint.class);
      return Optional.ofNullable(checkpoint);
    } catch (Exception e) {
      log.warn("[DagCheckpoint] Redis 加载检查点失败: executionId={}, err={}", executionId, e.getMessage());
      return Optional.empty();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void delete(String executionId) {
    if (executionId == null || executionId.isBlank()) {
      return;
    }
    String key = buildKey(executionId);
    try {
      redisStringOps.del(key);
      log.debug("[DagCheckpoint] 删除检查点: executionId={}", executionId);
    } catch (Exception e) {
      log.warn("[DagCheckpoint] Redis 删除检查点失败: executionId={}, err={}", executionId, e.getMessage());
    }
  }

  private static String buildKey(String executionId) {
    return AgentStateKey.ofBusiness(AgentStateKey.NAMESPACE_CHECKPOINT, executionId).toStorageKey();
  }
}
