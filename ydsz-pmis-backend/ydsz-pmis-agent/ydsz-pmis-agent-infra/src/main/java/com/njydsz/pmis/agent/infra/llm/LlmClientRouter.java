package com.njydsz.pmis.agent.infra.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.agent.domain.gateway.LlmClient;
import com.njydsz.pmis.agent.domain.gateway.LlmException;
import com.njydsz.pmis.agent.domain.model.ChatChunk;
import com.njydsz.pmis.agent.domain.model.ChatRequest;
import com.njydsz.pmis.agent.domain.model.ChatResponse;
import com.njydsz.pmis.agent.domain.model.LlmModelConfig;

/**
 * LLM 客户端路由器
 *
 * <p>按模型配置路由到对应的 {@link LlmClient} 实现，支持：
 * <ul>
 *   <li>按 Provider 匹配（openai / deepseek / qwen / ollama）</li>
 *   <li>Fallback 降级：主模型不可用时自动切换备用模型</li>
 *   <li>运行时动态注册/注销 Provider</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class LlmClientRouter implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClientRouter.class);

    private final Map<String, LlmClient> clients = new ConcurrentHashMap<>();
    private LlmClient defaultClient;

    public void register(LlmClient client) {
        clients.put(client.getProvider(), client);
        if (defaultClient == null) {
            defaultClient = client;
        }
        log.info("[LLM-Router] 注册 Provider: {}", client.getProvider());
    }

    public void unregister(String provider) {
        clients.remove(provider);
        if (defaultClient != null && defaultClient.getProvider().equals(provider)) {
            defaultClient = clients.values().stream().findFirst().orElse(null);
        }
    }

    public LlmClient resolve(LlmModelConfig config) {
        String provider = config.getProvider();
        LlmClient client = clients.get(provider);
        if (client != null && client.supports(config.getModelName())) {
            return client;
        }
        for (LlmClient c : clients.values()) {
            if (c.supports(config.getModelName())) {
                return c;
            }
        }
        return defaultClient;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        LlmClient client = resolveClient(request.getModel());
        if (client == null) {
            throw new LlmException("无可用 LLM Provider，model=" + request.getModel(),
                    LlmException.ErrorType.MODEL_NOT_FOUND);
        }
        try {
            return client.chat(request);
        } catch (LlmException e) {
            log.warn("[LLM-Router] 主 Provider 调用失败，尝试 Fallback: {}", e.getMessage());
            LlmClient fallback = findFallback(client);
            if (fallback != null) {
                return fallback.chat(request);
            }
            throw e;
        }
    }

    @Override
    public void stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
        LlmClient client = resolveClient(request.getModel());
        if (client == null) {
            throw new LlmException("无可用 LLM Provider，model=" + request.getModel(),
                    LlmException.ErrorType.MODEL_NOT_FOUND);
        }
        client.stream(request, chunkConsumer);
    }

    @Override
    public boolean supports(String modelId) {
        return clients.values().stream().anyMatch(c -> c.supports(modelId));
    }

    @Override
    public String getProvider() {
        return "router";
    }

    public List<String> getAvailableProviders() {
        return List.copyOf(clients.keySet());
    }

    private LlmClient resolveClient(String model) {
        for (LlmClient c : clients.values()) {
            if (c.supports(model)) {
                return c;
            }
        }
        return defaultClient;
    }

    private LlmClient findFallback(LlmClient primary) {
        for (LlmClient c : clients.values()) {
            if (!c.getProvider().equals(primary.getProvider())) {
                return c;
            }
        }
        return null;
    }
}
