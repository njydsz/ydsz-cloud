package com.njydsz.agent.domain.model;

import java.util.HashMap;
import java.util.Map;

/**
 * SSE 流式事件（标准化事件协议）
 *
 * <p>定义了 Agent 流式输出的统一事件类型，每个事件由 {@code event} 类型 + {@code data} 载荷组成，
 * 前端通过监听不同事件类型实现丰富的交互效果（流式文本、工具调用动画、思考链展示等）。
 *
 * <h3>事件类型</h3>
 *
 * <ul>
 *   <li>{@link #EVENT_MESSAGE} — 增量文本片段
 *   <li>{@link #EVENT_TOOL_CALL_STARTED} — 工具调用开始
 *   <li>{@link #EVENT_TOOL_CALL_COMPLETED} — 工具调用完成
 *   <li>{@link #EVENT_REASONING} — 思考链/ReAct 推理过程
 *   <li>{@link #EVENT_CITATION} — 引用来源（RAG 检索命中）
 *   <li>{@link #EVENT_DONE} — 生成完成
 *   <li>{@link #EVENT_ERROR} — 生成错误
 * </ul>
 *
 * <p><b>线程安全</b>：不可变值对象，使用 {@code List.copyOf} / {@code Map.copyOf} 封装集合字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class SseEvent {

  /** 增量文本事件 */
  public static final String EVENT_MESSAGE = "message";

  /** 工具调用开始事件 */
  public static final String EVENT_TOOL_CALL_STARTED = "tool_call_started";

  /** 工具调用完成事件 */
  public static final String EVENT_TOOL_CALL_COMPLETED = "tool_call_completed";

  /** 思考链/ReAct 推理事件 */
  public static final String EVENT_REASONING = "reasoning";

  /** 引用来源事件（RAG 命中） */
  public static final String EVENT_CITATION = "citation";

  /** 生成完成事件 */
  public static final String EVENT_DONE = "done";

  /** 生成错误事件 */
  public static final String EVENT_ERROR = "error";

  /** 事件类型 */
  private final String event;

  /** 事件数据载荷（JSON 对象序列化） */
  private final Map<String, Object> data;

  private SseEvent(String event, Map<String, Object> data) {
    this.event = event;
    this.data = data != null ? Map.copyOf(data) : Map.of();
  }

  public String getEvent() {
    return event;
  }

  public Map<String, Object> getData() {
    return data;
  }

  // ========== 工厂方法 ==========

  /**
   * 创建增量文本事件
   *
   * @param deltaContent 增量文本片段
   * @return SSE 事件
   */
  public static SseEvent message(String deltaContent) {
    return new SseEvent(EVENT_MESSAGE, Map.of("content", deltaContent != null ? deltaContent : ""));
  }

  /**
   * 创建工具调用开始事件
   *
   * @param toolName 工具名称
   * @param arguments 调用参数
   * @return SSE 事件
   */
  public static SseEvent toolCallStarted(String toolName, Map<String, Object> arguments) {
    return new SseEvent(
        EVENT_TOOL_CALL_STARTED,
        Map.of("tool", toolName, "arguments", arguments != null ? arguments : Map.of()));
  }

  /**
   * 创建工具调用完成事件
   *
   * @param toolName 工具名称
   * @param result 调用结果
   * @param durationMs 执行耗时
   * @return SSE 事件
   */
  public static SseEvent toolCallCompleted(String toolName, String result, long durationMs) {
    return new SseEvent(
        EVENT_TOOL_CALL_COMPLETED,
        Map.of(
            "tool", toolName,
            "result", result != null ? result : "",
            "durationMs", durationMs));
  }

  /**
   * 创建思考链事件
   *
   * @param thought 推理思考内容
   * @return SSE 事件
   */
  public static SseEvent reasoning(String thought) {
    return new SseEvent(EVENT_REASONING, Map.of("content", thought != null ? thought : ""));
  }

  /**
   * 创建引用来源事件
   *
   * @param documentId 文档 ID
   * @param documentTitle 文档标题
   * @param snippet 引用片段
   * @param score 相关性得分
   * @return SSE 事件
   */
  public static SseEvent citation(
      String documentId, String documentTitle, String snippet, double score) {
    return new SseEvent(
        EVENT_CITATION,
        Map.of(
            "documentId", documentId,
            "documentTitle", documentTitle,
            "snippet", snippet,
            "score", score));
  }

  /**
   * 创建生成完成事件
   *
   * @param finishReason 完成原因（stop / length / tool_calls）
   * @param usage Token 用量（可为 null）
   * @return SSE 事件
   */
  public static SseEvent done(String finishReason, TokenUsage usage) {
    Map<String, Object> dataMap = new HashMap<>(16);
    dataMap.put("finishReason", finishReason);
    if (usage != null) {
      dataMap.put(
          "usage",
          Map.of(
              "promptTokens", usage.getPromptTokens(),
              "completionTokens", usage.getCompletionTokens(),
              "totalTokens", usage.getTotalTokens()));
    }
    return new SseEvent(EVENT_DONE, dataMap);
  }

  /**
   * 创建错误事件
   *
   * @param errorCode 错误码
   * @param errorMessage 错误描述
   * @return SSE 事件
   */
  public static SseEvent error(String errorCode, String errorMessage) {
    return new SseEvent(
        EVENT_ERROR,
        Map.of("code", errorCode, "message", errorMessage != null ? errorMessage : ""));
  }

  @Override
  public String toString() {
    return "SseEvent{event='" + event + "', data=" + data + "}";
  }
}
