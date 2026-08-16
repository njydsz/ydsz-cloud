package com.njydsz.agent.domain.gateway;

import java.util.function.Consumer;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;

/**
 * LLM 客户端接口（Provider 抽象层）
 *
 * <p>统一抽象不同 LLM Provider（OpenAI/DeepSeek/Qwen/Anthropic/Ollama 等）的调用接口。
 * 实现类通过 {@code @Component} 注册，由 {@code LlmClientRouter} 按模型配置路由。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #chat} — 同步调用，等待完整响应</li>
 *   <li>{@link #stream} — 流式调用，逐 token 回调消费</li>
 *   <li>{@link #supports} — 判断是否支持指定模型</li>
 * </ul>
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>实现必须是线程安全的</li>
 *   <li>单次调用应在配置的超时时间内完成</li>
 *   <li>网络异常应包装为 {@code LlmException} 抛出，不返回 null</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface LlmClient {

    /**
     * 同步聊天补全
     *
     * @param request 聊天请求
     * @return 聊天响应
     * @throws LlmException LLM 调用异常
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式聊天补全（SSE）
     *
     * <p>逐 token 回调 {@code chunkConsumer}，调用方在回调中处理增量内容。
     * 最后一个 chunk 的 {@link ChatChunk#isFinished()} 返回 true。
     *
     * @param request       聊天请求（stream 字段将被强制设为 true）
     * @param chunkConsumer 流式片段消费者
     * @throws LlmException LLM 调用异常
     */
    void stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer);

    /**
     * 判断是否支持指定模型
     *
     * @param modelId 模型标识
     * @return true=支持
     */
    boolean supports(String modelId);

    /**
     * Provider 标识（如 "openai"、"deepseek"、"qwen"）
     *
     * @return Provider 标识
     */
    String getProvider();
}
