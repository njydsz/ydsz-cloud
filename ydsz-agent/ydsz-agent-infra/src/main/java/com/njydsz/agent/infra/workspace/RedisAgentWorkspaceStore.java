package com.njydsz.agent.infra.workspace;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.state.AgentStateKey;
import com.njydsz.agent.domain.state.AgentStateStore;
import com.njydsz.agent.domain.workspace.AgentWorkspace;
import com.njydsz.agent.domain.workspace.AgentWorkspaceStore;

/**
 * Redis 工作区存储实现 — 生产环境跨副本共享 Agent 工作区状态。
 *
 * <p>工作区按业务标识（Agent 编码）分区，值统一 JSON 序列化写入统一状态存储
 * （见 {@link AgentStateStore}），并设置 TTL 自动过期，避免长期不活跃的工作区
 * 无界占用 Redis 内存。
 *
 * <p><b>对标 AgentScope</b>：对应 AgentScope 的 RedisAgentStateStore 后端，
 * 使同一 Agent 在多副本间共享运行时状态。
 *
 * <p><b>启用方式</b>：{@code ydsz.agent.workspace.backend=redis}（默认 memory）。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.agent.workspace",
    name = "backend",
    havingValue = "redis",
    matchIfMissing = false)
public class RedisAgentWorkspaceStore implements AgentWorkspaceStore {

  /** 存储后端标识 */
  private static final String BACKEND_TYPE = "redis";

  /** 工作区状态 TTL（小时），默认 7 天：工作区为长周期状态，需比会话状态保留更久 */
  private static final long WORKSPACE_TTL_HOURS = 168L;

  /** 工作区映射初始容量 */
  private static final int WORKSPACE_MAP_CAPACITY = 8;

  private final AgentStateStore stateStore;

  /**
   * 构造 Redis 工作区存储。
   *
   * @param stateStore 统一状态存储门面
   */
  public RedisAgentWorkspaceStore(AgentStateStore stateStore) {
    this.stateStore = stateStore;
  }

  @Override
  public AgentWorkspace load(String workspaceId) {
    if (workspaceId == null || workspaceId.isBlank()) {
      return null;
    }
    Optional<Map<String, Object>> raw = loadRawState(key(workspaceId));
    if (raw.isEmpty()) {
      return null;
    }
    try {
      return toWorkspace(workspaceId, raw.get());
    } catch (Exception e) {
      log.warn("[Workspace-Redis] 工作区反序列化失败: id={}, error={}", workspaceId, e.getMessage());
      return null;
    }
  }

  @Override
  public void save(AgentWorkspace workspace) {
    if (workspace == null || workspace.getWorkspaceId() == null) {
      return;
    }
    stateStore.put(
        key(workspace.getWorkspaceId()),
        toMap(workspace),
        Duration.ofHours(WORKSPACE_TTL_HOURS));
    log.debug(
        "[Workspace-Redis] 保存工作区: id={}, version={}",
        workspace.getWorkspaceId(),
        workspace.getVersion());
  }

  @Override
  public void delete(String workspaceId) {
    if (workspaceId == null || workspaceId.isBlank()) {
      return;
    }
    stateStore.remove(key(workspaceId));
    log.debug("[Workspace-Redis] 删除工作区: id={}", workspaceId);
  }

  @Override
  public boolean exists(String workspaceId) {
    if (workspaceId == null || workspaceId.isBlank()) {
      return false;
    }
    return stateStore.exists(key(workspaceId));
  }

  @Override
  public String getBackendType() {
    return BACKEND_TYPE;
  }

  /**
   * 构造工作区分区键。
   *
   * @param workspaceId 工作区 ID（Agent 编码）
   * @return 工作区分区键
   */
  private static AgentStateKey key(String workspaceId) {
    return AgentStateKey.ofBusiness(AgentStateKey.NAMESPACE_WORKSPACE, workspaceId);
  }

  /**
   * 读取底层状态映射（集中处理泛型擦除带来的强转）。
   *
   * @param stateKey 工作区分区键
   * @return 状态映射；不存在时返回空
   */
  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> loadRawState(AgentStateKey stateKey) {
    return stateStore.get(stateKey, (Class<Map<String, Object>>) (Class<?>) Map.class);
  }

  /**
   * 工作区对象转可序列化映射（更新时间按 ISO-8601 字符串存储）。
   *
   * @param workspace 工作区
   * @return 可序列化映射
   */
  private static Map<String, Object> toMap(AgentWorkspace workspace) {
    Map<String, Object> map = new LinkedHashMap<>(WORKSPACE_MAP_CAPACITY);
    map.put("personality", workspace.getPersonality());
    map.put("memory", workspace.getMemory());
    map.put("executionPlan", workspace.getExecutionPlan());
    map.put("skills", workspace.getSkills());
    map.put("version", workspace.getVersion());
    map.put("updatedAt", workspace.getUpdatedAt() != null ? workspace.getUpdatedAt().toString() : null);
    return map;
  }

  /**
   * 映射还原为工作区对象。
   *
   * @param workspaceId 工作区 ID
   * @param map 工作区映射
   * @return 工作区实例
   */
  private static AgentWorkspace toWorkspace(String workspaceId, Map<String, Object> map) {
    return new AgentWorkspace(
        workspaceId,
        asString(map.get("personality")),
        asString(map.get("memory")),
        asString(map.get("executionPlan")),
        asString(map.get("skills")),
        asLong(map.get("version")),
        parseDateTime(map.get("updatedAt")));
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
   * 安全转换为长整型。
   *
   * @param value 原始值
   * @return 长整型；无法转换时返回 1（工作区初始版本）
   */
  private static long asLong(Object value) {
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    if (value == null) {
      return 1L;
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 1L;
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
