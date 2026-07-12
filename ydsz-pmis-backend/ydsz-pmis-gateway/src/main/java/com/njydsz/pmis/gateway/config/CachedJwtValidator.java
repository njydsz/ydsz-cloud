package com.njydsz.pmis.gateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.njydsz.pmis.common.auth.model.UserInfo;
import com.njydsz.pmis.common.auth.token.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * JWT 校验结果缓存（P1-7）
 *
 * <p>使用 Caffeine 本地缓存 JWT 解析结果，避免每个请求重复执行
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
 * <h3>性能预期</h3>
 * <p>假设单实例 QPS=2000，90% 请求在 5 秒窗口内复用缓存，
 * JWT 解析次数从 2000/s 降至 ~200/s，CPU 开销减少 90%。
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
@Slf4j
@Component
public class CachedJwtValidator {

    /** 缓存 TTL（秒） */
    private static final long CACHE_TTL_SECONDS = 5;

    /** 缓存最大容量 */
    private static final long CACHE_MAX_SIZE = 10_000;

    /** Caffeine 缓存实例 */
    private final Cache<String, Optional<UserInfo>> claimsCache;

    /** Token 服务 */
    private final TokenService tokenService;

    /**
     * 构造 JWT 缓存校验器
     *
     * @param tokenService Token 服务
     */
    public CachedJwtValidator(TokenService tokenService) {
        this.tokenService = tokenService;
        this.claimsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(CACHE_TTL_SECONDS))
                .maximumSize(CACHE_MAX_SIZE)
                .recordStats()
                .build();
        log.info("[JwtCache] JWT 校验缓存初始化完成, TTL={}s, maxSize={}", CACHE_TTL_SECONDS, CACHE_MAX_SIZE);
    }

    /**
     * 校验并解析 JWT Token（带缓存）
     *
     * <p>优先从 Caffeine 缓存读取解析结果；缓存未命中时执行实际解析并写入缓存。
     *
     * @param jwt JWT Token 字符串
     * @return UserInfo 解析结果，Token 无效时返回 null
     */
    public UserInfo validateAndParse(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return null;
        }

        Optional<UserInfo> cached = claimsCache.getIfPresent(jwt);
        if (cached != null) {
            return cached.orElse(null);
        }

        // 缓存未命中，执行实际校验
        UserInfo userInfo = null;
        if (tokenService.validateAccessToken(jwt)) {
            try {
                userInfo = tokenService.parseAccessToken(jwt);
            } catch (Exception e) {
                log.warn("[JwtCache] 解析 JWT 失败: {}", e.getMessage());
            }
        }

        // 写入缓存（null 也缓存，避免无效 Token 重复解析）
        claimsCache.put(jwt, Optional.ofNullable(userInfo));
        return userInfo;
    }

    /**
     * 获取缓存统计信息（供监控使用）
     *
     * @return Caffeine 缓存统计快照的字符串表示
     */
    public String getCacheStats() {
        return claimsCache.stats().toString();
    }

    /**
     * 手动清除缓存（供 Nacos 配置刷新时调用）
     */
    public void invalidateAll() {
        claimsCache.invalidateAll();
        log.info("[JwtCache] 缓存已手动清除");
    }
}
