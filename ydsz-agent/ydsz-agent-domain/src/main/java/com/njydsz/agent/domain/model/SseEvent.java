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
 *   <li>{@link #EVENT_TOOL_CALL_DELTA} — 工具调用参数增量（流式拼接）
 *   <li>{@link #EVENT_REASONING} — 思考链/ReAct 推理过程
 *   <li>{@link #EVENT_CITATION} — 引用来源（RAG 检索命中）
 *   <li>{@link #EVENT_APPROVAL_REQUIRED} — 人工审批请求（HITL，前端以 replyId 回填决策）
 *   <li>{@link #EVENT_APPROVAL_RESOLVED} — 人工审批完成
 *   <li>{@link #EVENT_PROGRESS} — 编排进度（DAG 节点 / 子任务）
 *   <li>{@link #EVENT_RESULT} — 最终结果（在 done 之前携带完整响应）
 *   <li>{@link #EVENT_DONE} — 生成完成
 *   <li>{@link #EVENT_ERROR} — 生成错误
 * </ul>
 *
 * <h3>来源标识（source）</h3>
 *
 * <p>多 Agent 协作时子 Agent 事件与主 Agent 事件共用同一条流，事件载荷中的 {@code source}
 * 标识归属（主 Agent 为 {@code main}，子 Agent 为 {@code supervisor/2} 形式的路径），
 * 前端据此对事件解复用。
 *
 * <p><b>线程安全</b>：不可变值对象，使用 {@code List.copyOf} / {@code Map.copyOf} 封装集合字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class SseEvent {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 数据载荷中来源标识的字段名 */
  public static final String FIELD_SOURCE = "source";

  /** 主 Agent 来源标识 */
  public static final String SOURCE_MAIN = "main";


  /** 增量文本事件 */
  public static final String EVENT_MESSAGE = "message";

  /** 工具调用开始事件 */
  public static final String EVENT_TOOL_CALL_STARTED = "tool_call_started";

  /** 工具调用完成事件 */
  public static final String EVENT_TOOL_CALL_COMPLETED = "tool_call_completed";

  /** 工具调用参数增量事件（流式 Function Calling 下参数分片到达） */
  public static final String EVENT_TOOL_CALL_DELTA = "tool_call_delta";

  /** 思考链/ReAct 推理事件 */
  public static final String EVENT_REASONING = "reasoning";

  /** 引用来源事件（RAG 命中） */
  public static final String EVENT_CITATION = "citation";

  /** 人工审批请求事件（HITL） */
  public static final String EVENT_APPROVAL_REQUIRED = "approval_required";

  /** 人工审批完成事件（HITL） */
  public static final String EVENT_APPROVAL_RESOLVED = "approval_resolved";

  /** 编排进度事件（DAG 节点 / Supervisor 子任务） */
  public static final String EVENT_PROGRESS = "progress";

  /** 最终结果事件（在 done 之前推送完整响应） */
  public static final String EVENT_RESULT = "result";

  /** 生成完成事件 */
  public static final String EVENT_DONE = "done";

  /** 生成错误事件 */
  public static final String EVENT_ERROR = "error";

  /** 事件类型 */
  private final String event;

  /** 事件数据载荷（JSON 对象序列化） */
  private final Map<String, Object> data;

  /** 事件来源标识（多 Agent 场景区分归属，单 Agent 为 null） */
  private final String source;

  private SseEvent(String event, Map<String, Object> data) {
    this(event, data, null);
  }

  private SseEvent(String event, Map<String, Object> data, String source) {
    this.event = event;
    this.data = data != null ? Map.copyOf(data) : Map.of();
    this.source = source;
  }

  public String getEvent() {
    return event;
  }

  public Map<String, Object> getData() {
    return data;
  }

  /**
   * 获取事件来源标识。
   *
   * @return 来源标识（主 Agent 为 {@link #SOURCE_MAIN}，子 Agent 为路径形式，未标识时为 null）
   */
  public String getSource() {
    return source;
  }

  /**
   * 创建携带来源标识的事件副本。
   *
   * <p>用于子 Agent 事件转发到父级流时补充归属信息，不修改原对象。
   *
   * @param newSource 来源标识
   * @return 携带新来源标识的事件副本
   */
  public SseEvent withSource(String newSource) {
    return new SseEvent(event, data, newSource);
  }

  /**
   * 判断事件是否已携带来源标识。
   *
   * @return true 表示 {@link #getSource()} 非空
   */
  public boolean hasSource() {
    return source != null && !source.isBlank();
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
   * 创建工具调用参数增量事件。
   *
   * <p>流式 Function Calling 场景下，LLM 分片推送工具参数（JSON 片段），
   * 前端可据此实时展示"正在准备参数"的中间态。
   *
   * @param toolName 工具名称
   * @param argumentsDelta 参数增量片段（JSON 分片，可能不是合法 JSON）
   * @return SSE 事件
   */
  public static SseEvent toolCallDelta(String toolName, String argumentsDelta) {
    return new SseEvent(
        EVENT_TOOL_CALL_DELTA,
        Map.of(
            "tool", toolName != null ? toolName : "",
            "argumentsDelta", argumentsDelta != null ? argumentsDelta : ""));
  }

  /**
   * 创建人工审批请求事件（HITL）。
   *
   * <p>审批请求直接推入当前 SSE 流，前端渲染审批卡片；用户决策后以 {@code replyId} 关联回填，
   * 无需轮询审批列表接口。
   *
   * @param approvalId 审批请求 ID（作为前端回填的 replyId）
   * @param stepDescription 待审批步骤描述
   * @param summary 上下文摘要（供审批人判断，可为空）
   * @return SSE 事件
   */
  public static SseEvent approvalRequired(String approvalId, String stepDescription, String summary) {
    Map<String, Object> dataMap = new HashMap<>(COLLECTION_CAPACITY);
    dataMap.put("replyId", approvalId != null ? approvalId : "");
    dataMap.put("approvalId", approvalId != null ? approvalId : "");
    dataMap.put("stepDescription", stepDescription != null ? stepDescription : "");
    dataMap.put("summary", summary != null ? summary : "");
    return new SseEvent(EVENT_APPROVAL_REQUIRED, dataMap);
  }

  /**
   * 创建人工审批完成事件（HITL）。
   *
   * @param approvalId 审批请求 ID
   * @param approved 是否通过
   * @param approver 审批人（可为空）
   * @param comment 审批意见（可为空）
   * @return SSE 事件
   */
  public static SseEvent approvalResolved(
      String approvalId, boolean approved, String approver, String comment) {
    Map<String, Object> dataMap = new HashMap<>(COLLECTION_CAPACITY);
    dataMap.put("replyId", approvalId != null ? approvalId : "");
    dataMap.put("approvalId", approvalId != null ? approvalId : "");
    dataMap.put("approved", approved);
    dataMap.put("approver", approver != null ? approver : "");
    dataMap.put("comment", comment != null ? comment : "");
    return new SseEvent(EVENT_APPROVAL_RESOLVED, dataMap);
  }

  /**
   * 创建编排进度事件。
   *
   * <p>用于 DAG 节点执行进度、Supervisor 子任务进度等编排态推送。
   *
   * @param eventType 进度类型（如 node_started / node_completed / sub_task_started）
   * @param nodeId 节点或子任务标识
   * @param completedCount 已完成数量
   * @param totalCount 总数量
   * @return SSE 事件
   */
  public static SseEvent progress(
      String eventType, String nodeId, int completedCount, int totalCount) {
    Map<String, Object> dataMap = new HashMap<>(COLLECTION_CAPACITY);
    dataMap.put("eventType", eventType != null ? eventType : "");
    dataMap.put("nodeId", nodeId != null ? nodeId : "");
    dataMap.put("completedCount", completedCount);
    dataMap.put("totalCount", totalCount);
    return new SseEvent(EVENT_PROGRESS, dataMap);
  }

  /**
   * 创建最终结果事件。
   *
   * <p>在 {@link #done(String, TokenUsage)} 之前推送完整响应内容，便于前端在结束前落库或渲染富文本。
   *
   * @param content 最终响应内容
   * @param model 模型名称（可为空）
   * @return SSE 事件
   */
  public static SseEvent result(String content, String model) {
    Map<String, Object> dataMap = new HashMap<>(COLLECTION_CAPACITY);
    dataMap.put("content", content != null ? content : "");
    dataMap.put("model", model != null ? model : "");
    return new SseEvent(EVENT_RESULT, dataMap);
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
    Map<String, Object> dataMap = new HashMap<>(COLLECTION_CAPACITY);
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
