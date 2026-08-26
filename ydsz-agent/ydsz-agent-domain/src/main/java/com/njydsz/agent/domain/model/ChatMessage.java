package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.njydsz.common.util.id.IdGenerator;

/**
 * 对话消息值对象
 *
 * <p>每条消息包含角色（System/User/Assistant/Tool）、内容、可选的工具调用信息。 不可变值对象，修改操作返回新实例。
 *
 * <p><b>线程安全</b>：全字段 final 且集合经不可变封装，实例不可变，可安全在多线程/流式回调间共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ChatMessage implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 消息唯一标识 */
  private final String id;

  /** 消息角色（System/User/Assistant/Tool） */
  private final MessageRole role;

  /** 消息内容 */
  private final String content;

  /** 多模态内容（含文本+图片，用于 Vision 模型；为 null 表示纯文本） */
  private final MessageContent multimodalContent;

  /** 所属对话 ID */
  private final String conversationId;

  /** 创建时间 */
  private final LocalDateTime createdAt;

  /** 工具调用列表（Assistant 角色可选） */
  private final List<ToolCall> toolCalls;

  /** 工具调用 ID（Tool 角色使用，关联对应的工具调用） */
  private final String toolCallId;

  /** 本次消息的 Token 用量 */
  private final TokenUsage tokenUsage;

  public ChatMessage(
      String id,
      MessageRole role,
      String content,
      String conversationId,
      LocalDateTime createdAt,
      List<ToolCall> toolCalls,
      String toolCallId,
      TokenUsage tokenUsage) {
    this(id, role, content, null, conversationId, createdAt, toolCalls, toolCallId, tokenUsage);
  }

  public ChatMessage(
      String id,
      MessageRole role,
      String content,
      MessageContent multimodalContent,
      String conversationId,
      LocalDateTime createdAt,
      List<ToolCall> toolCalls,
      String toolCallId,
      TokenUsage tokenUsage) {
    this.id = Objects.requireNonNull(id, "id 不能为 null");
    this.role = Objects.requireNonNull(role, "role 不能为 null");
    this.content = content;
    this.multimodalContent = multimodalContent;
    this.conversationId = conversationId;
    this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    this.toolCallId = toolCallId;
    this.tokenUsage = tokenUsage;
  }

  /**
   * 创建系统角色消息（通常用于注入系统提示词）。
   *
   * @param content 系统提示内容
   * @return 系统消息实例（自动生成消息 ID 与时间戳）
   */
  public static ChatMessage system(String content) {
    return new ChatMessage(
        IdGenerator.nextIdStr(),
        MessageRole.SYSTEM,
        content,
        null,
        LocalDateTime.now(),
        null,
        null,
        null);
  }

  /**
   * 创建用户角色消息。
   *
   * @param content 用户输入内容
   * @param conversationId 所属对话 ID
   * @return 用户消息实例（自动生成消息 ID 与时间戳）
   */
  public static ChatMessage user(String content, String conversationId) {
    return new ChatMessage(
        IdGenerator.nextIdStr(),
        MessageRole.USER,
        content,
        conversationId,
        LocalDateTime.now(),
        null,
        null,
        null);
  }

  /**
   * 创建助手角色消息（纯文本回复）。
   *
   * @param content 助手回复内容
   * @param conversationId 所属对话 ID
   * @param usage 本次回复的 Token 用量（可空）
   * @return 助手消息实例（自动生成消息 ID 与时间戳）
   */
  public static ChatMessage assistant(String content, String conversationId, TokenUsage usage) {
    return new ChatMessage(
        IdGenerator.nextIdStr(),
        MessageRole.ASSISTANT,
        content,
        conversationId,
        LocalDateTime.now(),
        null,
        null,
        usage);
  }

  /**
   * 创建助手角色消息（含工具调用）。
   *
   * <p>当 LLM 决定调用工具时，助手消息携带 {@code toolCalls} 列表， 后续由工具执行结果以 {@link #tool(String, String, String)}
   * 形式回填。
   *
   * @param content 助手回复内容（可为 null，纯工具调用场景无文本）
   * @param conversationId 所属对话 ID
   * @param toolCalls 工具调用列表
   * @param usage 本次回复的 Token 用量（可空）
   * @return 助手消息实例
   */
  public static ChatMessage assistantWithTools(
      String content, String conversationId, List<ToolCall> toolCalls, TokenUsage usage) {
    return new ChatMessage(
        IdGenerator.nextIdStr(),
        MessageRole.ASSISTANT,
        content,
        conversationId,
        LocalDateTime.now(),
        toolCalls,
        null,
        usage);
  }

  /**
   * 创建用户多模态消息（文本 + 图片，用于 Vision 模型）。
   *
   * <p>发送给支持视觉的模型，模型可同时理解文本与图片内容。
   *
   * @param multimodalContent 多模态内容（文本+图片段落）
   * @param conversationId 所属对话 ID
   * @return 用户消息实例（content 字段为 null，图片信息在 multimodalContent 中）
   */
  public static ChatMessage userWithContent(
      MessageContent multimodalContent, String conversationId) {
    return new ChatMessage(
        IdGenerator.nextIdStr(),
        MessageRole.USER,
        null,
        multimodalContent,
        conversationId,
        LocalDateTime.now(),
        null,
        null,
        null);
  }

  /**
   * 创建工具角色消息（工具执行结果回填）。
   *
   * @param toolCallId 被执行的工具调用 ID（关联助手消息中的 toolCalls）
   * @param content 工具执行结果内容
   * @param conversationId 所属对话 ID
   * @return 工具消息实例
   */
  public static ChatMessage tool(String toolCallId, String content, String conversationId) {
    return new ChatMessage(
        IdGenerator.nextIdStr(),
        MessageRole.TOOL,
        content,
        conversationId,
        LocalDateTime.now(),
        null,
        toolCallId,
        null);
  }

  public String getId() {
    return id;
  }

  public MessageRole getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public MessageContent getMultimodalContent() {
    return multimodalContent;
  }

  public String getConversationId() {
    return conversationId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public List<ToolCall> getToolCalls() {
    return toolCalls;
  }

  public String getToolCallId() {
    return toolCallId;
  }

  public TokenUsage getTokenUsage() {
    return tokenUsage;
  }

  /**
   * 判断该消息是否携带工具调用。
   *
   * @return {@code true} 表示消息含至少一个工具调用
   */
  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }

  /**
   * 追加内容并返回新消息（不可变语义）。
   *
   * <p>原消息不可修改，此方法拼接内容后返回全新实例；工具调用列表同样做 拷贝防御，避免外部引用影响新实例。
   *
   * @param delta 待追加的内容片段（可为空字符串）
   * @return 拼接内容后的新 ChatMessage 实例
   */
  public ChatMessage appendContent(String delta) {
    // 不可变语义：拼接出新内容后返回全新 ChatMessage 实例，原消息的 toolCalls 也做拷贝防御，绝不原地修改
    String newContent = (content == null ? "" : content) + delta;
    return new ChatMessage(
        id,
        role,
        newContent,
        conversationId,
        createdAt,
        new ArrayList<>(toolCalls),
        toolCallId,
        tokenUsage);
  }

  @Override
  public String toString() {
    return "ChatMessage{role="
        + role
        + ", content='"
        + (content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content)
        + "'}";
  }
}
