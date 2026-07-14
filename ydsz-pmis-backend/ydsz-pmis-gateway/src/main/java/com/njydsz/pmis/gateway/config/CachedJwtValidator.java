package com.njydsz.pmis.gateway.config;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.auth.model.UserInfo;
import com.njydsz.pmis.common.auth.token.TokenService;
import com.njydsz.pmis.common.cache.YdszCache;
import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheType;

import lombok.extern.slf4j.Slf4j;

/**
 * JWT 校验结果缓存（P1-7 + P2-12 增强）
 *
 * <p>使用 ydsz-pmis-common-cache 本地缓存 JWT 解析结果，避免每个请求重复执行
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

    /** 本地缓存实例 */
    private final Cache<String, Optional<UserInfo>> claimsCache;

    /** Token 服务 */
    private final TokenService tokenService;

    /** P2-12: 网关指标组件（可选，用于记录 JWT 校验耗时） */
    private GatewayMetrics gatewayMetrics;

    /**
     * 构造 JWT 缓存校验器
     *
     * @param tokenService Token 服务
     * @param gatewayMetrics 网关指标组件（可选）
     */
    public CachedJwtValidator(TokenService tokenService, GatewayMetrics gatewayMetrics) {
        this.tokenService = tokenService;
        this.gatewayMetrics = gatewayMetrics;
        this.claimsCache = YdszCache.<String, Optional<UserInfo>>newBuilder()
                .type(CacheType.TTL)
                .expireAfterWrite(CACHE_TTL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(CACHE_MAX_SIZE)
                .recordStats()
                .build();
        log.info("[JwtCache] JWT 校验缓存初始化完成, TTL={}s, maxSize={}", CACHE_TTL_SECONDS, CACHE_MAX_SIZE);
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
     * P2-12: 失效单个 Token（黑名单加入后立即清除缓存）
     *
     * <p>当 Token 被加入 Redis 黑名单时，调用此方法立即清除 Caffeine 缓存，
     * 无需等待 TTL 过期。
     *
     * <p>调用时机：在 {@link com.njydsz.pmis.gateway.filter.AuthGlobalFilter} 中，
     * 当检测到 Token 在黑名单中时调用。
     *
     * @param jwt 需要失效的 JWT Token
     */
    public void invalidate(String jwt) {
        if (jwt != null && !jwt.isBlank()) {
            claimsCache.invalidate(jwt);
            log.debug("[JwtCache] Token 已从缓存中移除 jwt={}", maskToken(jwt));
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
     * Token 脱敏（日志中不暴露完整内容）
     *
     * @param jwt JWT Token
     * @return 脱敏后的字符串
     */
    private String maskToken(String jwt) {
        if (jwt == null || jwt.length() <= 10) {
            return "***";
        }
        return jwt.substring(0, 5) + "***" + jwt.substring(jwt.length() - 5);
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
     */
    public void invalidateAll() {
        claimsCache.invalidateAll();
        log.info("[JwtCache] 缓存已手动清除");
    }
}
