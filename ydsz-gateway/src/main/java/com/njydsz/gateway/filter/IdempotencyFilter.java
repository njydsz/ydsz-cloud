package com.njydsz.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.IdempotencyProperties;

/**
 * P3-7: 请求幂等性全局过滤器
 *
 * <p>基于 {@code Idempotency-Key} 请求头实现请求幂等性保证，防止业务重复提交。
 *
 * <h3>工作原理</h3>
 *
 * <ol>
 *   <li>客户端在请求头中携带 {@code Idempotency-Key}（UUID 或业务唯一标识）
 *   <li>网关检查 Redis 中是否已有该 Key 的缓存结果
 *   <li>如果命中缓存：直接返回缓存的响应（短路，不转发下游）
 *   <li>如果未命中：放行请求，响应成功后缓存结果（TTL 可配置）
 * </ol>
 *
 * <h3>Redis 键设计</h3>
 *
 * <pre>
 *   ydsz:idempotency:{sha256(userId:key)}  → {status, body}
 * </pre>
 *
 * <h3>幂等键生成</h3>
 *
 * <p>使用 {@code userId + idempotencyKey} 的 SHA-256 摘要作为 Redis key， 避免不同用户使用相同 idempotencyKey 时冲突。
 *
 * <h3>执行顺序</h3>
 *
 * <p>{@code HIGHEST_PRECEDENCE + 28}，在限流（+30）之前执行， 确保重复请求在限流检查之前被拦截，避免浪费限流配额。
 *
 * @since 3.7.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.idempotency",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class IdempotencyFilter implements GlobalFilter, Ordered {

  /** 幂等性请求头名称 */
  private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

  /** 幂等性重放响应头 */
  private static final String HEADER_IDEMPOTENCY_REPLAYED = "X-Idempotency-Replayed";

  /** Redis 键前缀 */
  private static final String REDIS_KEY_PREFIX = "ydsz:idempotency:";

  private final IdempotencyProperties properties;
  private final ReactiveStringRedisTemplate redisTemplate;

  /**
   * P3-7: 幂等性核心过滤器
   *
   * <p>检查 Idempotency-Key 请求头，命中缓存则短路返回，未命中则包装响应以缓存结果。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 放行或短路返回缓存结果的完成信号 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }

    ServerHttpRequest request = exchange.getRequest();
    HttpMethod method = request.getMethod();

    // 仅对配置的 HTTP 方法生效
    if (!isIdempotencyMethod(method)) {
      return chain.filter(exchange);
    }

    // 仅对配置的路径前缀生效（空列表表示全部路径）
    if (!isIdempotencyPath(request.getURI().getPath())) {
      return chain.filter(exchange);
    }

    // 检查 Idempotency-Key 请求头
    String idempotencyKey = request.getHeaders().getFirst(HEADER_IDEMPOTENCY_KEY);
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      // 无幂等键，放行（幂等性是可选增强，不强制要求）
      return chain.filter(exchange);
    }

    // 幂等键长度校验（防止恶意超长 key）
    if (idempotencyKey.length() > 128) {
      log.warn("[Idempotency] 幂等键过长，跳过缓存: keyLength={}", idempotencyKey.length());
      return chain.filter(exchange);
    }

    String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
    String redisKey = buildRedisKey(userId, idempotencyKey);

    // 尝试从 Redis 获取缓存结果
    return redisTemplate
        .opsForValue()
        .get(redisKey)
        .flatMap(
            cachedResult -> {
              // 命中缓存：短路返回缓存的响应
              return replayCachedResponse(exchange, cachedResult);
            })
        .switchIfEmpty(
            Mono.defer(
                () -> {
                  // 未命中缓存：放行请求，并在响应成功后缓存结果
                  return proceedAndCache(exchange, chain, redisKey);
                }))
        .onErrorResume(
            e -> {
              // Redis 异常时降级放行（不影响业务）
              log.warn("[Idempotency] Redis 查询异常，降级放行: {}", e.getMessage());
              return chain.filter(exchange);
            });
  }

  /**
   * P3-7: 判断是否为需要幂等性保护的 HTTP 方法
   *
   * @param method HTTP 方法
   * @return true=需要幂等性保护
   */
  private boolean isIdempotencyMethod(HttpMethod method) {
    return properties.getMethods().stream().anyMatch(m -> m.equalsIgnoreCase(method.name()));
  }

  /**
   * P3-7: 判断路径是否需要幂等性保护
   *
   * @param path 请求路径
   * @return true=需要幂等性保护
   */
  private boolean isIdempotencyPath(String path) {
    List<String> prefixes = properties.getPathPrefixes();
    if (prefixes == null || prefixes.isEmpty()) {
      // 空列表表示全部路径
      return true;
    }
    return prefixes.stream().anyMatch(path::startsWith);
  }

  /**
   * P3-7: 构建 Redis 键（userId + idempotencyKey 的 SHA-256 摘要）
   *
   * @param userId 用户 ID（可为空）
   * @param idempotencyKey 幂等键
   * @return Redis 键字符串
   */
  private String buildRedisKey(String userId, String idempotencyKey) {
    String raw = (userId != null ? userId : "anonymous") + ":" + idempotencyKey;
    return REDIS_KEY_PREFIX + sha256(raw);
  }

  /**
   * P3-7: 从缓存中重放响应（短路返回）
   *
   * <p>缓存格式: {"status":200,"body":"..."}
   *
   * @param exchange 服务器 Web 交换上下文
   * @param cachedResult 缓存的响应 JSON 字符串
   * @return 完成信号 Mono
   */
  private Mono<Void> replayCachedResponse(ServerWebExchange exchange, String cachedResult) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.OK);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // 标记为重放响应
    if (properties.getResponseHeaders().isEnabled()) {
      response.getHeaders().add(HEADER_IDEMPOTENCY_REPLAYED, "true");
    }

    byte[] bytes = cachedResult.getBytes(StandardCharsets.UTF_8);
    DataBuffer buffer = response.bufferFactory().wrap(bytes);

    log.debug("[Idempotency] 幂等性命中，重放缓存响应: path={}", exchange.getRequest().getURI().getPath());
    return response.writeWith(Mono.just(buffer));
  }

  /**
   * P3-7: 放行请求并在响应成功后缓存结果
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @param redisKey Redis 键
   * @return 完成信号 Mono
   */
  private Mono<Void> proceedAndCache(
      ServerWebExchange exchange, GatewayFilterChain chain, String redisKey) {
    ServerHttpResponse originalResponse = exchange.getResponse();

    // 使用响应装饰器拦截响应体
    ServerHttpResponseDecorator decoratedResponse =
        new ServerHttpResponseDecorator(originalResponse) {
          @Override
          public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return DataBufferUtils.join(body)
                .flatMap(
                    dataBuffer -> {
                      byte[] content = new byte[dataBuffer.readableByteCount()];
                      dataBuffer.read(content);
                      DataBufferUtils.release(dataBuffer);

                      // 仅缓存 2xx 响应
                      HttpStatus statusCode = getStatusCode();
                      if (statusCode != null && statusCode.is2xxSuccessful()) {
                        cacheResponse(redisKey, content);
                      }

                      DataBuffer wrapped = originalResponse.bufferFactory().wrap(content);
                      return super.writeWith(Mono.just(wrapped));
                    })
                .onErrorResume(
                    e -> {
                      log.debug("[Idempotency] 响应拦截异常: {}", e.getMessage());
                      return super.writeWith(body);
                    });
          }
        };

    return chain.filter(exchange.mutate().response(decoratedResponse).build());
  }

  /**
   * P3-7: 缓存响应到 Redis
   *
   * @param redisKey Redis 键
   * @param content 响应体内容
   */
  private void cacheResponse(String redisKey, byte[] content) {
    // 限制缓存大小
    if (content.length > properties.getMaxResponseSize()) {
      log.debug("[Idempotency] 响应体过大，跳过缓存: size={}", content.length);
      return;
    }

    String bodyStr = new String(content, StandardCharsets.UTF_8);
    // 简单缓存格式：直接存储原始响应体（通常是 JSON）
    // 重放时直接返回原始响应体，保留原始格式

    redisTemplate
        .opsForValue()
        .set(redisKey, bodyStr, Duration.ofSeconds(properties.getKeyTtlSeconds()))
        .onErrorResume(
            e -> {
              log.debug("[Idempotency] 缓存写入失败: {}", e.getMessage());
              return Mono.empty();
            })
        .subscribe();
  }

  /**
   * P3-7: 计算字符串的 SHA-256 摘要（十六进制小写）
   *
   * @param input 输入字符串
   * @return SHA-256 摘要
   */
  private static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      return "sha256-unavailable-" + input.hashCode();
    }
  }

  /**
   * 过滤器顺序：{@code HIGHEST_PRECEDENCE + 28}。
   *
   * <p>在限流（+30）之前执行，确保重复请求在限流检查之前被拦截。
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.IDEMPOTENCY.getOrder();
  }
}
