package com.njydsz.agent.infra.state;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.state.AgentStateKey;
import com.njydsz.agent.domain.state.AgentStateStore;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * Redis 状态存储实现 — 统一承载 Agent 运行时状态的跨副本共享。
 *
 * <p>按 {@link AgentStateKey} 的四段式分区键读写，状态值统一 JSON 序列化，
 * 并支持 TTL 自动过期（避免 Redis 内存无界增长）。
 *
 * <p><b>降级策略</b>：Redis 不可用或序列化失败时记录告警并返回空结果，
 * 不中断 Agent 主流程（状态持久化属于增强能力，非关键路径）。
 *
 * <p><b>对标 AgentScope</b>：对应其 {@code RedisAgentStateStore}，
 * 使同一会话的状态可被任意副本按同一键寻址。
 *
 * <p><b>装配说明</b>：本类为 {@code AgentStateStore} 的唯一实现，直接由组件扫描注册，
 * 不使用 {@code @ConditionalOnBean}——该注解作用于被扫描的 {@code @Component} 时依赖
 * 自动配置的注册顺序，存在被误判跳过的风险；Redis 为本模块的必需依赖
 * （{@code AgentAutoConfiguration#conversationMemory} 已强依赖 RedisStringOps），
 * 因此无需条件装配。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Slf4j
@Component
public class RedisAgentStateStore implements AgentStateStore {

  /** 存储后端标识 */
  private static final String BACKEND_TYPE = "redis";

  /** 扫描键数量上限，防止大数据量场景拉取过多键 */
  private static final int SCAN_MAX_KEYS = 1000;

  private final RedisStringOps redisStringOps;

  /**
   * 构造 Redis 状态存储。
   *
   * @param redisStringOps Redis String 操作组件
   */
  public RedisAgentStateStore(RedisStringOps redisStringOps) {
    this.redisStringOps = redisStringOps;
  }

  @Override
  public void put(AgentStateKey key, Object value, Duration ttl) {
    if (key == null || value == null) {
      return;
    }
    String storageKey = key.toStorageKey();
    try {
      String json = YdszJson.toJson(value);
      if (ttl == null) {
        redisStringOps.set(storageKey, json);
      } else {
        redisStringOps.set(storageKey, json, ttl);
      }
      log.debug("[AgentState] 写入状态: key={}, ttl={}", storageKey, ttl);
    } catch (Exception e) {
      log.warn("[AgentState] 写入状态失败（降级跳过）: key={}, error={}", storageKey, e.getMessage());
    }
  }

  @Override
  public <T> Optional<T> get(AgentStateKey key, Class<T> type) {
    if (key == null || type == null) {
      return Optional.empty();
    }
    String storageKey = key.toStorageKey();
    try {
      String json = redisStringOps.get(storageKey, String.class);
      if (json == null || json.isBlank()) {
        return Optional.empty();
      }
      return Optional.ofNullable(YdszJson.fromJson(json, type));
    } catch (Exception e) {
      log.warn("[AgentState] 读取状态失败（降级返回空）: key={}, error={}", storageKey, e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public boolean exists(AgentStateKey key) {
    if (key == null) {
      return false;
    }
    try {
      return redisStringOps.hasKey(key.toStorageKey());
    } catch (Exception e) {
      log.warn("[AgentState] 存在性检测失败（降级返回 false）: key={}, error={}",
          key.toStorageKey(), e.getMessage());
      return false;
    }
  }

  @Override
  public void remove(AgentStateKey key) {
    if (key == null) {
      return;
    }
    try {
      redisStringOps.del(key.toStorageKey());
      log.debug("[AgentState] 删除状态: key={}", key.toStorageKey());
    } catch (Exception e) {
      log.warn("[AgentState] 删除状态失败（降级跳过）: key={}, error={}",
          key.toStorageKey(), e.getMessage());
    }
  }

  @Override
  public Set<String> keysOfPartition(AgentStateKey partitionKey) {
    if (partitionKey == null) {
      return Set.of();
    }
    Set<String> keys = redisStringOps.scan(partitionKey.toScanPattern(), SCAN_MAX_KEYS);
    return keys != null ? keys : Set.of();
  }

  @Override
  public Set<String> keysOfNamespace(String namespace) {
    if (namespace == null || namespace.isBlank()) {
      return Set.of();
    }
    Set<String> keys =
        redisStringOps.scan(AgentStateKey.namespaceScanPattern(namespace), SCAN_MAX_KEYS);
    return keys != null ? keys : Set.of();
  }

  @Override
  public String getBackendType() {
    return BACKEND_TYPE;
  }
}
