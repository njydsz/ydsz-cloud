package com.njydsz.agent.infra.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;

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
 * <h3>Fallback 策略</h3>
 * <p>仅对以下可恢复错误类型触发 Fallback：
 * <ul>
 *   <li>{@code NETWORK_TIMEOUT} — 网络超时，切换 Provider 可恢复</li>
 *   <li>{@code RATE_LIMITED} — 限流（429），切换 Provider 分散负载</li>
 *   <li>{@code PROVIDER_ERROR} — Provider 服务端错误（5xx），切换 Provider 可恢复</li>
 * </ul>
 * <p>以下错误类型<b>不触发</b> Fallback，直接抛出：
 * <ul>
 *   <li>{@code AUTH_FAILED} — 认证失败（401/403），多为配置错误，需运维介入</li>
 *   <li>{@code MODEL_NOT_FOUND} — 模型不存在（404），换 Provider 也未必支持</li>
 *   <li>{@code INVALID_RESPONSE} — 响应格式错误，多为解析 bug 或 API 变更</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class LlmClientRouter implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClientRouter.class);

    /** 已注册的 Provider 客户端映射（key=provider name） */
    private final Map<String, LlmClient> clients = new ConcurrentHashMap<>();
    /** 默认客户端（无匹配 Provider 时使用） */
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
            if (!shouldFallback(e.getErrorType())) {
                log.warn("[LLM-Router] 主 Provider 调用失败 ({})，错误类型不可恢复，不触发 Fallback",
                        e.getErrorType());
                throw e;
            }
            log.warn("[LLM-Router] 主 Provider 调用失败 ({})，尝试 Fallback: {}",
                    e.getErrorType(), e.getMessage());
            LlmClient fallback = findFallback(client);
            if (fallback != null) {
                return fallback.chat(request);
            }
            log.warn("[LLM-Router] 无可用 Fallback Provider，抛出原始异常");
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
        AtomicBoolean streamStarted = new AtomicBoolean(false);
        try {
            client.stream(request, chunk -> {
                streamStarted.set(true);
                chunkConsumer.accept(chunk);
            });
        } catch (LlmException e) {
            if (!shouldFallback(e.getErrorType()) || streamStarted.get()) {
                if (streamStarted.get()) {
                    log.warn("[LLM-Router] 流式输出已开始，无法 Fallback: {}", e.getMessage());
                } else {
                    log.warn("[LLM-Router] 主 Provider 流式调用失败 ({})，错误类型不可恢复，不触发 Fallback",
                            e.getErrorType());
                }
                throw e;
            }
            log.warn("[LLM-Router] 主 Provider 流式调用失败 ({})，尝试 Fallback: {}",
                    e.getErrorType(), e.getMessage());
            LlmClient fallback = findFallback(client);
            if (fallback != null) {
                fallback.stream(request, chunkConsumer);
                return;
            }
            log.warn("[LLM-Router] 无可用 Fallback Provider，抛出原始异常");
            throw e;
        }
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

    /**
     * 判断错误类型是否应该触发 Fallback
     *
     * <p>仅网络超时、限流、Provider 服务端错误才切换备用 Provider。
     * 认证失败、模型不存在、响应格式错误不切换，避免无效重试。
     */
    private boolean shouldFallback(LlmException.ErrorType errorType) {
        return errorType == LlmException.ErrorType.NETWORK_TIMEOUT
                || errorType == LlmException.ErrorType.RATE_LIMITED
                || errorType == LlmException.ErrorType.PROVIDER_ERROR;
    }
}
