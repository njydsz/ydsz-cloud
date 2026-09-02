package com.njydsz.gateway.filter;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayErrorCode;
import com.njydsz.gateway.config.GatewayErrorWriter;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayMetrics;
import com.njydsz.gateway.config.PathGuard;

/**
 * 网关全局熔断过滤器（P0-A2，基于 Resilience4j）。
 *
 * <p>按路由 ID 维护独立熔断器，对下游调用失败率超阈值时快速失败并返回 503，
 * 防止下游服务雪崩时网关被拖垮：
 *
 * <ul>
 *   <li>滑动窗口统计（默认 10 次调用、最少 5 次调用才参与判定）
 *   <li>失败率超阈值（默认 50%）→ OPEN，此后请求直接拒绝（{@code CallNotPermittedException}）
 *   <li>等待 {@code wait-duration-in-open-state}（默认 10s）后进入 HALF_OPEN，放行少量探测流量
 *   <li>探测成功恢复 CLOSED，失败重新 OPEN
 * </ul>
 *
 * <h3>配置方式（任选其一）</h3>
 *
 * <p>1. spring.cloud.gateway 内置过滤器（全局路由自动生效）：
 *
 * <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: userinfo-service
 *           uri: lb://ydsz-userinfo
 *           predicates:
 *             - Path=/api/user/**
 *           filters:
 *             - name: CircuitBreaker
 *               args:
 *                 name: userinfoCircuitBreaker
 *                 fallbackUri: forward:/fallback/userinfo
 * </pre>
 *
 * <p>2. 本过滤器（按路由名称细粒度控制 + 指标接入 {@link GatewayMetrics}）：
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     circuit-breaker:
 *       failure-rate-threshold: 50
 *       wait-duration-in-open-state-ms: 10000
 *       sliding-window-size: 10
 *       minimum-number-of-calls: 5
 * </pre>
 *
 * <h3>响应式集成</h3>
 *
 * <p>以 Mono.defer + 许可获取 + doOnSuccess/doOnError 三段式手动记录替代
 * Resilience4j 预置响应式算子（订阅时获取许可、完成时记录成败，语义等价）。
 *
 * <p><b>豁免路径：</b>健康检查 / 认证入口等白名单路径不参与熔断，避免探针失败导致 K8s 摘除网关实例。
 *
 * <p><b>处理顺序：</b>{@code HIGHEST_PRECEDENCE + 45}，位于限流（+30）之后，路由转发（+100）之前。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(CircuitBreakerRegistry.class)
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "circuit-breaker",
    havingValue = "true",
    matchIfMissing = true)
public class CircuitBreakerGlobalFilter implements GlobalFilter, Ordered {

  /** 熔断器状态指标值：CLOSED */
  private static final int STATE_CLOSED = 0;

  /** 熔断器状态指标值：OPEN */
  private static final int STATE_OPEN = 1;

  /** 熔断器状态指标值：HALF_OPEN */
  private static final int STATE_HALF_OPEN = 2;

  /** 健康检查/探针路径前缀（豁免熔断） */
  private static final String ACTUATOR_PATH_PREFIX = "/actuator";

  private final CircuitBreakerRegistry circuitBreakerRegistry;

  private final GatewayMetrics gatewayMetrics;

  /** 失败率阈值（百分比），默认 50 */
  @Value("${ydsz.gateway.circuit-breaker.failure-rate-threshold:50}")
  private int failureRateThreshold;

  /** OPEN 状态持续时间（毫秒），默认 10s */
  @Value("${ydsz.gateway.circuit-breaker.wait-duration-in-open-state-ms:10000}")
  private long waitDurationInOpenStateMs;

  /** 滑动窗口大小（次数），默认 10 */
  @Value("${ydsz.gateway.circuit-breaker.sliding-window-size:10}")
  private int slidingWindowSize;

  /** 最少调用次数（低于此不参与判定），默认 5 */
  @Value("${ydsz.gateway.circuit-breaker.minimum-number-of-calls:5}")
  private int minimumNumberOfCalls;

  /** HALF_OPEN 状态下允许的探测调用数，默认 2 */
  @Value("${ydsz.gateway.circuit-breaker.permitted-number-of-calls-in-half-open-state:2}")
  private int permittedNumberOfCallsInHalfOpenState;

  /** 已注册状态指标监听的路由集合（避免重复注册事件监听器） */
  private final Set<String> metricListenersRegistered = ConcurrentHashMap.newKeySet();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // 豁免路径：健康检查 / 探针 / 白名单，避免探针失败导致 K8s 摘除网关实例
    String path = exchange.getRequest().getURI().getPath();
    if (PathGuard.isWhiteList(path) || path.startsWith(ACTUATOR_PATH_PREFIX)) {
      return chain.filter(exchange);
    }

    // 路由未命中（如未知路径）不参与熔断，交由后续过滤器链处理
    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    if (route == null) {
      return chain.filter(exchange);
    }
    String routeId = route.getId();

    CircuitBreaker circuitBreaker =
        circuitBreakerRegistry.circuitBreaker(routeId, defaultCircuitBreakerConfig());
    registerStateMetrics(routeId, circuitBreaker);

    // 三段式手动记录（替代 Resilience4j 预置响应式算子）：
    // 订阅时获取许可 → 完成记录成功 → 异常记录失败；熔断中直接抛 CallNotPermittedException
    return Mono.defer(
            () -> {
              if (!circuitBreaker.tryAcquirePermission()) {
                return Mono.error(
                    CallNotPermittedException.createCallNotPermittedException(circuitBreaker));
              }
              long startNanos = System.nanoTime();
              return chain
                  .filter(exchange)
                  .doOnSuccess(
                      v ->
                          circuitBreaker.onSuccess(
                              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                              TimeUnit.MILLISECONDS))
                  .doOnError(
                      t ->
                          circuitBreaker.onError(
                              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                              TimeUnit.MILLISECONDS,
                              t));
            })
        .onErrorResume(
            CallNotPermittedException.class,
            e -> rejectCircuitOpen(exchange, routeId));
  }

  /**
   * 构建熔断配置（P0-A2：可通过 {@code ydsz.gateway.circuit-breaker.*} 覆盖）。
   *
   * @return 熔断器配置
   */
  private CircuitBreakerConfig defaultCircuitBreakerConfig() {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold(failureRateThreshold)
        .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenStateMs))
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(slidingWindowSize)
        .minimumNumberOfCalls(minimumNumberOfCalls)
        .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build();
  }

  /**
   * 为路由注册熔断器状态指标监听（每个路由仅注册一次）。
   *
   * <p>将 Resilience4j 状态机事件映射为 {@code ydsz_gateway_circuit_breaker_state} 指标
   * （0=CLOSED, 1=OPEN, 2=HALF_OPEN），供 Grafana 观测网关熔断状态。
   *
   * @param routeId 路由 ID
   * @param circuitBreaker 熔断器实例
   */
  private void registerStateMetrics(String routeId, CircuitBreaker circuitBreaker) {
    if (!metricListenersRegistered.add(routeId)) {
      return;
    }
    gatewayMetrics.setCircuitBreakerState(routeId, STATE_CLOSED);
    circuitBreaker
        .getEventPublisher()
        .onStateTransition(
            (CircuitBreakerOnStateTransitionEvent event) -> {
              int state;
              CircuitBreaker.State toState = event.getStateTransition().getToState();
              if (toState == CircuitBreaker.State.OPEN) {
                state = STATE_OPEN;
              } else if (toState == CircuitBreaker.State.HALF_OPEN) {
                state = STATE_HALF_OPEN;
              } else {
                state = STATE_CLOSED;
              }
              gatewayMetrics.setCircuitBreakerState(routeId, state);
            });
    log.info("[CircuitBreaker] 已为路由 {} 注册熔断状态指标", routeId);
  }

  /**
   * 熔断打开时返回 503 统一错误响应。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param routeId 路由 ID（用于日志）
   * @return 完成信号 Mono
   */
  private Mono<Void> rejectCircuitOpen(ServerWebExchange exchange, String routeId) {
    log.warn("[CircuitBreaker] 路由 {} 熔断打开，拒绝请求 path={}",
        routeId, exchange.getRequest().getURI().getPath());
    return GatewayErrorWriter.write(
        exchange,
        HttpStatus.SERVICE_UNAVAILABLE,
        GatewayErrorCode.CIRCUIT_BREAKER_OPEN,
        GatewayErrorCode.CIRCUIT_BREAKER_OPEN.getMessageKey(),
        exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID));
  }

  /** 过滤器执行顺序：{@code HIGHEST_PRECEDENCE + 45}。 */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.CIRCUIT_BREAKER.getOrder();
  }
}
