package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;

/**
 * LLM 接入抽象接口（批次 19 P3-1 落地）
 *
 * <p>PMIS Agent 推理有两种实现：
 * <ol>
 *   <li>MockLlmProvider - 内置规则推理（开发/测试用）</li>
 *   <li>SpringAiLlmProvider - Spring AI 真实大模型（生产用）</li>
 * </ol>
 *
 * <p>切换方式：Nacos 配置 {@code pmis.agent.llm.provider=mock|spring-ai-openai|spring-ai-dashscope}
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface LlmProvider {

    /**
     * Provider 名称
     */
    String name();

    /**
     * 调用 LLM 推理
     *
     * @param systemPrompt  系统提示词（PMIS Agent 角色定义）
     * @param userPrompt    用户提示词（业务上下文 + 问题）
     * @param context       Agent 上下文（用于 traceId / provider_trace_id 追踪）
     * @return 推理结果（JSON 格式，包含 score / level / reasoning / recommendations）
     */
    String chat(String systemPrompt, String userPrompt, AgentContext context);

    /**
     * 解析 LLM 输出为 AgentResult
     */
    default AgentResult parse(String llmOutput, AgentContext context) {
        // 子类可重写以支持结构化输出（Spring AI 的 BeanOutputParser）
        // 默认实现：返回原始文本 + RECOMMEND 等级
        return new AgentResult(
                null,
                com.njydsz.pmis.agent.enums.AgentAlertLevel.RECOMMEND,
                new java.math.BigDecimal("0.5"),
                null,
                llmOutput,
                null,
                null
        );
    }
}
