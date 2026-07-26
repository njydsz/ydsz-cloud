package com.njydsz.common.auth.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.security.TokenBlacklistBloomFilter;
import com.njydsz.common.auth.util.AuthDigestUtils;

import reactor.core.publisher.Mono;

/**
 * Reactive 版 Token 黑名单服务（WebFlux 网关专用）
 *
 * <p>与 {@link TokenBlacklistService} 功能对等，但基于 {@link ReactiveStringRedisTemplate}
 * 实现非阻塞 Redis 查询，适用于 Spring Cloud Gateway 等 reactive 栈场景。
 *
 * <h3>与同步版本的区别</h3>
 * <ul>
 *   <li>Redis 操作使用 {@link ReactiveStringRedisTemplate}（非阻塞）</li>
 *   <li>Bloom Filter 前置过滤逻辑完全一致（内存操作，无需 reactive）</li>
 *   <li>key 前缀与同步版本一致：{@code auth:token:blacklist:} + SHA-256 摘要</li>
 *   <li>返回 {@code Mono<Boolean>} 适配 WebFlux 过滤器链</li>
 * </ul>
 *
 * <h3>Bloom Filter 优化</h3>
 * <p>当 Bloom Filter 返回 false 时，Token 一定不在黑名单中，直接返回 {@code Mono.just(false)}，
 * 避免 90%+ 的 Redis 查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ReactiveTokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(ReactiveTokenBlacklistService.class);

    private static final String BLACKLIST_KEY_PREFIX = "auth:token:blacklist:";

    private final ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider;
    private final AuthProperties authProperties;
    private final TokenBlacklistBloomFilter bloomFilter;

    /**
     * 构造 Reactive Token 黑名单服务
     *
     * @param redisTemplateProvider Reactive Redis 模板（可选，未配置时降级为放行）
     * @param authProperties       认证配置
     */
    public ReactiveTokenBlacklistService(
            ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
            AuthProperties authProperties) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.authProperties = authProperties;
        this.bloomFilter = new TokenBlacklistBloomFilter(1_000_000);
        log.info("[ReactiveTokenBlacklist] 初始化完成, blacklist.enabled={}",
                authProperties.getBlacklist().isEnabled());
    }

    /**
     * 检查 Token 是否在黑名单中（非阻塞）
     *
     * <p>流程：
     * <ol>
     *   <li>如果黑名单功能未启用，直接返回 false</li>
     *   <li>Bloom Filter 前置过滤：返回 false 时一定不在黑名单</li>
     *   <li>Bloom Filter 返回 true 时查 Redis 确认（可能有误判）</li>
     *   <li>Redis 不可用时降级为放行（false），避免阻断流量</li>
     * </ol>
     *
     * @param token JWT Token
     * @return Mono&lt;true&gt; 表示在黑名单中，Mono&lt;false&gt; 表示不在
     */
    public Mono<Boolean> isBlacklisted(String token) {
        if (!authProperties.getBlacklist().isEnabled()) {
            return Mono.just(false);
        }
        if (token == null || token.isBlank()) {
            return Mono.just(false);
        }

        // Bloom Filter 前置过滤
        if (!bloomFilter.mightBeBlacklisted(token)) {
            return Mono.just(false);
        }

        // Bloom Filter 可能命中，查 Redis 确认
        ReactiveStringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.warn("[ReactiveTokenBlacklist] Redis 未配置，降级为放行");
            return Mono.just(false);
        }

        String key = buildBlacklistKey(token);
        return redisTemplate.hasKey(key)
                .onErrorResume(e -> {
                    log.warn("[ReactiveTokenBlacklist] Redis 查询异常，降级为放行: {}", e.getMessage());
                    return Mono.just(false);
                })
                .defaultIfEmpty(false);
    }

    /**
     * 将 Token 加入黑名单（非阻塞，用于网关侧主动加入）
     *
     * @param token JWT Token
     * @return 完成信号 Mono
     */
    public Mono<Void> addToBlacklist(String token) {
        if (!authProperties.getBlacklist().isEnabled()) {
            return Mono.empty();
        }
        if (token == null || token.isBlank()) {
            return Mono.empty();
        }

        bloomFilter.addToBlacklist(token);

        ReactiveStringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return Mono.empty();
        }

        String key = buildBlacklistKey(token);
        long expire = authProperties.getBlacklist().getExpireSeconds();
        return redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(expire))
                .doOnSuccess(v -> log.info("[ReactiveTokenBlacklist] Token 已加入黑名单, expire={}s", expire))
                .then();
    }

    /**
     * 将 Token 的 SHA-256 摘要作为 Redis key
     */
    private String buildBlacklistKey(String token) {
        return BLACKLIST_KEY_PREFIX + AuthDigestUtils.sha256Hex(token);
    }
}
