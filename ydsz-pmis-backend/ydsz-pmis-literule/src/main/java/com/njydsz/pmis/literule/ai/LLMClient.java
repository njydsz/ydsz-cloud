package com.njydsz.pmis.literule.ai;

import java.util.List;
import java.util.Map;

/**
 * LLM 客户端抽象接口（P2-15 AI 增强）
 *
 * <p>通过 SPI 方式解耦不同 LLM 提供方（OpenAI、DeepSeek、通义千问、Ollama 等），
 * 业务层只依赖本接口；默认实现为 {@link MockLLMClient}（无网络依赖、便于开发/测试），
 * 通过 {@code pmis.literule.ai.llm-client=OPENAI_COMPATIBLE} 切换到
 * {@link OpenAICompatibleLLMClient}。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public interface LLMClient {

    /**
     * 同步对话补全
     *
     * @param systemPrompt 系统提示词（角色/约束/输出格式）
     * @param userPrompt   用户输入
     * @param options      额外参数（temperature / maxTokens 等）
     * @return LLM 输出的原始文本
     * @throws LLMException 调用失败时抛出
     */
    String chat(String systemPrompt, String userPrompt, Map<String, Object> options) throws LLMException;

    /**
     * 带对话历史的同步对话补全
     *
     * @param messages  消息列表（按时间顺序，每条是 role/content 组成的 Map，
     *                  role ∈ {system, user, assistant}）
     * @param options   额外参数
     * @return LLM 输出的原始文本
     * @throws LLMException 调用失败时抛出
     */
    String chatWithHistory(List<Map<String, String>> messages, Map<String, Object> options) throws LLMException;

    /**
     * 获取当前 LLM 提供方标识
     *
     * @return 提供商标识（如 OPENAI_COMPATIBLE / MOCK / DEEPSEEK）
     */
    String provider();

    /**
     * 当前模型名称
     */
    String model();
}
