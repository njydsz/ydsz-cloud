package com.njydsz.pmis.common.ai;

import java.util.List;
import java.util.Map;

/**
 * 统一 LLM 客户端抽象接口（P0-2 架构优化）。
 *
 * <p>合并 agent 模块的 {@code LlmProvider} 和 literule 模块的 {@code LLMClient} 两套独立抽象，
 * 提供通用的 LLM 调用能力。各模块（agent、literule、message、workflow 等）统一依赖此接口。
 *
 * <h3>核心方法</h3>
 * <ul>
 *   <li>{@link #chat(String, String, Map)} — 同步对话补全（system + user）</li>
 *   <li>{@link #chatWithHistory(List, Map)} — 带对话历史的同步对话补全</li>
 *   <li>{@link #provider()} — 获取提供方标识</li>
 *   <li>{@link #model()} — 获取当前模型名称</li>
 * </ul>
 *
 * <h3>实现方</h3>
 * <ul>
 *   <li>{@code com.njydsz.pmis.common.ai.impl.OpenAICompatibleLlmClient} — OpenAI 兼容协议（生产用）</li>
 *   <li>{@code com.njydsz.pmis.common.ai.impl.MockLlmClient} — 离线 Mock（开发/测试用）</li>
 *   <li>{@code com.njydsz.pmis.agent.engine.llm.LlmProviderAdapter} — agent 模块适配器（桥接到 LlmProvider）</li>
 * </ul>
 *
 * <p>切换方式：配置 {@code pmis.common.ai.llm-client=OPENAI_COMPATIBLE|MOCK}
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P0-2)
 */
public interface LlmClient {

    /**
     * 同步对话补全
     *
     * @param systemPrompt 系统提示词（角色/约束/输出格式）
     * @param userPrompt   用户输入
     * @param options      额外参数（temperature / maxTokens / topP 等），可为 null
     * @return LLM 输出的原始文本
     * @throws LlmException 调用失败时抛出
     */
    String chat(String systemPrompt, String userPrompt, Map<String, Object> options) throws LlmException;

    /**
     * 带对话历史的同步对话补全
     *
     * @param messages 消息列表（按时间顺序，每条是 role/content 组成的 Map，
     *                 role ∈ {system, user, assistant}）
     * @param options  额外参数，可为 null
     * @return LLM 输出的原始文本
     * @throws LlmException 调用失败时抛出
     */
    String chatWithHistory(List<Map<String, String>> messages, Map<String, Object> options) throws LlmException;

    /**
     * 获取当前 LLM 提供方标识
     *
     * @return 提供商标识（如 OPENAI_COMPATIBLE / MOCK / DEEPSEEK）
     */
    String provider();

    /**
     * 当前模型名称
     *
     * @return 模型名称（如 gpt-4o-mini / mock-llm-v1）
     */
    String model();

    /**
     * 便捷方法：不带额外参数的对话补全
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @return LLM 输出的原始文本
     * @throws LlmException 调用失败时抛出
     */
    default String chat(String systemPrompt, String userPrompt) throws LlmException {
        return chat(systemPrompt, userPrompt, null);
    }
}
