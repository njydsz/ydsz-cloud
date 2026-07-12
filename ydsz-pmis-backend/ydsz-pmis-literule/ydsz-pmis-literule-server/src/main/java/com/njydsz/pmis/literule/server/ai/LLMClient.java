package com.njydsz.pmis.literule.server.ai;

import com.njydsz.pmis.agent.api.llm.LlmClient;

/**
 * LLM 客户端抽象接口（P2-15 AI 增强）
 *
 * <p>通过 SPI 方式解耦不同 LLM 提供方（OpenAI、DeepSeek、通义千问、Ollama 等），
 * 业务层只依赖本接口；默认实现为 {@link MockLLMClient}（无网络依赖、便于开发/测试），
 * 通过 {@code pmis.literule.ai.llm-client=OPENAI_COMPATIBLE} 切换到
 * {@link OpenAICompatibleLLMClient}。
 *
 * <p><b>P0-2 架构优化</b>：继承 {@link LlmClient}（common 模块统一接口），
 * literule 内部代码仍可依赖本接口（保持向后兼容），但实际能力由 common 模块提供。
 * 后续迭代中将逐步迁移所有引用到 {@link LlmClient}。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public interface LLMClient extends LlmClient {

}
