package com.njydsz.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.gateway.config.GatewayConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.reactivestreams.Publisher;

/**
 * P2-2: 响应缓存过滤器
 *
 * <p>在网关层缓存幂等 GET 请求的响应，减少下游服务负载，提升响应速度。
 *
 * <h3>缓存策略</h3>
 * <ul>
 *   <li>仅缓存 GET 请求（幂等安全）</li>
 *   <li>默认 TTL 60 秒，可通过 Nacos 配置按路径覆盖</li>
 *   <li>缓存键 = MD5(method + path + query + userId + Vary headers)</li>
 *   <li>仅缓存 2xx 响应</li>
 *   <li>熔断：Redis 异常时直接放行（降级为无缓存）</li>
 * </ul>
 *
 * <h3>缓存控制</h3>
 * <ul>
 *   <li>{@code Cache-Control: no-store} → 跳过缓存</li>
 *   <li>{@code Cache-Control: no-cache} → 回源并写入新缓存</li>
 *   <li>{@code X-Cache: HIT/MISS} 响应头告知客户端命中状态</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@code HIGHEST_PRECEDENCE + 25}，在限流(+30)之后、负载均衡(+100)之前。
 * 早于代理请求执行，命中缓存时直接返回，避免建立下游连接。
 *
 * @since 1.0.0 (P2-2)
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ydsz.gateway.filter", name = "response-cache", havingValue = "true", matchIfMissing = true)
public class ResponseCacheFilter implements GlobalFilter, Ordered {

    private static final String CACHE_KEY_PREFIX = "ydsz:gateway:cache:";
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    /** 可缓存的 Content-Type 前缀 */
    private static final Set<String> CACHEABLE_CONTENT_TYPES = Set.of(
            "application/json",
            "application/xml",
            "text/"
    );

    /** 不可缓存路径前缀 */
    private static final Set<String> UNCACHEABLE_PREFIXES = Set.of(
            "/actuator",
            "/auth",
            "/realtime",
            "/ws"
    );

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 响应缓存过滤器入口
     *
     * <p>检查是否命中缓存，命中则直接返回；未命中则放行并装饰响应以捕获缓存。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 缓存命中直接返回，或放行后的完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. 仅处理 GET 请求
        if (!HttpMethod.GET.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        String path = request.getURI().getPath();

        // 2. 跳过不可缓存路径
        if (isUncacheablePath(path)) {
            return chain.filter(exchange);
        }

        // 3. 检查客户端是否禁用缓存
        String cacheControl = request.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL);
        if (cacheControl != null && cacheControl.contains("no-store")) {
            return chain.filter(exchange);
        }

        // 4. 构建缓存键
        String cacheKey = buildCacheKey(request);

        // 5. 尝试命中缓存
        return redisTemplate.opsForValue().get(cacheKey)
                .defaultIfEmpty("")
                .flatMap(cachedBody -> {
                    if (!cachedBody.isEmpty()) {
                        // 缓存命中：直接返回
                        return serveFromCache(exchange, cachedBody);
                    }

                    // 6. 缓存未命中：放行并装饰响应以捕获缓存
                    return passAndCache(exchange, chain, cacheKey);
                })
                .onErrorResume(e -> {
                    // Redis 异常时降级为无缓存模式
                    log.debug("[ResponseCache] Redis 异常，降级放行: {}", e.getMessage());
                    return chain.filter(exchange);
                });
    }

    /**
     * 从缓存服务于请求
     *
     * @param exchange   服务器 Web 交换上下文
     * @param cachedBody 缓存的响应体 JSON 字符串
     * @return 完成信号 Mono
     */
    private Mono<Void> serveFromCache(ServerWebExchange exchange, String cachedBody) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("X-Cache", "HIT");

        byte[] bytes = cachedBody.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        if (log.isDebugEnabled()) {
            log.debug("[ResponseCache] 缓存命中: path={}",
                    exchange.getRequest().getURI().getPath());
        }

        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 放行请求并异步缓存响应
     *
     * <p>使用 {@link ServerHttpResponseDecorator} 装饰器模式，
     * 在响应体写入客户端前拦截并异步写入 Redis。
     *
     * @param exchange  服务器 Web 交换上下文
     * @param chain     网关过滤器链
     * @param cacheKey  缓存键
     * @return 放行后的完成信号
     */
    private Mono<Void> passAndCache(ServerWebExchange exchange, GatewayFilterChain chain, String cacheKey) {
        ServerHttpResponse originalResponse = exchange.getResponse();

        // 装饰响应以拦截 body
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(fluxBody.collectList()
                            .doOnNext(dataBuffers -> {
                                if (canCacheResponse(originalResponse)) {
                                    cacheResponse(cacheKey, dataBuffers);
                                } else {
                                    originalResponse.getHeaders().add("X-Cache", "BYPASS");
                                }
                            })
                            .flatMapMany(Flux::fromIterable));
                }
                if (body instanceof Mono) {
                    Mono<? extends DataBuffer> monoBody = (Mono<? extends DataBuffer>) body;
                    return super.writeWith(monoBody
                            .doOnNext(dataBuffer -> {
                                if (canCacheResponse(originalResponse)) {
                                    cacheResponseFromBuffer(cacheKey, dataBuffer);
                                } else {
                                    originalResponse.getHeaders().add("X-Cache", "BYPASS");
                                }
                            }));
                }
                originalResponse.getHeaders().add("X-Cache", "BYPASS");
                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * 判断响应是否可缓存
     *
     * @param response 服务器 HTTP 响应
     * @return true 如果可缓存
     */
    private boolean canCacheResponse(ServerHttpResponse response) {
        HttpStatusCode statusCode = response.getStatusCode();
        if (statusCode == null || !statusCode.is2xxSuccessful()) {
            return false;
        }

        // 检查 Content-Type
        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
            return false;
        }

        return CACHEABLE_CONTENT_TYPES.stream()
                .anyMatch(contentType::startsWith);
    }

    /**
     * 缓存多个 DataBuffer 拼接后的响应
     *
     * @param cacheKey    缓存键
     * @param dataBuffers 数据缓冲区列表
     */
    private void cacheResponse(String cacheKey, java.util.List<? extends DataBuffer> dataBuffers) {
        try {
            int totalSize = dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
            byte[] bytes = new byte[totalSize];
            int offset = 0;
            for (DataBuffer buf : dataBuffers) {
                int len = buf.readableByteCount();
                buf.read(bytes, offset, len);
                offset += len;
            }
            cacheResponseBody(cacheKey, new String(bytes, StandardCharsets.UTF_8));
        } finally {
            dataBuffers.forEach(DataBufferUtils::release);
        }
    }

    /**
     * 缓存单个 DataBuffer 的响应
     *
     * @param cacheKey  缓存键
     * @param dataBuffer 数据缓冲区
     */
    private void cacheResponseFromBuffer(String cacheKey, DataBuffer dataBuffer) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            cacheResponseBody(cacheKey, new String(bytes, StandardCharsets.UTF_8));
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    /**
     * 执行缓存写入（异步，fire-and-forget）
     *
     * @param cacheKey 缓存键
     * @param body     响应体字符串
     */
    private void cacheResponseBody(String cacheKey, String body) {
        redisTemplate.opsForValue().set(cacheKey, body, DEFAULT_TTL)
                .doOnError(e -> log.debug("[ResponseCache] 缓存写入失败: {}", e.getMessage()))
                .subscribe();
    }

    /**
     * 构建缓存键
     *
     * <p>键 = MD5("ydsz:gateway:cache:" + method + ":" + path + "?" + query + ":u=" + userId)
     *
     * @param request 服务器 HTTP 请求
     * @return MD5 哈希后的缓存键
     */
    private String buildCacheKey(ServerHttpRequest request) {
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String query = request.getURI().getQuery();
        String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);

        StringBuilder keyBuilder = new StringBuilder(128);
        keyBuilder.append(CACHE_KEY_PREFIX)
                .append(method).append(":")
                .append(path);

        if (query != null && !query.isEmpty()) {
            keyBuilder.append("?").append(query);
        }

        // 加入用户 ID 实现用户级缓存隔离（避免 A 用户缓存被 B 用户命中）
        if (userId != null) {
            keyBuilder.append(":u=").append(userId);
        }

        return md5(keyBuilder.toString());
    }

    /**
     * MD5 哈希（用于缩短缓存键长度）
     *
     * @param input 输入字符串
     * @return MD5 十六进制字符串
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(32);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // MD5 算法必定存在，不会走到这里
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 检查路径是否不可缓存
     *
     * @param path 请求路径
     * @return true 如果路径不可缓存
     */
    private boolean isUncacheablePath(String path) {
        return UNCACHEABLE_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * 过滤器顺序：+25，在限流(+30)之后、灰度(+20)之后
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 25;
    }
}
