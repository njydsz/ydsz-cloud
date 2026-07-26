package com.njydsz.gateway.config;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.safe.sensitive.SensitiveUtil;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * JWT 校验结果缓存（P1-7 + P2-12 增强 + P1 多实例广播）
 *
 * <p>使用 ydsz-common-cache 本地缓存 JWT 解析结果，避免每个请求重复执行
 * {@code tokenService.parseAccessToken(token)} 的 CPU 开销。
 *
 * <h3>缓存策略</h3>
 * <ul>
 *   <li>缓存键: JWT Token 字符串</li>
 *   <li>缓存值: UserInfo 解析结果（或 INVALID 标记）</li>
 *   <li>TTL: 5 秒（平衡性能与黑名单生效延迟）</li>
 *   <li>最大容量: 10,000 条（防止内存溢出）</li>
 * </ul>
 *
 * <h3>黑名单兼容</h3>
 * <p>5 秒 TTL 意味着 Token 被加入 Redis 黑名单后，最长 5 秒内仍可能通过缓存命中。
 * 这是可接受的权衡——大厂网关通常也采用 3-10 秒的 JWT 缓存窗口。
 *
 * <h3>P2-12 增强项</h3>
 * <ul>
 *   <li>{@code invalidate(token)}: 单 Token 失效方法（黑名单加入后立即清除缓存）</li>
 *   <li>{@code recordMetrics()}: JWT 校验耗时指标集成（GatewayMetrics）</li>
 * </ul>
 *
 * <h3>P1: 多实例缓存广播（Redis Pub/Sub）</h3>
 * <p>当网关多实例部署时，单实例调用 {@link #invalidate(String)} 仅清除本实例的 Caffeine 缓存，
 * 其他实例最长 5 秒 TTL 期间仍可能命中旧缓存（导致黑名单生效延迟）。
 *
 * <p>本版本引入 Redis Pub/Sub 失效广播：
 * <ol>
 *   <li>调用 {@link #invalidate(String)} 时，本地清除 + 发布失效事件到 Redis 频道</li>
 *   <li>所有网关实例订阅该频道，收到事件后清除本地缓存</li>
 *   <li>广播消息为 Token 字符串（Redis 内网通信，且订阅者仅本服务实例）</li>
 *   <li>回环消息幂等：本实例发布的消息也会被自己订阅，重复 invalidate 是无副作用操作</li>
 * </ol>
 *
 * <h3>性能预期</h3>
 * <p>假设单实例 QPS=2000，90% 请求在 5 秒窗口内复用缓存，
 * JWT 解析次数从 2000/s 降至 ~200/s，CPU 开销减少 90%。
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class CachedJwtValidator {

    /** 缓存 TTL（秒） */
    private static final long CACHE_TTL_SECONDS = 5;

    /** 缓存最大容量 */
    private static final long CACHE_MAX_SIZE = 10_000;

    /** P1: Redis Pub/Sub 失效广播频道 */
    private static final String INVALIDATION_CHANNEL = "ydsz:gateway:jwt-cache:invalidate";

    /** 本地缓存实例 */
    private final Cache<String, Optional<UserInfo>> claimsCache;

    /** Token 服务 */
    private final TokenService tokenService;

    /** P2-12: 网关指标组件（可选，用于记录 JWT 校验耗时） */
    private final GatewayMetrics gatewayMetrics;

    /** P1: Reactive Redis 模板（可选，未配置时降级为单实例模式） */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** P1: Redis 消息监听容器（可选） */
    private final ReactiveRedisMessageListenerContainer messageListenerContainer;

    /** P1: 订阅句柄（用于 @PreDestroy 释放） */
    private Disposable subscription;

    /**
     * 构造 JWT 缓存校验器
     *
     * @param tokenService Token 服务
     * @param gatewayMetrics 网关指标组件（可选）
     * @param redisTemplateProvider Reactive Redis 模板提供者（可选，未配置时降级为单实例模式）
     * @param listenerContainerProvider Redis 监听容器提供者（可选）
     */
    public CachedJwtValidator(TokenService tokenService,
                                GatewayMetrics gatewayMetrics,
                                ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
                                ObjectProvider<ReactiveRedisMessageListenerContainer> listenerContainerProvider) {
        this.tokenService = tokenService;
        this.gatewayMetrics = gatewayMetrics;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.messageListenerContainer = listenerContainerProvider.getIfAvailable();
        this.claimsCache = YdszCache.<String, Optional<UserInfo>>newBuilder()
                .type(CacheType.STRIPED)
                .expireAfterWrite(CACHE_TTL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(CACHE_MAX_SIZE)
                .recordStats()
                .build();
        log.info("[JwtCache] JWT 校验缓存初始化完成, TTL={}s, maxSize={}, redisBroadcast={}",
                CACHE_TTL_SECONDS, CACHE_MAX_SIZE, redisTemplate != null);
    }

    /**
     * P1: 启动后订阅 Redis Pub/Sub 失效广播频道
     *
     * <p>当 Redis 不可用时降级为单实例模式（仅本实例 invalidate 生效，其他实例通过 TTL 自然过期）。
     */
    @PostConstruct
    public void subscribeInvalidationChannel() {
        if (redisTemplate == null || messageListenerContainer == null) {
            log.warn("[JwtCache] Redis 未配置，降级为单实例模式（多实例部署时黑名单生效延迟最长 {}s）", CACHE_TTL_SECONDS);
            return;
        }
        try {
            subscription = messageListenerContainer
                    .receive(ChannelTopic.of(INVALIDATION_CHANNEL))
                    .doOnNext(message -> {
                        String token = message.getMessage();
                        // 收到广播后本地清除（幂等操作，回环消息无副作用）
                        claimsCache.invalidate(token);
                        log.debug("[JwtCache] 收到广播失效事件 token={}", maskToken(token));
                    })
                    .onErrorContinue((e, o) -> log.warn("[JwtCache] Redis 广播订阅异常: {}", e.getMessage()))
                    .subscribe();
            log.info("[JwtCache] 已订阅失效广播频道 channel={}", INVALIDATION_CHANNEL);
        } catch (Exception e) {
            log.warn("[JwtCache] 订阅 Redis 失效广播失败，降级为单实例模式: {}", e.getMessage());
        }
    }

    /**
     * P1: 关闭时释放订阅
     */
    @PreDestroy
    public void unsubscribe() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("[JwtCache] 已释放 Redis 失效广播订阅");
        }
    }

    /**
     * 校验并解析 JWT Token（带缓存）
     *
     * <p>优先从 Caffeine 缓存读取解析结果；缓存未命中时执行实际解析并写入缓存。
     * <p>P2-12: 同时记录 JWT 校验耗时到 GatewayMetrics（如果可用）。
     *
     * @param jwt JWT Token 字符串
     * @return UserInfo 解析结果，Token 无效时返回 null
     */
    public UserInfo validateAndParse(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return null;
        }

        long startTime = System.currentTimeMillis();
        Optional<UserInfo> cached = claimsCache.getIfPresent(jwt);
        long duration = System.currentTimeMillis() - startTime;

        boolean isCached = cached != null;
        if (isCached) {
            recordMetrics(duration, true);
            return cached.orElse(null);
        }

        // 缓存未命中，执行实际校验
        startTime = System.currentTimeMillis();
        UserInfo userInfo = null;
        if (tokenService.validateAccessToken(jwt)) {
            try {
                userInfo = tokenService.parseAccessToken(jwt);
            } catch (Exception e) {
                log.warn("[JwtCache] 解析 JWT 失败: {}", e.getMessage());
            }
        }
        duration = System.currentTimeMillis() - startTime;

        // 写入缓存（null 也缓存，避免无效 Token 重复解析）
        claimsCache.put(jwt, Optional.ofNullable(userInfo));
        recordMetrics(duration, false);
        return userInfo;
    }

    /**
     * P2-12 + P1: 失效单个 Token（黑名单加入后立即清除缓存）
     *
     * <p>当 Token 被加入 Redis 黑名单时，调用此方法立即清除 Caffeine 缓存，无需等待 TTL 过期。
     *
     * <p>P1: 多实例部署时，本方法同时：
     * <ol>
     *   <li>清除本实例的 Caffeine 缓存</li>
     *   <li>发布失效事件到 Redis Pub/Sub 频道</li>
     *   <li>其他实例订阅到事件后清除各自的 Caffeine 缓存</li>
     * </ol>
     *
     * <p>调用时机：在 {@link com.njydsz.gateway.filter.AuthGlobalFilter} 中，
     * 当检测到 Token 在黑名单中时调用。
     *
     * @param jwt 需要失效的 JWT Token
     */
    public void invalidate(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return;
        }
        // 1. 本地立即清除
        claimsCache.invalidate(jwt);
        log.debug("[JwtCache] Token 已从本地缓存移除 jwt={}", maskToken(jwt));

        // 2. P1: 广播到其他实例（异步，不阻塞主流程）
        broadcastInvalidation(jwt);
    }

    /**
     * P1: 发布失效事件到 Redis Pub/Sub 频道
     *
     * <p>使用 reactive 模式异步发布，发布失败不影响主流程（最坏情况下其他实例通过 TTL 自然过期）。
     *
     * @param jwt 需要失效的 JWT Token
     */
    private void broadcastInvalidation(String jwt) {
        if (redisTemplate == null) {
            // Redis 未配置，跳过广播（单实例模式）
            return;
        }
        try {
            redisTemplate.convertAndSend(INVALIDATION_CHANNEL, jwt)
                    .onErrorResume(e -> {
                        log.warn("[JwtCache] Redis 广播失效失败（其他实例将通过 TTL 过期）: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        } catch (Exception e) {
            log.warn("[JwtCache] Redis 广播发布异常: {}", e.getMessage());
        }
    }

    /**
     * P2-12: 记录 JWT 校验耗时指标
     *
     * @param durationMs 耗时（毫秒）
     * @param cached 是否命中缓存
     */
    private void recordMetrics(long durationMs, boolean cached) {
        if (gatewayMetrics != null) {
            try {
                gatewayMetrics.recordJwtValidationDuration(durationMs, cached);
            } catch (Exception e) {
                // 指标记录失败不影响主流程
                log.debug("[JwtCache] 记录指标失败: {}", e.getMessage());
            }
        }
    }

    /**
     * Token 脱敏（P0-5：复用 ydsz-common-safe 的 SensitiveUtil 统一脱敏策略）
     *
     * @param jwt JWT Token
     * @return 脱敏后的字符串
     */
    private String maskToken(String jwt) {
        return SensitiveUtil.defaultDesensitize(jwt, '*');
    }

    /**
     * 获取缓存统计信息（供监控使用）
     *
     * @return Caffeine 缓存统计快照的字符串表示
     */
    public String getCacheStats() {
        return claimsCache.getStats().toString();
    }

    /**
     * 手动清除缓存（供 Nacos 配置刷新时调用）
     *
     * <p>P1: 多实例部署时仅清除本实例，其他实例通过 TTL 自然过期。
     * 如需全量清除，可调用 {@link #broadcastInvalidateAll()} 广播清除事件。
     */
    public void invalidateAll() {
        claimsCache.invalidateAll();
        log.info("[JwtCache] 缓存已手动清除");
    }
}
