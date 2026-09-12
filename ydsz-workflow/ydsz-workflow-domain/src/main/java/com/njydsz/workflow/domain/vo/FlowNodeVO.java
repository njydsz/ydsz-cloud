package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;

/**
 * FlowNode 视图对象。
 *
 * <p>提供 ext JSON 的懒解析 getter 方法，避免调用方重复编写解析逻辑。
 * 解析结果缓存在 transient volatile 字段中，同一 VO 多次调用只解析一次。
 *
 * <p>线程安全使用 {@link ReentrantLock} 替代 {@code synchronized} 关键字，
 * 提供等价的互斥语义同时避免内置锁的局限性。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Slf4j
public class FlowNodeVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String definitionId;
  private String flowCode;
  private Integer nodeType;
  private String nodeCode;
  private String nodeName;
  private String permissionFlag;
  private String skipAnyNode;
  private String coordinate;
  private String skipList;
  private String ext;
  private String formFieldsConfig;
  private String slaConfig;
  private String providerTraceId;
  /** 租户标识（对齐实体继承链 MpBaseEntity.tenantId） */
  private String tenantId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;

  /** ext JSON 懒解析缓存（不参与序列化）。 */
  private transient volatile Map<String, Object> parsedExt;

  /** SLA 配置懒解析缓存（不参与序列化）。 */
  private transient volatile SlaConfigVO parsedSlaConfig;

  /** 服务节点配置懒解析缓存（不参与序列化）。 */
  private transient volatile ServiceNodeConfigVO parsedServiceNodeConfig;

  /** 会签配置懒解析缓存（不参与序列化）。 */
  private transient volatile CountersignConfigVO parsedCountersignConfig;

  /** 办理人配置懒解析缓存（不参与序列化）。 */
  private transient volatile AssigneeConfigVO parsedAssigneeConfig;

  /** AI 审批节点配置懒解析缓存（不参与序列化）。 */
  private transient volatile AiAgentNodeConfigVO parsedAiAgentNodeConfig;

  /** 驳回策略配置懒解析缓存（不参与序列化）。 */
  private transient volatile RejectStrategyConfigVO parsedRejectStrategyConfig;

  /** 催办通道配置懒解析缓存（不参与序列化）。 */
  private transient volatile UrgeChannelConfigVO parsedUrgeChannelConfig;

  /** 实例级可重入锁，用于懒解析 double-check（不参与序列化）。 */
  private transient volatile ReentrantLock parseLock;

  // ==================== ext 懒解析基础设施 ====================

  /**
   * 获取实例级解析锁（懒初始化）。
   *
   * <p>使用 DCL 保证 parseLock 单次创建。由于 volatile 语义，最终所有线程都能看到正确的锁实例，
   * 极少数情况下短暂创建两个锁对象不影响正确性（后续 set 会覆盖，volatile 保证可见性）。
   *
   * @return 实例级可重入锁
   */
  private ReentrantLock getParseLock() {
    if (parseLock == null) {
      parseLock = new ReentrantLock();
    }
    return parseLock;
  }

  /**
   * 获取 ext JSON 的解析结果 Map（懒解析、线程安全的 double-check 缓存）。
   *
   * <p>使用 {@link ReentrantLock} 替代 {@code synchronized(this)} 实现互斥。
   *
   * @return ext 对应的 Map，无配置时返回空 Map（非 null）
   */
  public Map<String, Object> getExtMap() {
    if (parsedExt != null) {
      return parsedExt;
    }
    getParseLock().lock();
    try {
      if (parsedExt != null) {
        return parsedExt;
      }
      if (ext == null || ext.isBlank()) {
        parsedExt = Collections.emptyMap();
        return parsedExt;
      }
      try {
        Map<String, Object> map = YdszJson.parseMap(ext);
        parsedExt = map != null ? map : Collections.emptyMap();
        return parsedExt;
      } catch (Exception e) {
        log.warn("[FlowNodeVO] 解析 ext JSON 失败: nodeCode={} err={}", nodeCode, e.getMessage());
        parsedExt = Collections.emptyMap();
        return parsedExt;
      }
    } finally {
      getParseLock().unlock();
    }
  }

  // ==================== 值对象访问（类型安全） ====================

  /**
   * 获取 SLA 超时配置值对象（懒解析、线程安全）。
   *
   * @return SLA 配置值对象（不可变，非 null）
   */
  public SlaConfigVO getSlaConfig() {
    if (parsedSlaConfig != null) {
      return parsedSlaConfig;
    }
    getParseLock().lock();
    try {
      if (parsedSlaConfig != null) {
        return parsedSlaConfig;
      }
      parsedSlaConfig = SlaConfigVO.fromExt(getExtMap());
      return parsedSlaConfig;
    } finally {
      getParseLock().unlock();
    }
  }

  /**
   * 获取 SLA 配置原始 JSON 字符串（数据库原始值）。
   *
   * <p>供需要原始 JSON 的场景使用（如持久化、比较、解析 Map）。
   * 类型安全的值对象访问请使用 {@link #getSlaConfig()}。
   *
   * @return SLA 配置 JSON 字符串，未配置返回 null
   */
  public String getSlaConfigJson() {
    return slaConfig;
  }

  /**
   * 获取服务节点配置值对象（懒解析、线程安全）。
   *
   * @return 服务节点配置值对象（不可变，非 null）
   */
  public ServiceNodeConfigVO getServiceNodeConfig() {
    if (parsedServiceNodeConfig != null) {
      return parsedServiceNodeConfig;
    }
    getParseLock().lock();
    try {
      if (parsedServiceNodeConfig != null) {
        return parsedServiceNodeConfig;
      }
      parsedServiceNodeConfig = ServiceNodeConfigVO.fromExt(getExtMap());
      return parsedServiceNodeConfig;
    } finally {
      getParseLock().unlock();
    }
  }

  /**
   * 获取会签配置值对象（懒解析、线程安全）。
   *
   * @return 会签配置值对象（不可变，非 null）
   */
  public CountersignConfigVO getCountersignConfig() {
    if (parsedCountersignConfig != null) {
      return parsedCountersignConfig;
    }
    getParseLock().lock();
    try {
      if (parsedCountersignConfig != null) {
        return parsedCountersignConfig;
      }
      parsedCountersignConfig = CountersignConfigVO.fromExt(getExtMap());
      return parsedCountersignConfig;
    } finally {
      getParseLock().unlock();
    }
  }

  /**
   * 获取办理人配置值对象（懒解析、线程安全）。
   *
   * @return 办理人配置值对象（不可变，非 null）
   */
  public AssigneeConfigVO getAssigneeConfig() {
    if (parsedAssigneeConfig != null) {
      return parsedAssigneeConfig;
    }
    getParseLock().lock();
    try {
      if (parsedAssigneeConfig != null) {
        return parsedAssigneeConfig;
      }
      parsedAssigneeConfig = AssigneeConfigVO.fromExt(getExtMap());
      return parsedAssigneeConfig;
    } finally {
      getParseLock().unlock();
    }
  }

  /**
   * 获取 AI 审批节点配置值对象（懒解析、线程安全）。
   *
   * @return AI 审批节点配置值对象（不可变，非 null）
   */
  public AiAgentNodeConfigVO getAiAgentNodeConfig() {
    if (parsedAiAgentNodeConfig != null) {
      return parsedAiAgentNodeConfig;
    }
    getParseLock().lock();
    try {
      if (parsedAiAgentNodeConfig != null) {
        return parsedAiAgentNodeConfig;
      }
      parsedAiAgentNodeConfig = AiAgentNodeConfigVO.fromExt(getExtMap());
      return parsedAiAgentNodeConfig;
    } finally {
      getParseLock().unlock();
    }
  }

  /**
   * 获取驳回策略配置值对象（懒解析、线程安全）。
   *
   * @return 驳回策略配置值对象（不可变，非 null）
   */
  public RejectStrategyConfigVO getRejectStrategyConfig() {
    if (parsedRejectStrategyConfig != null) {
      return parsedRejectStrategyConfig;
    }
    getParseLock().lock();
    try {
      if (parsedRejectStrategyConfig != null) {
        return parsedRejectStrategyConfig;
      }
      parsedRejectStrategyConfig = RejectStrategyConfigVO.fromExt(getExtMap());
      return parsedRejectStrategyConfig;
    } finally {
      getParseLock().unlock();
    }
  }

  /**
   * 获取催办通道配置值对象（懒解析、线程安全）。
   *
   * @return 催办通道配置值对象（不可变，非 null）
   */
  public UrgeChannelConfigVO getUrgeChannelConfig() {
    if (parsedUrgeChannelConfig != null) {
      return parsedUrgeChannelConfig;
    }
    getParseLock().lock();
    try {
      if (parsedUrgeChannelConfig != null) {
        return parsedUrgeChannelConfig;
      }
      parsedUrgeChannelConfig = UrgeChannelConfigVO.fromExt(getExtMap());
      return parsedUrgeChannelConfig;
    } finally {
      getParseLock().unlock();
    }
  }

  // ==================== 网关相关 ====================

  /**
   * 获取网关默认出边 sequenceFlowId（BPMN 2.0 default 属性）。
   *
   * @return 默认出边 ID，未配置时返回 null
   */
  public String getDefaultFlowId() {
    Object val = getExtMap().get("defaultFlowId");
    return val == null ? null : String.valueOf(val);
  }

  // ==================== 自动去重 ====================

  /**
   * 判断是否启用跨节点办理人去重。
   *
   * @return true 表示启用去重
   */
  public boolean getAutoDedup() {
    Object val = getExtMap().get("autoDedup");
    if (val == null) {
      return false;
    }
    if (val instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(val));
  }

  // ==================== 并行网关 join 聚合阈值 ====================

  /**
   * 解析并行/包容网关的 join 聚合阈值。
   *
   * <p>支持格式：
   *
   * <ul>
   *   <li>{@code "joinRequired": 3} — 固定数量
   *   <li>{@code "joinRequired": "3/5"} — 分数（5 个分支中需 3 个）
   *   <li>{@code "joinRequired": "majority"} — 过半数
   *   <li>未配置 — 返回 incomingCount（全部到达才聚合）
   * </ul>
   *
   * @param incomingCount 网关的入边总数
   * @return 需要到达的分支数量（1 ~ incomingCount）
   */
  public int getJoinRequired(int incomingCount) {
    Object val = getExtMap().get("joinRequired");
    if (val == null) {
      return incomingCount;
    }
    if (val instanceof Number n) {
      return clamp(n.intValue(), incomingCount);
    }
    String s = String.valueOf(val).trim();
    if ("majority".equalsIgnoreCase(s)) {
      return incomingCount / 2 + 1;
    }
    if (s.contains("/")) {
      String[] parts = s.split("/");
      return clamp(Integer.parseInt(parts[0].trim()), incomingCount);
    }
    return clamp(Integer.parseInt(s), incomingCount);
  }

  // ==================== 表单 Schema ====================

  /**
   * 获取表单 Schema JSON 字符串。
   *
   * @return 表单 Schema JSON，未配置时返回 null
   */
  public String getFormSchemaJson() {
    Object val = getExtMap().get("formSchema");
    return val == null ? null : String.valueOf(val);
  }

  // ==================== 优先级 ====================

  /**
   * 获取节点优先级（1~100）。
   *
   * @return 优先级数值，默认 50
   */
  public int getPriority() {
    Object val = getExtMap().get("priority");
    if (val == null) {
      return DEFAULT_PRIORITY;
    }
    if (val instanceof Number n) {
      return clamp(n.intValue(), 1, 100);
    }
    try {
      return clamp(Integer.parseInt(String.valueOf(val).trim()), 1, 100);
    } catch (NumberFormatException e) {
      return DEFAULT_PRIORITY;
    }
  }

  // ==================== 事件订阅 ====================

  /**
   * 获取事件类型（ERROR / TIMER / SIGNAL / MESSAGE）。
   *
   * @return 事件类型，未配置时返回空字符串
   */
  public String getEventType() {
    Object val = getExtMap().get("eventType");
    return val == null ? "" : String.valueOf(val).toUpperCase();
  }

  /**
   * 获取 boundaryEvent 的attachedToRef（关联的节点编码）。
   *
   * @return 关联节点编码，未配置时返回空字符串
   */
  public String getAttachedToRef() {
    Object val = getExtMap().get("attachedToRef");
    return val == null ? "" : String.valueOf(val);
  }

  /**
   * 获取错误边界事件引用标识。
   *
   * @return 错误引用标识，默认 "SERVICE_ERROR"
   */
  public String getErrorRef() {
    Object val = getExtMap().get("errorRef");
    return val == null ? "SERVICE_ERROR" : String.valueOf(val);
  }

  // ==================== 常量 ====================

  /** 节点默认优先级。 */
  public static final int DEFAULT_PRIORITY = 50;

  /** 默认超时分钟数。 */
  public static final int DEFAULT_TIMEOUT_MINUTES = 120;

  // ==================== 内部工具 ====================

  /**
   * 将值 clamp 到 [1, max] 范围。
   *
   * @param value 待限幅的整数值
   * @param max 上限（最大值）
   * @return clamp 到 [1, max] 范围的结果
   */
  private static int clamp(int value, int max) {
    return Math.min(Math.max(1, value), max);
  }

  /**
   * 将值 clamp 到 [min, max] 范围。
   *
   * @param value 待限幅的整数值
   * @param min 下限（最小值）
   * @param max 上限（最大值）
   * @return clamp 到 [min, max] 范围的结果
   */
  private static int clamp(int value, int min, int max) {
    return Math.min(Math.max(min, value), max);
  }
}
