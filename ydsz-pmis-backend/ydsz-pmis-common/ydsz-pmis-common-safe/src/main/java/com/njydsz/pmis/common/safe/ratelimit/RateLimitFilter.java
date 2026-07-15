package com.njydsz.pmis.common.safe.ratelimit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.redis.service.RedisService;
import com.njydsz.pmis.common.safe.alert.SafeAlertProperties;
import com.njydsz.pmis.common.safe.alert.SecurityEvent;
import com.njydsz.pmis.common.safe.alert.SecurityEventPublisher;
import com.njydsz.pmis.common.safe.alert.SecurityEventType;
import com.njydsz.pmis.common.safe.util.ClientIpResolver;
import com.njydsz.pmis.common.json.Json;
import com.njydsz.pmis.common.util.url.UrlPathUtils;

/**
 * 基于 Redis 令牌桶的全局限流 Filter。
 *
 * <p>支持按 IP / 用户 / 全局三种维度进行限流。
 * 使用 Redis + Lua 实现滑动窗口限流，保证分布式环境下的精确限流。
 * 继承 {@link OncePerRequestFilter}，确保每次请求只执行一次。
 *
 * <p><b>限流维度：</b>
 * <ul>
 *   <li>IP - 按客户端 IP 限流（默认）</li>
 *   <li>USER - 按登录用户限流，从请求头 X-User-Id 获取</li>
 *   <li>GLOBAL - 全局共享限流</li>
 * </ul>
 *
 * <p><b>实现原理：</b>
 * 使用 Redis ZSet 实现滑动窗口算法，将每个请求的时间戳作为 score 存入 ZSet，
 * 通过统计窗口内的请求数判断是否超限。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final List<String> DEFAULT_EXCLUDES = new ArrayList<>();

    static {
        DEFAULT_EXCLUDES.add("/error");
        DEFAULT_EXCLUDES.add("/favicon.ico");
        DEFAULT_EXCLUDES.add("/actuator/**");
    }

    private static final String LUA_RATE_LIMIT_SCRIPT =
            "local key = KEYS[1]\n" +
            "local window = tonumber(ARGV[1])\n" +
            "local limit = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local windowStart = now - window\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "if count < limit then\n" +
            "    redis.call('ZADD', key, now, now .. '-' .. math.random(100000))\n" +
            "    redis.call('EXPIRE', key, window + 1)\n" +
            "    return 1\n" +
            "end\n" +
            "return 0";

    private final RateLimitProperties properties;
    private final RedisService redisService;
    private final List<String> excludes;
    private final SecurityEventPublisher eventPublisher;
    private final SafeAlertProperties alertProperties;
    private final LocalRateLimiter localRateLimiter;

    /** JSON 序列化器，用于生成限流响应体 */
    // Json as JSON engine

    public RateLimitFilter(RateLimitProperties properties,
                           RedisService redisService,
                           SecurityEventPublisher eventPublisher,
                           SafeAlertProperties alertProperties) {
        this.properties = properties;
        this.redisService = redisService;
        this.excludes = properties.getExcludes() == null || properties.getExcludes().isEmpty()
                ? new ArrayList<>(DEFAULT_EXCLUDES)
                : new ArrayList<>(properties.getExcludes());
        this.eventPublisher = eventPublisher;
        this.alertProperties = alertProperties;
        this.localRateLimiter = new LocalRateLimiter(properties.getLimitPerSecond(), 1);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain)
            throws IOException, ServletException {
        if (isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rateLimitKey = resolveRateLimitKey(request);
        if (rateLimitKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long windowSeconds = 1;
        int limit = properties.getLimitPerSecond();
        long now = System.currentTimeMillis();

        try {
            Long result = redisService.executeScript(
                    LUA_RATE_LIMIT_SCRIPT,
                    List.of(rateLimitKey),
                    Long.class,
                    windowSeconds, limit, now
            );
            boolean allowed = result != null && result == 1L;

            if (!allowed) {
                log.warn("【安全模块】请求被限流 | key={}, uri={}, ip={}", rateLimitKey, request.getRequestURI(), ClientIpResolver.getClientIp(request));
                publishRateLimitEvent(request);
                writeRateLimitResponse(response);
                return;
            }
        } catch (Exception e) {
            // Redis 异常时降级到本地限流（fail-safe），避免 Redis 不可用时完全放行
            log.warn("【安全模块】Redis 限流不可用，降级到本地限流 | key={}, uri={}, error={}",
                    rateLimitKey, request.getRequestURI(), e.getMessage());
            if (!localRateLimiter.tryAcquire()) {
                log.warn("【安全模块】本地限流触发 | key={}, uri={}", rateLimitKey, request.getRequestURI());
                publishRateLimitEvent(request);
                writeRateLimitResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 写入限流响应（JSON 格式 BaseResponse，HTTP 429）
     */
    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        BaseResponse<Void> body = BaseResponse.error(
                UnifiedExceptionCode.RATE_LIMIT.getCode(), properties.getMessage());
        response.getWriter().write(Json.toJson(body));
    }

    /**
     * 根据配置的维度解析限流 Key。
     *
     * <p>为减少碰撞，IP 维度组合使用 IP + 用户ID（如有）+ URI；
     * USER 维度组合使用 用户ID + URI。
     */
    private String resolveRateLimitKey(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String clientIp = ClientIpResolver.getClientIp(request);
        String userId = request.getHeader("X-User-Id");

        RateLimitProperties.Dimension dimension = properties.getDimension();
        switch (dimension) {
            case USER:
                if (StringUtils.hasText(userId)) {
                    return properties.getUserKey() + userId + ":" + uri;
                }
                return properties.getIpKey() + clientIp + ":" + uri;
            case GLOBAL:
                return properties.getGlobalKey();
            case IP:
            default:
                // 组合 IP + 用户ID（如有）+ URI，减少 NAT 出口共享 IP 导致的误限
                StringBuilder key = new StringBuilder(properties.getIpKey()).append(clientIp);
                if (StringUtils.hasText(userId)) {
                    key.append(":").append(userId);
                }
                key.append(":").append(uri);
                return key.toString();
        }
    }


    /**
     * 判断请求路径是否需要排除限流。
     */
    private boolean isExcluded(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }

    /**
     * 发布限流安全事件。
     */
    private void publishRateLimitEvent(HttpServletRequest request) {
        if (eventPublisher == null || alertProperties == null || !alertProperties.isEnabled()) {
            return;
        }
        SecurityEvent event = new SecurityEvent(
                SecurityEventType.RATE_LIMIT_TRIGGERED,
                request.getRequestURI(),
                ClientIpResolver.getClientIp(request),
                request.getHeader("User-Agent"),
                "Rate limit exceeded for key: " + resolveRateLimitKey(request),
                SecurityEvent.Severity.MEDIUM
        );
        eventPublisher.publish(event);
    }
}
