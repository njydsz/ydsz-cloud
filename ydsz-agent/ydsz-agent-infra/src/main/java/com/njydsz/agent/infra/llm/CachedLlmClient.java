package com.njydsz.agent.infra.llm;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;

/**
 * 带语义缓存的 LLM 客户端（装饰器模式）
 *
 * <p>在调用实际 LLM 之前先查询缓存，命中时直接返回缓存结果，
 * 跳过 LLM 调用以节省成本与延迟。
 *
 * <p>缓存策略：
 * <ul>
 *   <li>仅缓存 temperature=0 的确定性请求</li>
 *   <li>仅缓存无工具调用的简单对话</li>
 *   <li>缓存 key = SHA-256(model + systemPrompt + userMessage)</li>
 *   <li>缓存命中时返回包含特殊标记 {@code [cached]} 的响应</li>
 * </ul>
 *
 * <p><b>线程安全</b>：无状态装饰器，委托给被装饰的 {@link LlmClient}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CachedLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(CachedLlmClient.class);

    /** 被装饰的实际 LLM 客户端 */
    private final LlmClient delegate;
    /** 语义缓存实例 */
    private final SemanticLlmCache cache;

    public CachedLlmClient(LlmClient delegate, SemanticLlmCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        boolean cacheable = SemanticLlmCache.isCacheable(request.getTemperature(), hasTools(request));
        // 仅对可缓存请求提取缓存内容；user 消息为空视为提取失败，直接跳过缓存避免 key 恒定的串流风险
        Map.Entry<String, String> cacheContent = cacheable
                ? SemanticLlmCache.extractCacheableContent(request.getMessages()) : null;
        if (cacheContent != null && !cacheContent.getValue().isBlank()) {
            SemanticLlmCache.CachedLlmResponse cached = cache.get(
                    request.getModel(), cacheContent.getKey(), cacheContent.getValue());
            if (cached != null) {
                log.info("[CachedLLM] 缓存命中，跳过 LLM 调用: model={}", request.getModel());
                return buildCachedResponse(request, cached);
            }
        }

        // 缓存未命中或不可缓存，调用实际 LLM
        ChatResponse response = delegate.chat(request);

        // 写入缓存：仅当可缓存、响应内容非空且提取到有效 user 消息时写入
        if (cacheable && response.getContent() != null && !response.getContent().isBlank()
                && cacheContent != null && !cacheContent.getValue().isBlank()) {
            cache.put(request.getModel(), cacheContent.getKey(), cacheContent.getValue(),
                    response.getContent(), delegate.getProvider());
        }
        return response;
    }

    @Override
    public void stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
        // 流式请求不缓存（逐 token 输出，缓存无意义）
        delegate.stream(request, chunkConsumer);
    }

    @Override
    public boolean supports(String modelId) {
        return delegate.supports(modelId);
    }

    @Override
    public String getProvider() {
        return delegate.getProvider();
    }

    /**
     * 判断请求是否携带工具定义。
     *
     * @param request 聊天请求
     * @return true 表示包含工具
     */
    private boolean hasTools(ChatRequest request) {
        return request.getTools() != null && !request.getTools().isEmpty();
    }

    /**
     * 从缓存值构建 ChatResponse。
     *
     * @param request 原始请求（用于获取模型名）
     * @param cached  缓存的响应内容
     * @return 构造的 ChatResponse
     */
    private ChatResponse buildCachedResponse(ChatRequest request, SemanticLlmCache.CachedLlmResponse cached) {
        return new ChatResponse(
                "chatcmpl-cached-" + System.identityHashCode(cached),
                request.getModel(),
                new com.njydsz.agent.domain.model.ChatMessage(
                        "assistant", cached.content(), null),
                TokenUsage.zero(),
                "stop",
                List.of());
    }
}
