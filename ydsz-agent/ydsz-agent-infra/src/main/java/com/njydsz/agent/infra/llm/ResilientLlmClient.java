package com.njydsz.agent.infra.llm;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;

/**
 * 带熔断保护的 LLM 客户端装饰器（Resilience4j）
 *
 * <p>为底层 LLM 客户端提供安全护栏，防止 Provider 故障或服务不可用时流量持续涌入导致雪崩：
 *
 * <ul>
 *   <li><b>熔断器</b>：在 Provider 错误率超过阈值时快速失败，给予远端恢复时间；经过冷却期后半开探测</li>
 *   <li><b>指标采集</b>：熔断器事件通过 Micrometer 输出到 Prometheus，与现有 {@code agent_llm_*} 指标互补</li>
 * </ul>
 *
 * <p>熔断器基于 Provider 名称隔离，每个 Provider 独立计数、独立状态机。
 *
 * <h3>熔断策略</h3>
 *
 * <ul>
 *   <li>基于计数的时间窗口（sliding window = 10 次请求）</li>
 *   <li>失败率阈值 50%（半开前需错误率过半）</li>
 *   <li>单次调用超时 60s（与 LLM 默认调用超时一致）</li>
 *   <li>半开态最多允许 3 个探测请求</li>
 *   <li>冷却期 30s（OPEN → HALF_OPEN 的最短等待）</li>
 * </ul>
 *
 * <p><b>异常处理语义</b>：
 *
 * <ul>
 *   <li>{@link LlmException.ErrorType#NETWORK_TIMEOUT} / {@link LlmException.ErrorType#PROVIDER_ERROR} / {@link LlmException.ErrorType#RATE_LIMITED}
 *       — 计入熔断统计（远端故障），触发熔断</li>
 *   <li>{@link LlmException.ErrorType#AUTH_FAILED} / {@link LlmException.ErrorType#MODEL_NOT_FOUND} / {@link LlmException.ErrorType#CANCELED}
 *       — 不计入熔断统计（本地配置错误 / 用户主动取消），立即抛出</li>
 *   <li>{@link TimeoutException} / {@link InterruptedException} — 计入熔断统计</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class ResilientLlmClient implements LlmClient {

  /** 滑动窗口大小（基于计数） */
  private static final int SLIDING_WINDOW_SIZE = 10;

  /** 熔断触发的失败率阈值（百分比） */
  private static final float FAILURE_RATE_THRESHOLD = 50.0f;

  /** 调用超时时间（秒，与 LLM 默认超时一致） */
  private static final int CALL_TIMEOUT_SECONDS = 60;

  /** 半开态最大探测请求数 */
  private static final int PERMITTED_CALLS_IN_HALF_OPEN = 3;

  /** OPEN → HALF_OPEN 的冷却时间（秒） */
  private static final int WAIT_DURATION_SECONDS = 30;

  /** 最小调用次数（达到此数量后才计算失败率） */
  private static final int MINIMUM_NUMBER_OF_CALLS = 5;

  /** 被装饰的实际 LLM 客户端 */
  private final LlmClient delegate;

  /** Resilience4j 熔断器实例（基于 per-Provider 隔离） */
  private final CircuitBreaker circuitBreaker;

  /**
   * 创建带熔断保护的 LLM 客户端。
   *
   * @param delegate 被装饰的原 LLM 客户端（不能为 null）
   * @param providerName Provider 名称（用于隔离熔断器实例和指标标签）
   */
  public ResilientLlmClient(LlmClient delegate, String providerName) {
    this(delegate, providerName, CircuitBreakerRegistry.ofDefaults());
  }

  /**
   * 创建带熔断保护的 LLM 客户端（支持自定义 Registry）。
   *
   * @param delegate 被装饰的原 LLM 客户端
   * @param providerName Provider 名称
   * @param registry 熔断器注册表（可接入 Micrometer 指标）
   */
  public ResilientLlmClient(LlmClient delegate, String providerName, CircuitBreakerRegistry registry) {
    if (delegate == null) {
      throw new IllegalArgumentException("delegate 不能为 null");
    }
    this.delegate = delegate;
    this.circuitBreaker = registry.circuitBreaker(
        "llm-" + providerName, createCircuitBreakerConfig());
    this.circuitBreaker.getEventPublisher()
        .onStateTransition(
            event -> log.warn("[LLM-{}] 熔断器状态转换: {}", providerName, event.getStateTransition()))
        .onError(
            event -> log.debug("[LLM-{}] 熔断器记录错误: {}", providerName, event.getThrowable().getMessage()))
        .onSuccess(
            event -> log.trace("[LLM-{}] 熔断器记录成功: {}", providerName, event.getElapsedDuration()));
  }

  @Override
  public ChatResponse chat(ChatRequest request) {
    return executeWithCircuitBreaker("chat", () -> delegate.chat(request));
  }

  @Override
  public void stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
    executeWithCircuitBreaker("stream", () -> {
      delegate.stream(request, chunkConsumer);
      return null;
    });
  }

  @Override
  public boolean supports(String modelId) {
    return delegate.supports(modelId);
  }

  @Override
  public String getProvider() {
    return delegate.getProvider();
  }

  @Override
  public List<Float> embed(String text) {
    return executeWithCircuitBreaker("embed", () -> delegate.embed(text));
  }

  // ======================== 内部实现 ========================

  /**
   * 在熔断器保护下执行 LLM 调用。
   *
   * @param operation 操作名称（chat / stream / embed），用于日志标识
   * @param supplier 实际 LLM 调用
   * @return LLM 响应
   */
  private <T> T executeWithCircuitBreaker(String operation, Supplier<T> supplier) {
    try {
      return CircuitBreaker.decorateSupplier(circuitBreaker, supplier).get();
    } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
      String provider = delegate.getProvider();
      log.warn("[LLM-{}] 熔断器 OPEN 态快速拒绝: operation={}, message={}",
          provider, operation, e.getMessage());
      throw new LlmException(
          "LLM Provider " + provider + " 熔断器已断开（OPEN），请稍后重试或切换 Provider",
          LlmException.ErrorType.PROVIDER_ERROR);
    }
  }

  /**
   * 创建熔断器配置。
   *
   * <p>关键参数：
   *
   * <ul>
   *   <li>记录可重试错误类型（网络/限流/Provider），忽略本地配置错误</li>
   *   <li>滑动窗口 10 次，最小 5 次计算，失败率 50%</li>
   *   <li>半开许可 3 个探测，冷却 30 秒</li>
   * </ul>
   *
   * @return CircuitBreakerConfig 实例
   */
  private static CircuitBreakerConfig createCircuitBreakerConfig() {
    return CircuitBreakerConfig.custom()
        // 基于计数滑动窗口（每次调用计数一次）
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(SLIDING_WINDOW_SIZE)
        .minimumNumberOfCalls(MINIMUM_NUMBER_OF_CALLS)
        .failureRateThreshold(FAILURE_RATE_THRESHOLD)
        // 记录哪些异常为"失败"
        .recordExceptions(
            LlmException.class,
            TimeoutException.class,
            InterruptedException.class,
            IOException.class)
        // 忽略本地错误（防止误触发熔断）
        .ignoreExceptions(
            IllegalArgumentException.class)
        // 半开态允许的探测请求数
        .permittedNumberOfCallsInHalfOpenState(PERMITTED_CALLS_IN_HALF_OPEN)
        // OPEN → HALF_OPEN 等待时间
        .waitDurationInOpenState(Duration.ofSeconds(WAIT_DURATION_SECONDS))
        // 慢调用不计入（LLM 本身可能慢）
        .slowCallDurationThreshold(Duration.ofSeconds(CALL_TIMEOUT_SECONDS))
        .slowCallRateThreshold(100.0f)
        // 自动从 OPEN 过渡到 HALF_OPEN
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build();
  }
}
