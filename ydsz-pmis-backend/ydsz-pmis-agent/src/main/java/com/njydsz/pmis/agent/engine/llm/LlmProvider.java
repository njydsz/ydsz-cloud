package com.njydsz.pmis.agent.engine.llm;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;

import java.math.BigDecimal;

/**
 * LLM 接入抽象接口
 *
 * <p>PMIS Agent 推理有多种实现：
 * <ol>
 *   <li>{@link MockLlmProvider} - 内置规则推理（开发/测试用）</li>
 *   <li>{@link SpringAiLlmProvider} - OpenAI 兼容协议真实大模型（生产用）</li>
 * </ol>
 *
 * <p>切换方式：Nacos 配置 {@code pmis.agent.llm.provider=mock|spring-ai-openai}
 *
 * <p><b>P1-4 增强</b>：新增 {@link #chatForJson(String, String, Class, AgentContext)} 默认方法，
 * 为 P1-5 结构化输出（替代 FlowGeneratorAgent 中的正则提取 XML）铺垫。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface LlmProvider {

    /**
     * Provider 名称。
     *
     * @return Provider 标识（如 "mock"、"spring-ai-openai"）
     */
    String name();

    /**
     * 调用 LLM 推理（同步）。
     *
     * @param systemPrompt 系统提示词（PMIS Agent 角色定义）
     * @param userPrompt   用户提示词（业务上下文 + 问题）
     * @param context      Agent 上下文（用于 traceId / provider_trace_id 追踪）
     * @return 推理结果（自由文本或 JSON 字符串，由调用方解析）
     */
    String chat(String systemPrompt, String userPrompt, AgentContext context);

    /**
     * 调用 LLM 并直接返回结构化 Java 对象（P1-4 新增）。
     *
     * <p>实现策略：
     * <ol>
     *   <li>在 userPrompt 末尾追加"请严格输出 JSON 格式"指令</li>
     *   <li>调用 {@link #chat(String, String, AgentContext)}</li>
     *   <li>剥离可能的 markdown 代码块（```json ... ```）</li>
     *   <li>用 fastjson2 反序列化为目标类型</li>
     * </ol>
     *
     * <p>子类可重写以提供原生 JSON 模式（如 OpenAI 的 response_format=json_object）。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param type         目标 Java 类型
     * @param context      Agent 上下文
     * @param <T>          返回类型
     * @return 反序列化后的对象；LLM 输出非法 JSON 时抛 RuntimeException
     */
    default <T> T chatForJson(String systemPrompt, String userPrompt,
                              Class<T> type, AgentContext context) {
        String enhanced = (userPrompt == null ? "" : userPrompt)
                + "\n\n请严格输出 JSON 格式（不要使用 markdown 代码块包裹）。";
        String raw = chat(systemPrompt, enhanced, context);
        String json = stripMarkdownCodeFence(raw);
        try {
            return JSON.parseObject(json, type);
        } catch (Exception e) {
            throw new RuntimeException("LLM 输出非合法 JSON: " + json, e);
        }
    }

    /**
     * 剥离 LLM 输出中可能包裹的 markdown 代码块（```json ... ``` 或 ``` ... ```）。
     *
     * @param raw LLM 原始输出
     * @return 去除代码块后的 JSON 字符串
     */
    static String stripMarkdownCodeFence(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // 去除开头的 ```json 或 ```
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            }
            // 去除结尾的 ```
            int lastFence = s.lastIndexOf("```");
            if (lastFence >= 0) {
                s = s.substring(0, lastFence);
            }
            s = s.trim();
        }
        return s;
    }

    /**
     * 解析 LLM 输出为 AgentResult。
     *
     * <p>子类可重写以支持结构化输出。
     * 默认实现：返回原始文本 + RECOMMEND 等级。
     *
     * @param llmOutput LLM 原始输出
     * @param context   Agent 上下文
     * @return 解析后的 AgentResult
     */
    default AgentResult parse(String llmOutput, AgentContext context) {
        // 默认实现：返回原始文本 + RECOMMEND 等级
        return new AgentResult(
                null,
                AgentAlertLevel.RECOMMEND,
                new BigDecimal("0.5"),
                null,
                llmOutput,
                null,
                null
        );
    }
}
