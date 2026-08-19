package com.njydsz.agent.infra.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.njydsz.agent.domain.model.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;

/**
 * LLM 客户端路由器
 *
 * <p>按模型配置路由到对应的 {@link LlmClient} 实现，支持：
 *
 * <ul>
 *   <li>按 Provider 匹配（openai / deepseek / qwen / ollama）
 *   <li>Fallback 降级：主模型不可用时自动切换备用模型
 *   <li>运行时动态注册/注销 Provider
 * </ul>
 *
 * <h3>Fallback 策略</h3>
 *
 * <p>仅对以下可恢复错误类型触发 Fallback：
 *
 * <ul>
 *   <li>{@code NETWORK_TIMEOUT} — 网络超时，切换 Provider 可恢复
 *   <li>{@code RATE_LIMITED} — 限流（429），切换 Provider 分散负载
 *   <li>{@code PROVIDER_ERROR} — Provider 服务端错误（5xx），切换 Provider 可恢复
 * </ul>
 *
 * <p>以下错误类型<b>不触发</b> Fallback，直接抛出：
 *
 * <ul>
 *   <li>{@code AUTH_FAILED} — 认证失败（401/403），多为配置错误，需运维介入
 *   <li>{@code MODEL_NOT_FOUND} — 模型不存在（404），换 Provider 也未必支持
 *   <li>{@code INVALID_RESPONSE} — 响应格式错误，多为解析 bug 或 API 变更
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class LlmClientRouter implements LlmClient {

  /** 已注册的 Provider 客户端映射（key=provider name） */
  private final Map<String, LlmClient> clients = new ConcurrentHashMap<>();

  /** 默认客户端（无匹配 Provider 时使用），使用 AtomicReference 保障并发注册的可见性与原子性 */
  private final AtomicReference<LlmClient> defaultClient = new AtomicReference<>();

  /**
   * 注册一个 LLM Provider 客户端。
   *
   * <p>以 {@code client.getProvider()} 为键写入注册表，同名 Provider 会被静默覆盖， 便于运行时热替换配置。首个注册的客户端自动成为 {@code
   * defaultClient}， 用于兜底处理无任何 Provider 显式 {@code supports} 的模型。
   *
   * <p><b>并发</b>：注册表基于 {@link ConcurrentHashMap}，写入本身线程安全； 但 {@code defaultClient}
   * 为普通字段，仅适合在应用启动阶段完成注册， 不建议在高并发请求期间动态调用。
   *
   * @param client 待注册的客户端，不可为 {@code null}，其 {@code getProvider()} 需返回稳定唯一值
   */
  public void register(LlmClient client) {
    clients.put(client.getProvider(), client);
    // 首个注册的客户端通过 CAS 成为 defaultClient，避免并发注册时重复赋值
    defaultClient.compareAndSet(null, client);
    log.info("[LLM-Router] 注册 Provider: {}", client.getProvider());
  }

  /**
   * 注销指定 Provider，用于 Provider 下线或密钥失效时的快速摘除。
   *
   * <p>若被摘除的正是当前默认客户端，会任取剩余一个客户端顶替；注册表清空后 {@code defaultClient} 置为 {@code null}，此后所有调用将以 {@code
   * MODEL_NOT_FOUND} 抛出 {@link LlmException}。
   *
   * <p>不存在的 provider 视为空操作，方法幂等。已在途的请求不受影响。
   *
   * @param provider Provider 名称，与注册时 {@code getProvider()} 返回值一致
   */
  public void unregister(String provider) {
    clients.remove(provider);
    // 原子方式更新 defaultClient：仅当当前默认值匹配被移除的 Provider 时才替换
    defaultClient.updateAndGet(
        current -> {
          if (current != null && current.getProvider().equals(provider)) {
            return clients.values().stream().findFirst().orElse(null);
          }
          return current;
        });
  }

  @Override
  public ChatResponse chat(ChatRequest request) {
    LlmClient client = resolveClient(request.getModel());
    if (client == null) {
      throw new LlmException(
          "无可用 LLM Provider，model=" + request.getModel(), LlmException.ErrorType.MODEL_NOT_FOUND);
    }
    try {
      return client.chat(request);
    } catch (LlmException e) {
      if (!shouldFallback(e.getErrorType())) {
        log.warn("[LLM-Router] 主 Provider 调用失败 ({})，错误类型不可恢复，不触发 Fallback", e.getErrorType());
        throw e;
      }
      log.warn(
          "[LLM-Router] 主 Provider 调用失败 ({})，尝试 Fallback: {}", e.getErrorType(), e.getMessage());
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
      throw new LlmException(
          "无可用 LLM Provider，model=" + request.getModel(), LlmException.ErrorType.MODEL_NOT_FOUND);
    }
    AtomicBoolean streamStarted = new AtomicBoolean(false);
    try {
      client.stream(
          request,
          chunk -> {
            streamStarted.set(true);
            chunkConsumer.accept(chunk);
          });
    } catch (LlmException e) {
      if (!shouldFallback(e.getErrorType()) || streamStarted.get()) {
        if (streamStarted.get()) {
          log.warn("[LLM-Router] 流式输出已开始，无法 Fallback: {}", e.getMessage());
        } else {
          log.warn("[LLM-Router] 主 Provider 流式调用失败 ({})，错误类型不可恢复，不触发 Fallback", e.getErrorType());
        }
        throw e;
      }
      log.warn(
          "[LLM-Router] 主 Provider 流式调用失败 ({})，尝试 Fallback: {}", e.getErrorType(), e.getMessage());
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

  /**
   * 获取已注册的全部 Provider 名称。
   *
   * @return Provider 名称的不可修改列表（如 [openai, qwen, deepseek]）
   */
  public List<String> getAvailableProviders() {
    return List.copyOf(clients.keySet());
  }

  private LlmClient resolveClient(String model) {
    for (LlmClient c : clients.values()) {
      if (c.supports(model)) {
        return c;
      }
    }
    return defaultClient.get();
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
   * <p>仅网络超时、限流、Provider 服务端错误才切换备用 Provider。 认证失败、模型不存在、响应格式错误不切换，避免无效重试。
   */
  private boolean shouldFallback(LlmException.ErrorType errorType) {
    return errorType == LlmException.ErrorType.NETWORK_TIMEOUT
        || errorType == LlmException.ErrorType.RATE_LIMITED
        || errorType == LlmException.ErrorType.PROVIDER_ERROR;
  }
}
