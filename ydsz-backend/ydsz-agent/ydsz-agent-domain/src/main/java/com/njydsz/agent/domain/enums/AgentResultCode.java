package com.njydsz.agent.domain.enums;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;

import lombok.Getter;
import com.njydsz.common.exception.registry.YdszResultCode;

/**
 * AI 智能体模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，通过 {@link ExceptionCodeRegistry} 全局注册，
 * 支持 i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 * <ul>
 *   <li>B94001-B94099 Agent 定义/执行</li>
 *   <li>B94101-B94199 对话/记忆</li>
 *   <li>B94201-B94299 LLM 调用</li>
 *   <li>B94301-B94399 RAG/工具/Prompt
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@YdszResultCode(module = "agent", description = "AI Agent")
public enum AgentResultCode implements ExceptionCode {

    // ==================== B94001-B94099 Agent 定义/执行 ====================
    AGENT_NOT_FOUND("B94001", "agent.not.found", 404),
    AGENT_CODE_DUPLICATE("B94002", "agent.code.duplicate"),
    AGENT_TYPE_NOT_SUPPORTED("B94003", "agent.type.not.supported"),
    AGENT_EXECUTION_FAILED("B94004", "agent.execution.failed", 500),
    AGENT_DAG_CYCLE_DETECTED("B94005", "agent.dag.cycle.detected"),

    // ==================== B94101-B94199 对话/记忆 ====================
    CONVERSATION_NOT_FOUND("B94101", "agent.conversation.not.found", 404),
    MEMORY_OVERFLOW("B94102", "agent.memory.overflow"),

    // ==================== B94201-B94299 LLM 调用 ====================
    LLM_CALL_FAILED("B94201", "agent.llm.call.failed", 502),
    LLM_RESPONSE_INVALID("B94202", "agent.llm.response.invalid"),
    LLM_TOKEN_EXCEEDED("B94203", "agent.llm.token.exceeded"),
    LLM_PROVIDER_NOT_CONFIGURED("B94204", "agent.llm.provider.not.configured"),

    // ==================== B94301-B94399 RAG/工具/Prompt ====================
    RAG_RETRIEVAL_FAILED("B94301", "agent.rag.retrieval.failed", 500),
    TOOL_NOT_FOUND("B94302", "agent.tool.not.found", 404),
    TOOL_EXECUTION_FAILED("B94303", "agent.tool.execution.failed", 500),
    PROMPT_TEMPLATE_NOT_FOUND("B94304", "agent.prompt.template.not.found", 404),
    PROMPT_TEMPLATE_DUPLICATE("B94305", "agent.prompt.template.duplicate"),
    GUARDRAIL_REJECTED("B94306", "agent.guardrail.rejected", 403);

    /** 错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    AgentResultCode(String code, String key) {
        this(code, key, 400);
    }

    AgentResultCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    static {
        Map<String, ExceptionCode> registryMap = new HashMap<>();
        for (AgentResultCode c : values()) {
            registryMap.put(c.getCode(), c);
        }
        // 类加载即完成全局注册，确保异常码在首次被抛出/翻译前已可用，避免消息键解析失败
        ExceptionCodeRegistry.register(registryMap);
    }
}
