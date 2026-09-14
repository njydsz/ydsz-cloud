package com.njydsz.agent.infra.runtime;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.runtime.RuntimeSession;
import com.njydsz.agent.domain.runtime.RuntimeSessionStore;
import com.njydsz.agent.domain.state.AgentStateKey;
import com.njydsz.agent.domain.state.AgentStateStore;

/**
 * 基于 Redis 的 Agent 运行时会话存储实现 — 支持多副本共享与重启恢复。
 *
 * <p>会话状态按 {@code (tenantId, userId, executionId)} 分区写入统一状态存储
 * （见 {@link AgentStateStore}），并额外维护 {@code executionId → (tenantId, userId)}
 * 的索引键，使仅持有 executionId 的调用方（运行面板、熔断回收任务）也能定位到会话。
 *
 * <p><b>与前身实现的差异</b>：此前的内存实现只在本 JVM 内可见，多副本部署时
 * 运行面板只能看到自己所在副本的会话，且重启即丢失；本实现使会话状态跨副本可见。
 *
 * <p><b>启用方式</b>：{@code ydsz.agent.runtime.backend=redis}（默认 memory，保持单实例零依赖启动）。
 *
 * <p><b>已知约束</b>：列表类查询依赖按命名空间 SCAN，若 Redis 侧启用了租户键前缀，
 * 全局扫描可能无法命中带前缀的键；此时按租户查询（{@link #findActiveSessionsByTenant}）
 * 仍可正常工作，因为租户信息已固化在会话数据中。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.agent.runtime",
    name = "backend",
    havingValue = "redis",
    matchIfMissing = false)
public class RedisRuntimeSessionStore implements RuntimeSessionStore {

  /** 会话状态 TTL（小时），默认 24 小时，与 DAG 检查点续跑窗口保持一致 */
  private static final long DEFAULT_SESSION_TTL_HOURS = 24L;

  /** 默认列表查询上限 */
  private static final int DEFAULT_FIND_ALL_LIMIT = 200;

  /** 会话映射初始容量 */
  private static final int SESSION_MAP_CAPACITY = 20;

  /** 索引值分隔符（分区键各段已清洗分隔符，故不会产生歧义） */
  private static final String INDEX_SEPARATOR = "|";

  /** 索引值段数 */
  private static final int INDEX_SEGMENT_COUNT = 2;

  /** 按开始时间倒序比较器（空值排后） */
  private static final Comparator<RuntimeSession> START_TIME_DESC =
      Comparator.comparing(
          RuntimeSession::getStartTime, Comparator.nullsLast(Comparator.reverseOrder()));

  /** 会话状态 TTL（小时） */
  @Value("${ydsz.agent.runtime.session-ttl-hours:24}")
  private long sessionTtlHours = DEFAULT_SESSION_TTL_HOURS;

  private final AgentStateStore stateStore;

  /**
   * 构造 Redis 运行时会话存储。
   *
   * @param stateStore 统一状态存储门面
   */
  public RedisRuntimeSessionStore(AgentStateStore stateStore) {
    this.stateStore = stateStore;
  }

  @Override
  public void save(RuntimeSession session) {
    if (session == null || session.getExecutionId() == null) {
      return;
    }
    Duration ttl = Duration.ofHours(sessionTtlHours);
    stateStore.put(sessionKey(session.getTenantId(), session.getUserId(), session.getExecutionId()),
        toMap(session), ttl);
    stateStore.put(
        AgentStateKey.ofBusiness(AgentStateKey.NAMESPACE_SESSION_INDEX, session.getExecutionId()),
        buildIndexValue(session),
        ttl);
    log.debug("[RuntimeSession-Redis] 保存会话: executionId={}, status={}",
        session.getExecutionId(), session.getStatus());
  }

  @Override
  public Optional<RuntimeSession> findByExecutionId(String executionId) {
    if (executionId == null || executionId.isBlank()) {
      return Optional.empty();
    }
    String indexValue =
        stateStore
            .get(
                AgentStateKey.ofBusiness(AgentStateKey.NAMESPACE_SESSION_INDEX, executionId),
                String.class)
            .orElse(null);
    if (indexValue == null) {
      return Optional.empty();
    }
    String[] segments = indexValue.split("\\" + INDEX_SEPARATOR, -1);
    if (segments.length < INDEX_SEGMENT_COUNT) {
      log.warn("[RuntimeSession-Redis] 索引值格式非法: executionId={}", executionId);
      return Optional.empty();
    }
    return loadSession(sessionKey(segments[0], segments[1], executionId));
  }

  @Override
  public List<RuntimeSession> findActiveSessions() {
    return loadAll(Integer.MAX_VALUE).stream().filter(RuntimeSession::isActive).toList();
  }

  @Override
  public List<RuntimeSession> findActiveSessionsByTenant(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return List.of();
    }
    return loadAll(Integer.MAX_VALUE).stream()
        .filter(RuntimeSession::isActive)
        .filter(session -> tenantId.equals(session.getTenantId()))
        .toList();
  }

  @Override
  public void remove(String executionId) {
    if (executionId == null || executionId.isBlank()) {
      return;
    }
    findByExecutionId(executionId)
        .ifPresent(
            session ->
                stateStore.remove(
                    sessionKey(
                        session.getTenantId(), session.getUserId(), session.getExecutionId())));
    stateStore.remove(
        AgentStateKey.ofBusiness(AgentStateKey.NAMESPACE_SESSION_INDEX, executionId));
    log.debug("[RuntimeSession-Redis] 移除会话: executionId={}", executionId);
  }

  @Override
  public List<RuntimeSession> findAll(int limit) {
    int effectiveLimit = limit > 0 ? limit : DEFAULT_FIND_ALL_LIMIT;
    return loadAll(effectiveLimit);
  }

  @Override
  public long countActive() {
    return findActiveSessions().size();
  }

  /**
   * 加载全部会话（按开始时间倒序，最多 limit 条）。
   *
   * @param limit 返回数量上限
   * @return 会话列表
   */
  private List<RuntimeSession> loadAll(int limit) {
    Set<String> storageKeys = stateStore.keysOfNamespace(AgentStateKey.NAMESPACE_SESSION);
    List<RuntimeSession> sessions = new ArrayList<>(storageKeys.size());
    for (String storageKey : storageKeys) {
      Optional<AgentStateKey> parsed = AgentStateKey.parse(storageKey);
      if (parsed.isEmpty()) {
        continue;
      }
      loadSession(parsed.get()).ifPresent(sessions::add);
    }
    sessions.sort(START_TIME_DESC);
    if (sessions.size() > limit) {
      return sessions.subList(0, limit);
    }
    return sessions;
  }

  /**
   * 读取单个会话状态。
   *
   * @param key 会话分区键
   * @return 会话；不存在或反序列化失败时返回空
   */
  private Optional<RuntimeSession> loadSession(AgentStateKey key) {
    Optional<Map<String, Object>> raw = loadRawState(key);
    if (raw.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(toSession(raw.get()));
    } catch (Exception e) {
      log.warn("[RuntimeSession-Redis] 会话反序列化失败: key={}, error={}", key, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * 读取底层状态映射（集中处理泛型擦除带来的强转）。
   *
   * @param key 会话分区键
   * @return 状态映射；不存在时返回空
   */
  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> loadRawState(AgentStateKey key) {
    return stateStore.get(key, (Class<Map<String, Object>>) (Class<?>) Map.class);
  }

  /**
   * 构造会话分区键。
   *
   * @param tenantId 租户 ID
   * @param userId 用户 ID
   * @param executionId 执行 ID（作为会话段，保证同一执行可被唯一寻址）
   * @return 会话分区键
   */
  private static AgentStateKey sessionKey(String tenantId, String userId, String executionId) {
    return AgentStateKey.of(AgentStateKey.NAMESPACE_SESSION, tenantId, userId, executionId);
  }

  /**
   * 构造执行 ID 索引值。
   *
   * @param session 运行时会话
   * @return 索引值（{@code tenantId|userId}）
   */
  private static String buildIndexValue(RuntimeSession session) {
    return (session.getTenantId() != null ? session.getTenantId() : "")
        + INDEX_SEPARATOR
        + (session.getUserId() != null ? session.getUserId() : "");
  }

  /**
   * 会话对象转可序列化映射（时间字段按 ISO-8601 字符串存储，避免依赖时间类型的 JSON 编解码器）。
   *
   * @param session 运行时会话
   * @return 可序列化映射
   */
  private static Map<String, Object> toMap(RuntimeSession session) {
    Map<String, Object> map = new LinkedHashMap<>(SESSION_MAP_CAPACITY);
    map.put("executionId", session.getExecutionId());
    map.put("conversationId", session.getConversationId());
    map.put("agentCode", session.getAgentCode());
    map.put("agentType", session.getAgentType());
    map.put("tenantId", session.getTenantId());
    map.put("userId", session.getUserId());
    map.put("model", session.getModel());
    map.put("status", session.getStatus());
    map.put("currentStep", session.getCurrentStep());
    map.put("currentIteration", session.getCurrentIteration());
    map.put("maxIterations", session.getMaxIterations());
    map.put("totalTokens", session.getTotalTokens());
    map.put("costUsd", session.getCostUsd());
    map.put("startTime", session.getStartTime() != null ? session.getStartTime().toString() : null);
    map.put(
        "lastActiveTime",
        session.getLastActiveTime() != null ? session.getLastActiveTime().toString() : null);
    map.put("source", session.getSource());
    map.put("errorMessage", session.getErrorMessage());
    return map;
  }

  /**
   * 映射还原为会话对象。
   *
   * @param map 会话映射
   * @return 运行时会话
   */
  private static RuntimeSession toSession(Map<?, ?> map) {
    return RuntimeSession.builder()
        .executionId(asString(map.get("executionId")))
        .conversationId(asString(map.get("conversationId")))
        .agentCode(asString(map.get("agentCode")))
        .agentType(asString(map.get("agentType")))
        .tenantId(asString(map.get("tenantId")))
        .userId(asString(map.get("userId")))
        .model(asString(map.get("model")))
        .status(asString(map.get("status")))
        .currentStep(asString(map.get("currentStep")))
        .currentIteration(asInt(map.get("currentIteration")))
        .maxIterations(asInt(map.get("maxIterations")))
        .totalTokens(asInt(map.get("totalTokens")))
        .costUsd(asDouble(map.get("costUsd")))
        .startTime(parseDateTime(map.get("startTime")))
        .lastActiveTime(parseDateTime(map.get("lastActiveTime")))
        .source(asString(map.get("source")))
        .errorMessage(asString(map.get("errorMessage")))
        .build();
  }

  /**
   * 安全转换为字符串。
   *
   * @param value 原始值
   * @return 字符串；原始值为 null 时返回 null
   */
  private static String asString(Object value) {
    return value != null ? String.valueOf(value) : null;
  }

  /**
   * 安全转换为整型。
   *
   * @param value 原始值
   * @return 整型；无法转换时返回 0
   */
  private static int asInt(Object value) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * 安全转换为双精度浮点。
   *
   * @param value 原始值
   * @return 双精度值；无法转换时返回 0
   */
  private static double asDouble(Object value) {
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    if (value == null) {
      return 0D;
    }
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0D;
    }
  }

  /**
   * 解析 ISO-8601 日期时间字符串。
   *
   * @param value 原始值
   * @return 日期时间；为空或格式非法时返回 null
   */
  private static LocalDateTime parseDateTime(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return LocalDateTime.parse(String.valueOf(value));
    } catch (Exception e) {
      return null;
    }
  }
}
