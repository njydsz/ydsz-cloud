package com.njydsz.agent.domain.state;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Agent 状态存储门面 — 统一管理 Agent 各类运行时状态的读写。
 *
 * <p>本接口收敛此前各自为政的多个状态存储（运行时会话、工作区、DAG 检查点），
 * 统一采用 {@link AgentStateKey} 的「命名空间 + 租户 + 用户 + 会话」分区规则，
 * 使同一会话的状态在任意副本上可被同一键寻址，为水平扩展下的跨实例会话恢复提供基础。
 *
 * <p><b>对标 AgentScope</b>：对应其 {@code DistributedBackend} 门面思想——
 * 用一个统一入口替代分散的 stateStore / baseStore / snapshotSpec 配置；
 * 本接口仅保留单一 Redis 后端的实现，不引入多后端矩阵。
 *
 * <p><b>实现约束</b>：
 * <ul>
 *   <li>实现须保证读写线程安全，并支持 TTL 自动过期</li>
 *   <li>实现应做降级容错：存储不可用时记录告警并返回空结果，不中断主流程</li>
 *   <li>存储内容统一按 JSON 序列化，避免引入额外的序列化协议</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.14
 */
public interface AgentStateStore {

  /**
   * 写入状态（覆盖写）。
   *
   * @param key 状态分区键
   * @param value 状态值（实现负责序列化）
   * @param ttl 存活时间（null 表示不过期）
   */
  void put(AgentStateKey key, Object value, Duration ttl);

  /**
   * 读取状态。
   *
   * @param key 状态分区键
   * @param type 期望的反序列化类型
   * @param <T> 状态类型
   * @return 状态值；不存在或读取失败时返回空
   */
  <T> Optional<T> get(AgentStateKey key, Class<T> type);

  /**
   * 判断状态是否存在。
   *
   * @param key 状态分区键
   * @return true=存在
   */
  boolean exists(AgentStateKey key);

  /**
   * 删除状态。
   *
   * @param key 状态分区键
   */
  void remove(AgentStateKey key);

  /**
   * 列举指定分区下的所有状态键。
   *
   * @param partitionKey 分区键（命名空间 + 租户 + 用户 + 会话）
   * @return 该分区下的存储键集合
   */
  Set<String> keysOfPartition(AgentStateKey partitionKey);

  /**
   * 列举指定命名空间下的所有状态键（跨租户/用户/会话）。
   *
   * <p>用于运行时面板、运维审计等需要遍历全局状态的场景；
   * 返回的存储键可由 {@link AgentStateKey#parse(String)} 还原为分区键后再读取值。
   *
   * @param namespace 命名空间（如 {@link AgentStateKey#NAMESPACE_SESSION}）
   * @return 该命名空间下的存储键集合（读取失败时返回空集合）
   */
  Set<String> keysOfNamespace(String namespace);

  /**
   * 获取存储后端标识（用于监控与日志）。
   *
   * @return 后端标识（如 "redis"、"memory"）
   */
  String getBackendType();
}
