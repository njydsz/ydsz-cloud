package com.njydsz.agent.domain.middleware;

import java.util.Map;

import com.njydsz.agent.domain.model.ChatResponse;

/**
 * Agent 执行中间件接口 — 对标 AgentScope MiddlewareBase 的 5 个钩子位置。
 *
 * <p>中间件在 Agent 执行管线的关键时机插入自定义逻辑，实现关注点分离：
 * 安全审计、链路追踪、指标采集、限流降级等工程能力均可通过中间件实现。
 *
 * <h3>生命周期钩子执行顺序</h3>
 *
 * <pre>
 * onAgentStart   →  Agent 执行开始（获取请求对象）
 *   ↓
 * onSystemPrompt →  构建系统 Prompt（可注入动态段落）
 *   ↓
 * onReasoning    →  LLM 推理前（消息列表就绪，可修改/审查）
 *   ↓
 * onModelCall    →  实际调用 LLM API（可缓存/限流/切换模型）
 *   ↓
 * onActing       →  工具执行阶段（可审计/超时控制/结果处理，洋葱模型）
 *   ↓
 * onObservation  →  单个工具执行结果返回（可审查/过滤）
 *   ↓
 * 循环回到 onReasoning 或 →
 * onAgentEnd     →  Agent 执行结束（最终响应）
 * </pre>
 *
 * <p><b>线程安全</b>：中间件实例通常为单例，被多个 Agent 执行并发调用。
 * 实现不得在实例字段中保存请求级状态，所有状态通过 {@link MiddlewareContext} 传递。
 *
 * <p><b>异常处理</b>：钩子方法抛出的异常会中断当前执行并向上传播，
 * 中间件应仅在确有必要时抛出（如安全护栏拒绝），常规异常应记录日志后放行。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
public interface AgentMiddleware {

  /** 默认优先级（中间入力），用于未显式指定优先级的通用中间件。 */
  int DEFAULT_PRIORITY = 50;

  /** 输入护栏建议优先级。 */
  int INPUT_GUARD_PRIORITY = 10;

  /** 链路追踪中间件建议优先级。 */
  int TRACE_PRIORITY = 20;

  /** 指标采集中间件建议优先级。 */
  int METRICS_PRIORITY = 30;

  /**
   * 限流中间件建议优先级。
   */
  int RATE_LIMIT_PRIORITY = 40;

  /** 工具审计中间件建议优先级（Acting 阶段，早于业务中间件）。 */
  int TOOL_AUDIT_PRIORITY = 45;

  /** 工具结果压缩驱逐中间件建议优先级（Acting 阶段，晚于审计）。 */
  int TOOL_EVICTION_PRIORITY = 55;

  /** 输出护栏建议优先级。 */
  int OUTPUT_GUARD_PRIORITY = 100;

  /**
   * Agent 执行开始时调用。
   *
   * <p>适用于：初始化链路追踪、记录请求日志、预检查配额、注入租户上下文。
   *
   * @param context 中间件上下文（携带请求、对话 ID、traceId 已就绪）
   */
  default void onAgentStart(MiddlewareContext context) {
    // 默认空实现
  }

  /**
   * 构建系统 Prompt 时调用。
   *
   * <p>适用于：注入动态安全指令（如时间敏感提示）、追加合规声明、
   * 根据租户配置切换人格模板。此时 {@link MiddlewareContext#getSystemPrompt()} 返回当前 Prompt，
   * 中间件可通过 {@link MiddlewareContext#setSystemPrompt(String)} 替换。
   *
   * @param context 中间件上下文（systemPrompt 已就绪）
   */
  default void onSystemPrompt(MiddlewareContext context) {
    // 默认空实现
  }

  /**
   * LLM 推理前调用（消息列表已组装完毕）。
   *
   * <p>适用于：输入护栏审查（消息内容安全检查）、消息截断调整。
   *
   * @param context 中间件上下文（llmRequest 已就绪，包含完整消息列表）
   */
  default void onReasoning(MiddlewareContext context) {
    // 默认空实现
  }

  /**
   * 实际调用 LLM API 的包装钩子。
   *
   * <p>适用于：LLM 调用级限流、缓存查询/写入、模型路由决策、异常重试策略。
   *
   * <p>中间件可选择直接消费此次调用（设置缓存结果）或放行给下一个中间件。
   * 要中断执行（如限流拒绝），抛出 {@link MiddlewareException}。
   *
   * @param context 中间件上下文（llmRequest 就绪，llmResponse 为空待填充）
   * @param proceed 放行函数 — 调用后继续执行下一个中间件/最终 LLM 调用，
   *     返回 LLM 响应供 {@link MiddlewareContext#setLlmResponse} 记录
   */
  default void onModelCall(MiddlewareContext context, ModelCallProceed proceed) {
    // 默认放行
    proceed.execute();
  }

  /**
   * 工具执行阶段的包装钩子（Acting 阶段，洋葱模型）。
   *
   * <p>适用于：工具级审计/权限二次校验、工具批量执行前后的耗时统计、
   * 长对话中工具结果的压缩驱逐、按租户限制工具调用并发度。
   *
   * <p>钩子执行前 {@link MiddlewareContext#setToolCalls(java.util.List)} 已填入本批次待执行的
   * 工具调用；中间件可读取（审计）、可修改（过滤）、可抛 {@link MiddlewareException} 拒绝整批执行。
   * 放行后框架完成实际工具调用，并把结果写入 {@link MiddlewareContext#setToolResults(java.util.Map)}。
   *
   * <p><b>实现注意</b>：若中间件需要对工具结果做压缩驱逐，必须确保结果已被
   * {@code TraceRecorder} 持久化后再驱逐（框架在工具执行时即落链路），
   * 否则会丢失可观测性证据。参考 AgentScope 将 ToolResultEviction 从 onActing
   * 调整至 onReasoning 阶段的实践。
   *
   * @param context 中间件上下文（toolCalls 已就绪，toolResults 待回填）
   * @param proceed 放行函数 — 调用后执行本批次工具调用（可能继续下一个中间件），
   *     返回 callId → 结果文本；不放行时中间件可直接返回自定义结果
   */
  default void onActing(MiddlewareContext context, ActingProceed proceed) {
    // 默认放行
    context.setToolResults(proceed.execute());
  }

  /**
   * 工具执行结果返回后调用（每次 Observation 阶段）。
   *
   * <p>适用于：工具输出审查、敏感数据脱敏（如数据库查询结果中的 PII）、
   * 工具执行耗时/成功率指标记录。
   *
   * @param context 中间件上下文（toolCall、toolResult 就绪）
   */
  default void onObservation(MiddlewareContext context) {
    // 默认空实现
  }

  /**
   * Agent 执行结束时调用（正常结束或异常退出均会触发）。
   *
   * <p>适用于：记录最终响应、上报成本指标、清理资源、发送完成事件。
   *
   * @param context 中间件上下文（isFinished()=true 或 getError() 非空）
   */
  default void onAgentEnd(MiddlewareContext context) {
    // 默认空实现
  }

  /**
   * 获取中间件名称（用于日志和监控）。
   *
   * @return 中间件英文标识（如 "guardrail-input"、"trace"、"metrics"）
   */
  String getName();

  /**
   * 获取执行优先级。
   *
   * <p>数字越小优先级越高（越先执行）。建议取值：
   * 输入护栏={@link #INPUT_GUARD_PRIORITY}、
   * 追踪中间件={@link #TRACE_PRIORITY}、
   * 指标中间件={@link #METRICS_PRIORITY}、
   * 限流中间件={@link #RATE_LIMIT_PRIORITY}、
   * 输出护栏={@link #OUTPUT_GUARD_PRIORITY}。
   *
   * @return 优先级整数，默认 {@link #DEFAULT_PRIORITY}
   */
  default int getPriority() {
    return DEFAULT_PRIORITY;
  }

  /**
   * LLM 调用放行函数 — 在 onModelCall 中调用以继续执行下一个中间件或最终调用。
   */
  @FunctionalInterface
  interface ModelCallProceed {
    /**
     * 执行被包装的 LLM 调用。
     *
     * @return LLM 响应
     */
    ChatResponse execute();
  }

  /**
   * 工具执行放行函数 — 在 onActing 中调用以继续执行下一个中间件或实际工具调用。
   */
  @FunctionalInterface
  interface ActingProceed {
    /**
     * 执行被包装的工具调用批次。
     *
     * @return callId → 工具执行结果文本
     */
    Map<String, String> execute();
  }
}
