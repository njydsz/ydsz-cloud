package com.njydsz.agent.server.execution;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.execution.ExecutionCheckpoint;
import com.njydsz.agent.domain.state.AgentStateKey;
import com.njydsz.agent.domain.state.AgentStateStore;

/**
 * 执行暂停服务 — 内存管理 {@link ExecutionCheckpoint}。
 *
 * <p>按 {@code approvalId} 保存/查询/删除检查点，供会话级暂停/恢复使用。
 * 当前为单实例内存实现；多副本部署下审批回调可能路由到不同实例，
 * 后续可替换为 {@code AgentStateStore} 持久化实现。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Slf4j
@Service
public class ExecutionPauseService {

  /** 检查点默认 TTL：1 小时 */
  private static final Duration DEFAULT_TTL = Duration.ofHours(1);

  /** 检查点内存缓存（单实例或 Redis 不可用时兜底） */
  private final ConcurrentMap<String, ExecutionCheckpoint> checkpoints = new ConcurrentHashMap<>();

  /** 可选的分布式状态存储（多副本部署时共享检查点） */
  private final AgentStateStore stateStore;

  /** 检查点 TTL */
  private final Duration ttl;

  /**
   * 默认构造（内存实现，单实例部署）。
   */
  public ExecutionPauseService() {
    this(null, DEFAULT_TTL);
  }

  /**
   * Spring 构造（自动注入可选的 AgentStateStore）。
   *
   * @param stateStoreProvider 分布式状态存储提供者
   * @param ttlHours 检查点 TTL（小时）
   */
  @Autowired
  public ExecutionPauseService(
      ObjectProvider<AgentStateStore> stateStoreProvider,
      @Value("${ydsz.agent.approval.pause-ttl-hours:1}") int ttlHours) {
    this(stateStoreProvider.getIfAvailable(), Duration.ofHours(ttlHours));
  }

  /**
   * 全参构造。
   *
   * @param stateStore 分布式状态存储（null 表示内存兜底）
   * @param ttl 检查点 TTL
   */
  public ExecutionPauseService(AgentStateStore stateStore, Duration ttl) {
    this.stateStore = stateStore;
    this.ttl = ttl != null ? ttl : DEFAULT_TTL;
  }

  /**
   * 保存执行检查点。
   *
   * <p>若配置了分布式状态存储，优先写入存储以实现多副本共享；
   * 同时保留内存缓存加速本机查询。
   *
   * @param approvalId 审批请求 ID
   * @param checkpoint 检查点
   */
  public void save(String approvalId, ExecutionCheckpoint checkpoint) {
    checkpoints.put(approvalId, checkpoint);
    if (stateStore != null) {
      try {
        stateStore.put(buildKey(approvalId), checkpoint, ttl);
      } catch (Exception e) {
        log.warn("[ExecutionPause] 分布式存储写入失败（保留内存缓存）: approvalId={}", approvalId);
      }
    }
    log.info("[ExecutionPause] 保存检查点: approvalId={}", approvalId);
  }

  /**
   * 按审批 ID 查询检查点。
   *
   * <p>优先查内存缓存；未命中且存在分布式存储时回查存储，命中后回填内存缓存。
   *
   * @param approvalId 审批请求 ID
   * @return 检查点；不存在时返回 empty
   */
  public Optional<ExecutionCheckpoint> find(String approvalId) {
    ExecutionCheckpoint cached = checkpoints.get(approvalId);
    if (cached != null) {
      return Optional.of(cached);
    }
    if (stateStore == null) {
      return Optional.empty();
    }
    try {
      Optional<ExecutionCheckpoint> fromStore =
          stateStore.get(buildKey(approvalId), ExecutionCheckpoint.class);
      fromStore.ifPresent(cp -> checkpoints.put(approvalId, cp));
      return fromStore;
    } catch (Exception e) {
      log.warn("[ExecutionPause] 分布式存储查询失败: approvalId={}", approvalId);
      return Optional.empty();
    }
  }

  /**
   * 删除检查点。
   *
   * @param approvalId 审批请求 ID
   */
  public void discard(String approvalId) {
    checkpoints.remove(approvalId);
    if (stateStore != null) {
      try {
        stateStore.remove(buildKey(approvalId));
      } catch (Exception e) {
        log.warn("[ExecutionPause] 分布式存储删除失败: approvalId={}", approvalId);
      }
    }
    log.info("[ExecutionPause] 删除检查点: approvalId={}", approvalId);
  }

  /**
   * 获取当前缓存的检查点数量（用于监控）。
   *
   * @return 数量
   */
  public int size() {
    return checkpoints.size();
  }

  /**
   * 构建检查点状态键。
   *
   * @param approvalId 审批请求 ID
   * @return 状态分区键
   */
  private AgentStateKey buildKey(String approvalId) {
    return AgentStateKey.ofBusiness(AgentStateKey.NAMESPACE_CHECKPOINT, approvalId);
  }
}
