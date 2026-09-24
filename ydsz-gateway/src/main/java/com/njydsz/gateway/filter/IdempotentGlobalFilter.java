package com.njydsz.gateway.filter;

import java.time.Duration;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.common.redis.service.ops.ReactiveStringRedisOps;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayErrorCode;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.exception.GatewayErrorWriter;

/**
 * B1: 请求幂等过滤器。
 *
 * <p>防止客户端因超时重试/网络抖动导致同一业务请求被重复处理。
 *
 * <h3>工作机制</h3>
 *
 * <ol>
 *   <li>客户端生成唯一的 {@code X-Idempotency-Key}（如 UUID），在重试时保持同一 Key 不变</li>
 *   <li>网关通过 {@code SETNX}（SET if Not eXists）在 Redis 写入幂等标记</li>
 *   <li>写入成功 → 放行请求；写入失败（Key 已存在） → 返回 409 Conflict 拒绝重复请求</li>
 *   <li>标记默认 TTL 30 秒后自动过期，表示幂等窗口——仅在此窗口内的重试会被拦截</li>
 * </ol>
 *
 * <h3>作用范围</h3>
 *
 * <p>仅对 POST / PUT / PATCH / DELETE 等变更类请求启用幂等保护，GET / HEAD / OPTIONS 默认跳过。
 *
 * <p>幂等 Key 未携带时，请求不做幂等拦截（向后兼容），确保旧版客户端和浏览器直接请求不受影响。
 *
 * <h3>配置项</h3>
 *
 * <ul>
 *   <li>{@code ydsz.gateway.idempotent.enabled=true}：是否启用幂等过滤器</li>
 *   <li>{@code ydsz.gateway.idempotent.ttl-seconds=30}：幂等窗口期（秒）</li>
 *   <li>{@code ydsz.gateway.idempotent.key-header=X-Idempotency-Key}：客户端传入幂等 Key 的请求头名</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 *
 * <p>位于 {@link AuthGlobalFilter}(+10) 之后、{@code GatewayApiKeyAuthFilter}(+15) 之前，确保已注入的身份信息可融入幂等 Key
 * （同一用户对不同业务操作的幂等 Key 独立、不同用户间的同名 Key 相互隔离）。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "idempotent",
    havingValue = "true",
    matchIfMissing = false)
public class IdempotentGlobalFilter implements GlobalFilter, Ordered {

  /** GET / HEAD / OPTIONS 等安全方法不启用幂等保护（只读请求无需幂等拦截）。 */
  private static final List<HttpMethod> MUTATING_METHODS =
      List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

  /** 响应头：标明本次请求是否为重复请求（对调用方可观测）。 */
  private static final String HEADER_IDEMPOTENT_REPLAYED = "X-Idempotent-Replayed";

  private final ReactiveStringRedisOps redisOps;

  /** 幂等窗口期（秒），默认 30s。 */
  @Value("${ydsz.gateway.idempotent.ttl-seconds:30}")
  private long idempotentTtlSeconds;

  /** 客户端幂等 Key 请求头名。 */
  @Value("${ydsz.gateway.idempotent.key-header:X-Idempotency-Key}")
  private String idempotencyKeyHeader;

  /**
   * 带 userId 维度的幂等 Redis key 构造。
   *
   * <p>Key 格式：{@code idempotent:{userId}:{sha256(idempotencyKey)}}
   * 取 SHA-256 避免客户端传入过长 Key 以及 userId 注入风险。
   *
   * @param userId 用户 ID（可为空）
   * @param rawKey 客户端传入的幂等 Key
   * @return Redis 存储 key
   */
  private String buildRedisKey(String userId, String rawKey) {
    String hash = DigestUtils.sha256Hex(rawKey);
    String userPart = (userId != null && !userId.isBlank()) ? userId : "anonymous";
    return "idempotent:" + userPart + ":" + hash;
  }

  /**
   * 判断 HTTP 方法是否需要幂等保护。
   *
   * @param method HTTP 方法
   * @return true=需要幂等拦截
   */
  private boolean isMutatingMethod(HttpMethod method) {
    return method != null && MUTATING_METHODS.contains(method);
  }

  /**
   * 幂等过滤逻辑入口：检查幂等 Key → SETNX 写入 → 冲突返回 409 或放行。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 放行（Mono 放行链）或 409 冲突拒绝（完成信号 Mono）
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    HttpMethod method = request.getMethod();

    // 非变更方法不启用幂等保护
    if (!isMutatingMethod(method)) {
      return chain.filter(exchange);
    }

    // 请求未携带幂等 Key：跳过（向后兼容）
    String rawKey = request.getHeaders().getFirst(idempotencyKeyHeader);
    if (rawKey == null || rawKey.isBlank()) {
      return chain.filter(exchange);
    }

    String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
    String redisKey = buildRedisKey(userId, rawKey);

    // SETNX + TTL：首次请求写入成功（true），重复请求写入失败（false → 409）
    return redisOps.setIfAbsent(redisKey, "1", Duration.ofSeconds(idempotentTtlSeconds))
        .flatMap(
            isFirstRequest -> {
              if (Boolean.TRUE.equals(isFirstRequest)) {
                log.debug("[Idempotent] 幂等校验通过 (firstRequest) key={}", redisKey);
                return chain.filter(exchange);
              }
              // 重复请求：200 + 特殊响应头 OR 409（当前采用 409，明确区分首次请求成功）
              log.warn("[Idempotent] 重复请求被拦截 key={} path={}",
                  redisKey, request.getURI().getPath());
              return rejectDuplicateRequest(exchange);
            })
        .onErrorResume(
            e -> {
              // Redis 异常时降级放行（避免限流/幂等组件导致全链路不可用）
              log.warn("[Idempotent] Redis 幂等检查异常，降级放行: {}", e.getMessage());
              return chain.filter(exchange);
            });
  }

  /**
   * 返回 409 Conflict 拒绝重复请求。
   *
   * <p>复用 {@link GatewayErrorCode#IDEMPOTENT_DUPLICATE}（新增的幂等冲突码）+ {@link GatewayErrorWriter} 统一响应。
   *
   * @param exchange 服务器 Web 交换上下文
   * @return 完成信号 Mono（已写出 409 响应）
   */
  private Mono<Void> rejectDuplicateRequest(ServerWebExchange exchange) {
    // 注入可观测性响应头
    exchange.getResponse().getHeaders().add(HEADER_IDEMPOTENT_REPLAYED, "true");
    return GatewayErrorWriter.write(
        exchange,
        HttpStatus.CONFLICT,
        GatewayErrorCode.IDEMPOTENT_DUPLICATE,
        GatewayErrorCode.IDEMPOTENT_DUPLICATE.getMessageKey(),
        exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID));
  }

  /**
   * 过滤器顺序：位于 AuthGlobalFilter(+10) 之后、GatewayApiKeyAuthFilter(+15) 之前。
   *
   * <p>左边界：确保 X-User-Id 已填充，为 userId 维度隔离提供支持。<br>
   * 右边界：早于 IP / API Key 鉴权，避免重复请求消耗宝贵的限流配额。
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.IDEMPOTENT.getOrder();
  }
}
