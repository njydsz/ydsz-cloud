package com.njydsz.agent.infra.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.gateway.CacheMetricsRecorder;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;

/**
 * 带缓存的 LLM 客户端（装饰器模式）
 *
 * <p>在调用实际 LLM 之前先查询缓存，命中时直接返回缓存结果， 跳过 LLM 调用以节省成本与延迟。
 *
 * <p>缓存策略：
 *
 * <ul>
 *   <li>仅缓存 temperature=0 的确定性请求
 *   <li>仅缓存无工具调用的简单对话
 *   <li>缓存 key = SHA-256(model + systemPrompt + userMessage)，精确匹配（详见 {@link SemanticLlmCache}）
 *   <li>缓存命中时返回包含特殊标记 {@code [cached]} 的响应
 * </ul>
 *
 * <p><b>缓存击穿防护（P1 修复）</b>：同一缓存 key 高并发未命中时，仅放行一个线程发起 LLM 调用， 其余线程等待其结果，
 * 避免全部请求同时打穿到 LLM（Singleflight 语义）。
 *
 * <p><b>缓存命中率指标（P1 增强）</b>：通过 {@link CacheMetricsRecorder} SPI（domain 层接口）上报
 * {@code agent_cache_hits_total} / {@code agent_cache_misses_total}，便于度量缓存效果； 具体采集由 server 层实现注入。
 * 指标组件可为 null（未装配时跳过指标采集，不影响功能）。
 *
 * <p><b>与安全护栏的交互</b>：输出护栏（PII 脱敏 / 内容拦截）在应用服务层执行， 本装饰器位于 LLM 客户端层，缓存写入的是 LLM
 * 原始输出；命中缓存后仍会经过服务层输出护栏，不会绕过安全管控。
 *
 * <p><b>线程安全</b>：无状态装饰器 + {@link ConcurrentHashMap} 互斥表，可安全并发调用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class CachedLlmClient implements LlmClient {

  /** 等待在途 LLM 调用的最大时间（秒），超时后降级为直接调用避免饿死 */
  private static final long INFLIGHT_WAIT_SECONDS = 30;

  /** 被装饰的实际 LLM 客户端 */
  private final LlmClient delegate;

  /** 语义缓存实例 */
  private final SemanticLlmCache cache;

  /** 指标采集组件（记录缓存命中率，可为 null） */
  private final CacheMetricsRecorder metrics;

  /** 在途调用表（key=缓存 key，value=对应 LLM 调用结果 Future），用于缓存击穿防护 */
  private final Map<String, CompletableFuture<ChatResponse>> inflight = new ConcurrentHashMap<>();

  public CachedLlmClient(LlmClient delegate, SemanticLlmCache cache, CacheMetricsRecorder metrics) {
    this.delegate = delegate;
    this.cache = cache;
    this.metrics = metrics;
  }

  @Override
  public ChatResponse chat(ChatRequest request) {
    boolean cacheable = SemanticLlmCache.isCacheable(request.getTemperature(), hasTools(request));
    // 仅对可缓存请求提取缓存内容；user 消息为空视为提取失败，直接跳过缓存避免 key 恒定的串流风险
    Map.Entry<String, String> cacheContent =
        cacheable ? SemanticLlmCache.extractCacheableContent(request.getMessages()) : null;
    if (cacheContent == null || cacheContent.getValue().isBlank()) {
      return delegate.chat(request);
    }

    String model = request.getModel();
    // 1. 缓存命中直接返回（记录命中指标）
    SemanticLlmCache.CachedLlmResponse cached =
        cache.get(model, cacheContent.getKey(), cacheContent.getValue());
    if (cached != null) {
      if (metrics != null) {
        metrics.recordCacheHit(delegate.getProvider());
      }
      log.info("[CachedLLM] 缓存命中，跳过 LLM 调用: model={}", model);
      return buildCachedResponse(request, cached);
    }
    if (metrics != null) {
      metrics.recordCacheMiss(delegate.getProvider());
    }

    // 2. 缓存击穿防护：同 key 并发未命中仅放行一个 LLM 调用，其余等待其结果
    String lockKey = cache.buildKey(model, cacheContent.getKey(), cacheContent.getValue());
    CompletableFuture<ChatResponse> future = new CompletableFuture<>();
    CompletableFuture<ChatResponse> existing = inflight.putIfAbsent(lockKey, future);
    if (existing != null) {
      // 已有调用在途：等待其结果（带超时兜底，避免极端场景饿死）
      try {
        return existing.get(INFLIGHT_WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return delegate.chat(request);
      } catch (Exception e) {
        log.warn("[CachedLLM] 等待在途调用超时，降级直接调用: {}", e.getMessage());
        return doChatAndCache(request, cacheContent);
      }
    }
    try {
      ChatResponse response = doChatAndCache(request, cacheContent);
      future.complete(response);
      return response;
    } catch (RuntimeException e) {
      future.completeExceptionally(e);
      throw e;
    } finally {
      inflight.remove(lockKey);
    }
  }

  /**
   * 调用实际 LLM 并在满足条件时写回缓存。
   *
   * @param request 聊天请求
   * @param cacheContent 提取的缓存内容（key=systemPrompt, value=userMessage）
   * @return LLM 响应
   */
  private ChatResponse doChatAndCache(
      ChatRequest request, Map.Entry<String, String> cacheContent) {
    ChatResponse response = delegate.chat(request);
    // 写入缓存：仅当响应内容非空时写入
    if (response.getContent() != null && !response.getContent().isBlank()) {
      cache.put(
          request.getModel(),
          cacheContent.getKey(),
          cacheContent.getValue(),
          response.getContent(),
          delegate.getProvider());
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
   * @param cached 缓存的响应内容
   * @return 构造的 ChatResponse
   */
  private ChatResponse buildCachedResponse(
      ChatRequest request, SemanticLlmCache.CachedLlmResponse cached) {
    return new ChatResponse(
        "chatcmpl-cached-" + System.identityHashCode(cached),
        request.getModel(),
        ChatMessage.assistant(
            cached.content(), null, TokenUsage.zero()),
        TokenUsage.zero(),
        "stop",
        List.of());
  }
}
