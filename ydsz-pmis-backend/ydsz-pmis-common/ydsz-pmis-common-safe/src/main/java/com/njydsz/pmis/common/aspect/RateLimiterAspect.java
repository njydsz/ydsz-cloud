package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Objects;

/**
 * 限流 AOP
 *
 * <p>基于 Redis + Lua 脚本实现滑动窗口限流，解决固定窗口算法的"临界突刺"问题。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class RateLimiterAspect {

    private static final String USER_ID_HEADER = "X-User-Id";

    /** Redis 操作模板 */
    private final StringRedisTemplate redisTemplate;

    private static final String SLIDING_WINDOW_LUA =
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])\n" +
            "local count = redis.call('ZCARD', KEYS[1])\n" +
            "if count < tonumber(ARGV[3]) then\n" +
            "  redis.call('ZADD', KEYS[1], ARGV[1], ARGV[1])\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[4])\n" +
            "  return 1\n" +
            "else\n" +
            "  return 0\n" +
            "end";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(SLIDING_WINDOW_LUA, Long.class);

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = buildKey(rateLimit, pjp);
        int qps = rateLimit.qps();
        int window = rateLimit.windowSeconds();

        Boolean allowed = checkRateLimit(key, qps, window);
        if (Boolean.FALSE.equals(allowed)) {
            log.warn("[RateLimit] 触发限流 key={} qps={} window={}s", key, qps, window);
            throw new BizException(rateLimit.message());
        }

        return pjp.proceed();
    }

    private String buildKey(RateLimit rateLimit, ProceedingJoinPoint pjp) {
        String prefix = "pmis:ratelimit:";
        String bizKey = rateLimit.key();
        if (bizKey == null || bizKey.isEmpty()) {
            String className = pjp.getSignature().getDeclaringType().getSimpleName();
            String methodName = pjp.getSignature().getName();
            bizKey = className + ":" + methodName;
        }

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String dimension;
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String userId = request.getHeader(USER_ID_HEADER);
            if (userId != null && !userId.isEmpty()) {
                dimension = userId;
            } else {
                dimension = getIp(request);
            }
        } else {
            dimension = "anonymous";
        }

        return prefix + bizKey + ":" + dimension;
    }

    private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > -1 ? ip.substring(0, idx) : ip;
        }
        return Objects.requireNonNullElse(request.getRemoteAddr(), "unknown");
    }

    private Boolean checkRateLimit(String key, int qps, int window) {
        long now = System.currentTimeMillis();
        long windowStart = now - (window * 1000L);
        Long result = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(now),
                String.valueOf(windowStart),
                String.valueOf(qps),
                String.valueOf(window)
        );
        return result != null && result.equals(1L);
    }
}
