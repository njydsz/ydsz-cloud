package com.njydsz.agent.infra.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageContent;
import com.njydsz.agent.domain.model.MessageRole;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.tenant.TenantContextHolder;

/**
 * Redis 对话记忆实现
 *
 * <p>使用 Redis List 存储对话消息（RPUSH + LTRIM 实现滑动窗口）。 每个对话的 key 格式：{@code
 * ydsz:agent:memory:{conversationId}} 默认 TTL 24 小时，可通过配置调整。
 *
 * <p><b>多租户隔离（P0 修复）</b>：存在租户上下文时 key 追加 {@code {tenantId}:} 段 （{@code
 * ydsz:agent:memory:{tenantId}:{conversationId}}），避免跨租户会话串扰； 无租户上下文（单租户部署）时保持原 key 格式，向后兼容。
 *
 * <h3>防无限增长</h3>
 *
 * <p>每次 RPUSH 后执行 LTRIM，将 List 截断为 {@code maxListSize} 条， 避免单对话消息数无限膨胀。{@code maxListSize} 默认为
 * {@code maxMessages * 2}， 保留一定余量供滑动窗口检索。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RedisConversationMemory implements ConversationMemory {

  /** Redis key 前缀 */
  private static final String KEY_PREFIX = "ydsz:agent:memory:";

  /** 默认 TTL（小时） */
  private static final int DEFAULT_TTL_HOURS = 24;

  /** 默认最大列表大小 */
  private static final int DEFAULT_MAX_LIST_SIZE = 50;

  /** 剩余 TTL 低于此阈值（秒）时才执行续期，避免活跃长对话被每次 save 无限续期 */
  private static final long TTL_RENEW_THRESHOLD_SECONDS = 3600L;

  /** 一小时的秒数 */
  private static final long SECONDS_PER_HOUR = 3600L;

  /** String 操作组件（expire / delete / hasKey） */
  private final RedisStringOps stringOps;

  /** 集合操作组件（rPush / lTrim / lSize / lRange） */
  private final RedisCollectionOps collectionOps;

  /** TTL（小时） */
  private final int ttlHours;

  /** 最大列表大小 */
  private final int maxListSize;

  public RedisConversationMemory(RedisStringOps stringOps, RedisCollectionOps collectionOps) {
    this(stringOps, collectionOps, DEFAULT_TTL_HOURS, DEFAULT_MAX_LIST_SIZE);
  }

  public RedisConversationMemory(
      RedisStringOps stringOps, RedisCollectionOps collectionOps, int ttlHours) {
    this(stringOps, collectionOps, ttlHours, DEFAULT_MAX_LIST_SIZE);
  }

  public RedisConversationMemory(
      RedisStringOps stringOps, RedisCollectionOps collectionOps, int ttlHours, int maxListSize) {
    this.stringOps = stringOps;
    this.collectionOps = collectionOps;
    this.ttlHours = ttlHours > 0 ? ttlHours : DEFAULT_TTL_HOURS;
    this.maxListSize = maxListSize > 0 ? maxListSize : DEFAULT_MAX_LIST_SIZE;
  }

  @Override
  public void save(String conversationId, ChatMessage message) {
    String key = buildKey(conversationId);
    String json = serializeMessage(message);
    collectionOps.rPush(key, json);
    collectionOps.lTrim(key, -maxListSize, -1);
    // P1 优化：仅当剩余 TTL 低于阈值时才续期，避免活跃长对话被每次 save 无限续期、Redis 内存不释放
    long remainTtl = stringOps.getExpire(key);
    if (remainTtl < TTL_RENEW_THRESHOLD_SECONDS) {
      stringOps.expire(key, ttlHours * SECONDS_PER_HOUR);
    }
  }

  @Override
  public List<ChatMessage> load(String conversationId, int maxMessages) {
    String key = buildKey(conversationId);
    long size = collectionOps.lSize(key);
    if (size == 0) {
      return Collections.emptyList();
    }
    long start = Math.max(0, size - maxMessages);
    List<String> rawList = collectionOps.lRange(key, start, size - 1, String.class);
    if (rawList == null || rawList.isEmpty()) {
      return Collections.emptyList();
    }
    List<ChatMessage> messages = new ArrayList<>(rawList.size());
    for (String raw : rawList) {
      ChatMessage msg = deserializeMessage(raw);
      if (msg != null) {
        messages.add(msg);
      }
    }
    return messages;
  }

  @Override
  public void clear(String conversationId) {
    stringOps.del(buildKey(conversationId));
  }

  @Override
  public long count(String conversationId) {
    return collectionOps.lSize(buildKey(conversationId));
  }

  /**
   * 构建带租户前缀的 Redis key。
   *
   * <p>存在租户上下文且非超级管理员/跳过隔离时，在 conversationId 前追加租户段； 否则保持原 key 格式（单租户部署向后兼容）。
   *
   * @param conversationId 对话 ID
   * @return 完整 Redis key
   */
  private String buildKey(String conversationId) {
    if (TenantContextHolder.isPresent()
        && !TenantContextHolder.isSkipIsolation()
        && !TenantContextHolder.isSuperAdmin()
        && TenantContextHolder.getTenantId() != null) {
      return KEY_PREFIX + TenantContextHolder.getTenantId() + ":" + conversationId;
    }
    return KEY_PREFIX + conversationId;
  }

  /**
   * 检查 Redis 连接是否可用
   *
   * @return true 表示连接正常
   */
  public boolean isAvailable() {
    try {
      return Boolean.TRUE.equals(stringOps.hasKey(KEY_PREFIX + "health-check"));
    } catch (Exception e) {
      log.warn("[Memory] Redis 连接检查失败: {}", e.getMessage());
      return false;
    }
  }

  private String serializeMessage(ChatMessage message) {
    SerializedMessage sm = new SerializedMessage();
    sm.version = SerializedMessage.CURRENT_VERSION;
    sm.id = message.getId();
    sm.role = message.getRole().name();
    sm.content = message.getContent();
    sm.conversationId = message.getConversationId();
    sm.createdAt = message.getCreatedAt() != null ? message.getCreatedAt().toString() : null;
    sm.toolCallId = message.getToolCallId();
    if (message.getTokenUsage() != null) {
      sm.promptTokens = message.getTokenUsage().getPromptTokens();
      sm.completionTokens = message.getTokenUsage().getCompletionTokens();
    }
    // 序列化多模态内容（Vision 模型场景）
    if (message.getMultimodalContent() != null && !message.getMultimodalContent().isEmpty()) {
      sm.multimodalContent = YdszJson.toJson(message.getMultimodalContent());
    }
    // 序列化工具调用（Assistant 角色含 Function Calling 场景）
    if (message.hasToolCalls()) {
      sm.toolCalls = YdszJson.toJson(message.getToolCalls());
    }
    return YdszJson.toJson(sm);
  }

  private ChatMessage deserializeMessage(String json) {
    try {
      SerializedMessage sm = YdszJson.fromJson(json, SerializedMessage.class);
      MessageRole role = MessageRole.valueOf(sm.role);
      TokenUsage usage = null;
      if (sm.promptTokens > 0 || sm.completionTokens > 0) {
        usage = new TokenUsage(sm.promptTokens, sm.completionTokens);
      }
      LocalDateTime createdAt =
          sm.createdAt != null ? LocalDateTime.parse(sm.createdAt) : LocalDateTime.now();
      // 反序列化多模态内容（v2+ 数据；v1 旧数据该字段为 null，安全跳过）
      MessageContent multimodalContent = null;
      if (sm.multimodalContent != null && !sm.multimodalContent.isBlank()) {
        multimodalContent = YdszJson.fromJson(sm.multimodalContent, MessageContent.class);
      }
      // 反序列化工具调用（v2+ 数据；v1 旧数据该字段为 null，安全跳过）
      List<ToolCall> toolCalls = null;
      if (sm.toolCalls != null && !sm.toolCalls.isBlank()) {
        toolCalls = YdszJson.fromJson(sm.toolCalls, List.class, ToolCall.class);
      }
      return new ChatMessage(
          sm.id,
          role,
          sm.content,
          multimodalContent,
          sm.conversationId,
          createdAt,
          toolCalls,
          sm.toolCallId,
          usage);
    } catch (Exception e) {
      log.warn("[Memory] 反序列化消息失败: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 消息序列化内部结构。
   *
   * <p>使用版本号保障向前兼容：v1 旧数据缺失新增字段时为 null，反序列化时安全跳过；v2 数据完整恢复多模态内容和工具调用。
   */
  private static class SerializedMessage {
    /** 序列化协议版本（当前为 {@link #CURRENT_VERSION}） */
    private static final int CURRENT_VERSION = 2;

    /** 版本号（用于向前兼容，v1 无此字段解析为 0，按 v1 逻辑处理） */
    public int version;

    /** 消息 ID */
    public String id;

    /** 消息角色 */
    public String role;

    /** 消息内容 */
    public String content;

    /** 多模态内容 JSON（v2+；Vision 模型场景，含文本+图片段落） */
    public String multimodalContent;

    /** 工具调用列表 JSON（v2+；Assistant 角色含 Function Calling 场景） */
    public String toolCalls;

    /** 工具调用 ID（Tool 角色使用，关联对应的工具调用） */
    public String toolCallId;

    /** 对话 ID */
    public String conversationId;

    /** 创建时间 */
    public String createdAt;

    /** 输入 Token 数量 */
    public int promptTokens;

    /** 输出 Token 数量 */
    public int completionTokens;

    /** 默认构造器（兼容旧数据反序列化，版本号初始为 0 表示 v1 格式） */
    SerializedMessage() {
      this.version = CURRENT_VERSION;
    }
  }
}
