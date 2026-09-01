package com.njydsz.agent.domain.enums;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * AI 智能体模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，自动注册到 {@link com.njydsz.common.exception.code.ErrorCodeTable}， 支持
 * i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 *
 * <ul>
 *   <li>B94001-B94099 Agent 定义/执行
 *   <li>B94101-B94199 对话/记忆
 *   <li>B94201-B94299 LLM 调用
 *   <li>B94301-B94399 RAG/工具/Prompt
 *   <li>B94401-B94499 调试/追踪
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@YdszExceptionCode(module = "agent", description = "AI Agent")
public enum AgentExceptionCode implements ExceptionCode {

  // ==================== B94001-B94099 Agent 定义/执行 ====================
  /** Agent 不存在 */
  AGENT_NOT_FOUND("B94001", "agent.not.found", 404),
  /** Agent 编码重复 */
  AGENT_CODE_DUPLICATE("B94002", "agent.code.duplicate"),
  /** Agent 类型不支持 */
  AGENT_TYPE_NOT_SUPPORTED("B94003", "agent.type.not.supported"),
  /** Agent 执行失败 */
  AGENT_EXECUTION_FAILED("B94004", "agent.execution.failed", 500),
  /** DAG 编排存在环引用 */
  AGENT_DAG_CYCLE_DETECTED("B94005", "agent.dag.cycle.detected"),

  // ==================== B94101-B94199 对话/记忆 ====================
  /** 会话不存在 */
  CONVERSATION_NOT_FOUND("B94101", "agent.conversation.not.found", 404),
  /** 记忆容量超限 */
  MEMORY_OVERFLOW("B94102", "agent.memory.overflow"),

  // ==================== B94201-B94299 LLM 调用 ====================
  /** LLM 调用失败 */
  LLM_CALL_FAILED("B94201", "agent.llm.call.failed", 502),
  /** LLM 响应无效 */
  LLM_RESPONSE_INVALID("B94202", "agent.llm.response.invalid"),
  /** Token 用量超限 */
  LLM_TOKEN_EXCEEDED("B94203", "agent.llm.token.exceeded"),
  /** LLM 提供方未配置 */
  LLM_PROVIDER_NOT_CONFIGURED("B94204", "agent.llm.provider.not.configured"),

  // ==================== B94251-B94299 配额/成本控制 ====================
  /** 日 Token 配额超限 */
  QUOTA_DAILY_TOKEN_EXCEEDED("B94251", "agent.quota.daily.token.exceeded", 429),
  /** 月度预算超限 */
  QUOTA_MONTHLY_BUDGET_EXCEEDED("B94252", "agent.quota.monthly.budget.exceeded", 429),

  // ==================== B94301-B94399 RAG/工具/Prompt ====================
  /** RAG 检索失败 */
  RAG_RETRIEVAL_FAILED("B94301", "agent.rag.retrieval.failed", 500),
  /** 工具不存在 */
  TOOL_NOT_FOUND("B94302", "agent.tool.not.found", 404),
  /** 工具执行失败 */
  TOOL_EXECUTION_FAILED("B94303", "agent.tool.execution.failed", 500),
  /** Prompt 模板不存在 */
  PROMPT_TEMPLATE_NOT_FOUND("B94304", "agent.prompt.template.not.found", 404),
  /** Prompt 模板重复 */
  PROMPT_TEMPLATE_DUPLICATE("B94305", "agent.prompt.template.duplicate"),
  /** 护栏校验拒绝 */
  GUARDRAIL_REJECTED("B94306", "agent.guardrail.rejected", 403),

  // ==================== B94401-B94499 调试/追踪 ====================
  /** 追踪记录不存在 */
  TRACE_NOT_FOUND("B94401", "agent.trace.not.found", 404),
  /** 追踪记录为空 */
  TRACE_EMPTY("B94402", "agent.trace.empty", 400);

  /** 缺省 HTTP 状态码 */
  private static final int DEFAULT_HTTP_STATUS = 400;

  /** 错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  AgentExceptionCode(String code, String key) {
    this(code, key, DEFAULT_HTTP_STATUS);
  }

  AgentExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }
}
