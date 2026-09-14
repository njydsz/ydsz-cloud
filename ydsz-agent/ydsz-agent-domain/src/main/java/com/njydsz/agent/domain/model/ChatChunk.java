package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * LLM 流式响应片段
 *
 * <p>流式输出时每个 SSE 事件对应一个 ChatChunk，包含增量内容或工具调用增量。
 *
 * <p><b>来源标识（source）</b>：多 Agent 协作场景（Supervisor / DAG / TeamRun）下，
 * 子 Agent 的流式片段会被转发到同一条父级 SSE 流中，{@link #getSource()} 用于标识
 * 片段归属（主 Agent 为 {@link #SOURCE_MAIN}，子 Agent 为 {@code supervisor/2} 形式的路径），
 * 前端据此对不同 Agent 的输出分组渲染。
 *
 * <p><b>线程安全</b>：全字段 final 且集合不可变，不可变值对象，可安全在流式回调与业务线程间共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class ChatChunk implements Serializable {

  /** toString 内容截断长度 */
  private static final int TO_STRING_TRUNCATE_LEN = 50;

  /** 主 Agent 来源标识（子 Agent 来源为 {@code 父路径/子标识} 形式） */
  public static final String SOURCE_MAIN = "main";

  private static final long serialVersionUID = 1L;

  private final String id;
  private final String model;
  private final String deltaContent;
  private final List<ToolCall> deltaToolCalls;
  private final String finishReason;
  private final TokenUsage usage;

  /** 片段来源标识（多 Agent 场景用于区分归属，单 Agent 场景为 null） */
  private final String source;

  /**
   * 构造流式片段（无来源标识，兼容单 Agent 场景）。
   *
   * @param id chunk 唯一标识
   * @param model 模型名称
   * @param deltaContent 增量文本内容
   * @param deltaToolCalls 增量工具调用列表
   * @param finishReason 结束原因
   * @param usage Token 用量
   */
  public ChatChunk(
      String id,
      String model,
      String deltaContent,
      List<ToolCall> deltaToolCalls,
      String finishReason,
      TokenUsage usage) {
    this(id, model, deltaContent, deltaToolCalls, finishReason, usage, null);
  }

  /**
   * 构造流式片段（含来源标识）。
   *
   * @param id chunk 唯一标识
   * @param model 模型名称
   * @param deltaContent 增量文本内容
   * @param deltaToolCalls 增量工具调用列表
   * @param finishReason 结束原因
   * @param usage Token 用量
   * @param source 片段来源标识（多 Agent 场景下区分归属，可为 null）
   */
  public ChatChunk(
      String id,
      String model,
      String deltaContent,
      List<ToolCall> deltaToolCalls,
      String finishReason,
      TokenUsage usage,
      String source) {
    this.id = Objects.requireNonNull(id, "id 不能为 null");
    this.model = model;
    this.deltaContent = deltaContent;
    this.deltaToolCalls = deltaToolCalls != null ? List.copyOf(deltaToolCalls) : List.of();
    this.finishReason = finishReason;
    this.usage = usage;
    this.source = source;
  }

  /**
   * 创建携带增量内容的流式片段。
   *
   * @param id chunk 唯一标识（SSE 事件 id）
   * @param model 模型名称
   * @param delta 增量文本内容
   * @return 内容型流式片段
   */
  public static ChatChunk content(String id, String model, String delta) {
    return new ChatChunk(id, model, delta, null, null, null);
  }

  /**
   * 创建携带增量内容与工具调用的流式片段。
   *
   * @param id chunk 唯一标识（SSE 事件 id）
   * @param model 模型名称
   * @param delta 增量文本内容
   * @param toolCalls 增量工具调用列表（部分 JSON，需下游拼接）
   * @return 内容型流式片段（含工具调用）
   */
  public static ChatChunk content(String id, String model, String delta, List<ToolCall> toolCalls) {
    return new ChatChunk(id, model, delta, toolCalls, null, null);
  }

  /**
   * 创建标识流结束的终止片段。
   *
   * @param id chunk 唯一标识
   * @param model 模型名称
   * @param finishReason 结束原因（如 stop / length / tool_calls）
   * @param usage 本次请求累计 Token 用量
   * @return 终止型流式片段
   */
  public static ChatChunk finish(String id, String model, String finishReason, TokenUsage usage) {
    return new ChatChunk(id, model, null, null, finishReason, usage);
  }

  /**
   * 创建标识流结束的终止片段（含最终工具调用）。
   *
   * @param id chunk 唯一标识
   * @param model 模型名称
   * @param finishReason 结束原因（如 stop / length / tool_calls）
   * @param usage 本次请求累计 Token 用量
   * @param toolCalls 完整工具调用列表（finish 时传递最终拼接结果）
   * @return 终止型流式片段
   */
  public static ChatChunk finish(
      String id, String model, String finishReason, TokenUsage usage, List<ToolCall> toolCalls) {
    return new ChatChunk(id, model, null, toolCalls, finishReason, usage);
  }

  /**
   * 创建纯工具调用增量片段（无文本内容）。
   *
   * <p>流式 Function Calling 场景下，LLM 可能先推送 tool_calls 结构再推送文本， 此类 chunk 仅携带工具调用增量，不含 delta content。
   *
   * @param id chunk 唯一标识（SSE 事件 id）
   * @param model 模型名称
   * @param toolCalls 增量工具调用列表
   * @return 工具调用型流式片段
   */
  public static ChatChunk toolCalls(String id, String model, List<ToolCall> toolCalls) {
    return new ChatChunk(id, model, null, toolCalls, null, null);
  }

  public String getId() {
    return id;
  }

  public String getModel() {
    return model;
  }

  public String getDeltaContent() {
    return deltaContent;
  }

  public List<ToolCall> getDeltaToolCalls() {
    return deltaToolCalls;
  }

  public String getFinishReason() {
    return finishReason;
  }

  public TokenUsage getUsage() {
    return usage;
  }

  /**
   * 获取片段来源标识。
   *
   * @return 来源标识（主 Agent 为 {@link #SOURCE_MAIN}，子 Agent 为路径形式，未标识时为 null）
   */
  public String getSource() {
    return source;
  }

  /**
   * 创建携带来源标识的副本。
   *
   * <p>用于子 Agent 片段转发到父级流时补充归属信息，不修改原对象。
   *
   * @param newSource 来源标识
   * @return 携带新来源标识的片段副本
   */
  public ChatChunk withSource(String newSource) {
    return new ChatChunk(
        id, model, deltaContent, deltaToolCalls, finishReason, usage, newSource);
  }

  /**
   * 判断流式响应是否已结束。
   *
   * <p>以 {@code finishReason} 是否非空判定流结束，与 SSE 约定一致； {@code null} 表示后续仍有 chunk 到达。
   *
   * @return {@code true} 表示流已结束（收到终止片段）
   */
  public boolean isFinished() {
        // 以 finishReason 是否非空判定流结束，与 SSE 约定一致；null 表示仍有后续 chunk
    return finishReason != null;
  }

  /**
   * 判断该片段是否携带增量文本内容。
   *
   * @return {@code true} 表示 {@code deltaContent} 非空
   */
  public boolean hasContent() {
    return deltaContent != null && !deltaContent.isEmpty();
  }

  /**
   * 判断该片段是否携带工具调用增量。
   *
   * @return {@code true} 表示 {@code deltaToolCalls} 非空
   */
  public boolean hasToolCalls() {
    return deltaToolCalls != null && !deltaToolCalls.isEmpty();
  }

  @Override
  public String toString() {
    return "ChatChunk{delta='"
        + (deltaContent != null && deltaContent.length() > TO_STRING_TRUNCATE_LEN
            ? deltaContent.substring(0, TO_STRING_TRUNCATE_LEN) + "..."
            : deltaContent)
        + "', toolCalls="
        + deltaToolCalls.size()
        + ", finished="
        + isFinished()
        + "}";
  }
}
