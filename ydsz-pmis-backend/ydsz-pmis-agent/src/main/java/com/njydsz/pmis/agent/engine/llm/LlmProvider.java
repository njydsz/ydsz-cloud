package com.njydsz.pmis.agent.engine.llm;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * 判断当前 Provider 是否支持 SSE 流式输出（P4-1 落地）。
     *
     * <p>支持流式时，调用方可使用 {@link #chatStream} 获取逐 token 增量输出，
     * 实现 Coze / Dify 式的实时打字机效果。
     *
     * @return true 表示支持 {@link #chatStream}；false 时调用方应降级为 {@link #chat}
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * 流式调用 LLM 推理，逐 token 回调（P4-1 落地）。
     *
     * <p>对标 OpenAI / DashScope 的 SSE stream=true 模式：服务端逐 chunk 推送，
     * 客户端实时收到 delta token，无需等待完整响应。
     *
     * <p>实现策略（默认降级为同步调用后整体回调）：
     * <ol>
     *   <li>调用 {@link #chat} 获取完整响应</li>
     *   <li>将完整响应作为单个 tokenDelta 回调给 consumer</li>
     * </ol>
     *
     * <p>子类应重写此方法以提供真正的 SSE 流式实现。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param context      Agent 上下文
     * @param tokenConsumer token 增量消费者（每收到一个 chunk 调用一次）
     * @return 完整推理结果（所有 token 拼接后的全文）
     */
    default String chatStream(String systemPrompt, String userPrompt,
                              AgentContext context,
                              java.util.function.Consumer<String> tokenConsumer) {
        String full = chat(systemPrompt, userPrompt, context);
        if (tokenConsumer != null && full != null && !full.isEmpty()) {
            tokenConsumer.accept(full);
        }
        return full;
    }

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
     * 匹配 markdown 代码块的正则（P2-3）。
     *
     * <p>支持以下格式（DOTALL 模式，跨行匹配）：
     * <ul>
     *   <li>{@code ```json\n...\n```}（带语言标识）</li>
     *   <li>{@code ```\n...\n```}（不带语言标识）</li>
     *   <li>{@code ```{...}```}（单行，无换行）</li>
     *   <li>首尾可能有空白字符</li>
     * </ul>
     *
     * <p>分组 1 捕获代码块内容。
     */
    Pattern CODE_FENCE_PATTERN = Pattern.compile(
            "^```[^\\n\\r]*\\s*\\n?(.*?)\\n?```\\s*$",
            Pattern.DOTALL | Pattern.MULTILINE);

    /**
     * 剥离 LLM 输出中可能包裹的 markdown 代码块（```json ... ``` 或 ``` ... ```）。
     *
     * <p><b>P2-3 修复</b>：原实现使用 {@code indexOf('\n')} + {@code lastIndexOf("```")}
     * 字符串截取，存在多个边界缺陷：
     * <ul>
     *   <li>单行代码块 {@code ```{...}```} 无换行符时无法剥离</li>
     *   <li>{@code ```json} 后无换行直接跟内容时 {@code firstNewline > 0} 判断错误</li>
     *   <li>代码块内部包含 {@code ```} 字符串时会错误截断</li>
     *   <li>只有开头 ``` 无结尾 ``` 时会错误截断</li>
     * </ul>
     *
     * <p>现改用正则 {@link #CODE_FENCE_PATTERN} 精确匹配整段代码块，
     * 提取分组 1 作为 JSON 内容，覆盖所有边界场景。
     *
     * @param raw LLM 原始输出
     * @return 去除代码块后的 JSON 字符串
     */
    static String stripMarkdownCodeFence(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // P2-3：正则精确匹配整段代码块，提取内容
        Matcher matcher = CODE_FENCE_PATTERN.matcher(s);
        if (matcher.matches()) {
            return matcher.group(1).trim();
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
