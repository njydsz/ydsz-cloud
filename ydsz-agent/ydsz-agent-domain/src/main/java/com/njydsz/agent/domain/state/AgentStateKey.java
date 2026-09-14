package com.njydsz.agent.domain.state;

import java.util.Objects;
import java.util.Optional;

/**
 * Agent 状态分区键 — 统一 Agent 运行时状态在存储介质中的命名与分区规则。
 *
 * <p>把 Agent 各类运行时状态（运行时会话、工作区、DAG 检查点、推理中间态）统一表达为
 * 「命名空间 + 租户 + 用户 + 会话」四段式键，解决此前后缀与分区规则各处不一致的问题：
 *
 * <pre>
 * ydsz:agent:{namespace}:{tenantId}:{userId}:{conversationId}[:{suffix}]
 * </pre>
 *
 * <p><b>分区语义</b>：租户/用户/会话三段共同构成状态归属，保证多租户与多用户之间
 * 物理隔离，且同一会话的状态可被任意副本按同一键寻址（跨实例恢复的前提）。
 *
 * <p><b>安全约束</b>：各段在拼接前会剔除分隔符、控制字符并截断超长内容，
 * 防止外部传入的会话 ID 构造出越界键（如 {@code ../} 形式的路径穿越或跨分区覆盖）。
 *
 * <p><b>线程安全</b>：不可变值对象，可安全跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
public final class AgentStateKey {

  /** 运行时会话状态命名空间 */
  public static final String NAMESPACE_SESSION = "session";

  /** 运行时会话执行 ID 索引命名空间（executionId → 会话分区） */
  public static final String NAMESPACE_SESSION_INDEX = "session-index";

  /** 工作区状态命名空间 */
  public static final String NAMESPACE_WORKSPACE = "workspace";

  /** DAG 检查点状态命名空间 */
  public static final String NAMESPACE_CHECKPOINT = "checkpoint";

  /** 键统一前缀（与《云顶编码规范》Redis 键命名约定一致：ydsz:{模块}:...） */
  private static final String KEY_PREFIX = "ydsz:agent:";

  /** 键段分隔符 */
  private static final String SEGMENT_SEPARATOR = ":";

  /** 空段的占位值（避免出现连续分隔符导致键结构歧义） */
  private static final String EMPTY_SEGMENT_PLACEHOLDER = "default";

  /** 单段最大长度（防止超长键与键注入） */
  private static final int MAX_SEGMENT_LENGTH = 64;

  /** 扫描模式通配符 */
  private static final String SCAN_WILDCARD = "*";

  /** 命名空间 */
  private final String namespace;

  /** 租户 ID */
  private final String tenantId;

  /** 用户 ID */
  private final String userId;

  /** 会话 ID（单轮无会话时使用执行 ID 或占位值） */
  private final String conversationId;

  private AgentStateKey(String namespace, String tenantId, String userId, String conversationId) {
    this.namespace = sanitize(Objects.requireNonNull(namespace, "namespace 不能为 null"));
    this.tenantId = sanitize(tenantId);
    this.userId = sanitize(userId);
    this.conversationId = sanitize(conversationId);
  }

  /**
   * 创建状态分区键。
   *
   * @param namespace 命名空间（如 {@link #NAMESPACE_SESSION}）
   * @param tenantId 租户 ID（可为 null，表示无租户上下文）
   * @param userId 用户 ID（可为 null）
   * @param conversationId 会话 ID（可为 null）
   * @return 状态分区键实例
   */
  public static AgentStateKey of(
      String namespace, String tenantId, String userId, String conversationId) {
    return new AgentStateKey(namespace, tenantId, userId, conversationId);
  }

  /**
   * 创建仅按命名空间与业务标识分区的键（无租户/用户/会话维度）。
   *
   * <p>适用于按业务 ID（如 Agent 编码）共享的状态，此时业务标识占据会话段。
   *
   * @param namespace 命名空间
   * @param businessId 业务标识
   * @return 状态分区键实例
   */
  public static AgentStateKey ofBusiness(String namespace, String businessId) {
    return new AgentStateKey(namespace, null, null, businessId);
  }

  /**
   * 生成存储键。
   *
   * @return 完整存储键（{@code ydsz:agent:{namespace}:{tenant}:{user}:{conv}}）
   */
  public String toStorageKey() {
    return KEY_PREFIX
        + namespace
        + SEGMENT_SEPARATOR
        + tenantId
        + SEGMENT_SEPARATOR
        + userId
        + SEGMENT_SEPARATOR
        + conversationId;
  }

  /**
   * 生成带后缀的存储键（用于同一分区下的多类状态，如会话的元数据与快照）。
   *
   * @param suffix 后缀标识
   * @return 完整存储键
   */
  public String toStorageKey(String suffix) {
    if (suffix == null || suffix.isBlank()) {
      return toStorageKey();
    }
    return toStorageKey() + SEGMENT_SEPARATOR + sanitize(suffix);
  }

  /**
   * 生成本分区的扫描模式（用于列举同一会话下的全部状态键）。
   *
   * @return 扫描模式（{@code ydsz:agent:{namespace}:{tenant}:{user}:{conv}:*}）
   */
  public String toScanPattern() {
    return toStorageKey() + SEGMENT_SEPARATOR + SCAN_WILDCARD;
  }

  /**
   * 生成指定命名空间的全局扫描模式（跨租户/用户/会话）。
   *
   * @param namespace 命名空间
   * @return 扫描模式（{@code ydsz:agent:{namespace}:*}）
   */
  public static String namespaceScanPattern(String namespace) {
    return KEY_PREFIX + sanitize(namespace) + SEGMENT_SEPARATOR + SCAN_WILDCARD;
  }

  /**
   * 从存储键还原分区键。
   *
   * <p>仅解析前四段（命名空间 / 租户 / 用户 / 会话），带后缀的键会忽略后缀部分。
   * 用于按命名空间枚举存储键后重新构造分区键以读取状态值。
   *
   * @param storageKey 存储键
   * @return 分区键；键格式非法时返回 {@link Optional#empty()}
   */
  public static Optional<AgentStateKey> parse(String storageKey) {
    if (storageKey == null || !storageKey.startsWith(KEY_PREFIX)) {
      return Optional.empty();
    }
    String body = storageKey.substring(KEY_PREFIX.length());
    String[] segments = body.split(SEGMENT_SEPARATOR, -1);
    int requiredSegments = 4;
    if (segments.length < requiredSegments) {
      return Optional.empty();
    }
    return Optional.of(
        new AgentStateKey(segments[0], segments[1], segments[2], segments[3]));
  }

  public String getNamespace() {
    return namespace;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getUserId() {
    return userId;
  }

  public String getConversationId() {
    return conversationId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AgentStateKey)) {
      return false;
    }
    AgentStateKey that = (AgentStateKey) other;
    return namespace.equals(that.namespace)
        && tenantId.equals(that.tenantId)
        && userId.equals(that.userId)
        && conversationId.equals(that.conversationId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(namespace, tenantId, userId, conversationId);
  }

  @Override
  public String toString() {
    return toStorageKey();
  }

  /**
   * 清洗单个键段：剔除分隔符与控制字符，空值回落占位符，超长截断。
   *
   * @param raw 原始键段
   * @return 可安全拼接的键段
   */
  private static String sanitize(String raw) {
    if (raw == null || raw.isBlank()) {
      return EMPTY_SEGMENT_PLACEHOLDER;
    }
    String cleaned = raw.replace(SEGMENT_SEPARATOR, "").replace(SCAN_WILDCARD, "").trim();
    cleaned = cleaned.replaceAll("\\s", "");
    if (cleaned.isEmpty()) {
      return EMPTY_SEGMENT_PLACEHOLDER;
    }
    return cleaned.length() > MAX_SEGMENT_LENGTH
        ? cleaned.substring(0, MAX_SEGMENT_LENGTH)
        : cleaned;
  }
}
