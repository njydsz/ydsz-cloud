package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 限流切面 — 基于 Redis 滑动窗口实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Aspect
@Component
public class RateLimiterAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterAspect.class);
    private static final String USER_ID_HEADER = "X-User-Id";

    private final StringRedisTemplate redisTemplate;

    public RateLimiterAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = buildKey(rateLimit, pjp);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(rateLimit.windowSeconds()));
        }
        if (count != null && count > rateLimit.qps()) {
            log.warn("限流拦截: key={}, count={}, qps={}", key, count, rateLimit.qps());
            throw new BizException(rateLimit.message());
        }
        return pjp.proceed();
    }

    private String buildKey(RateLimit rateLimit, ProceedingJoinPoint pjp) {
        StringBuilder sb = new StringBuilder("rate_limit:");
        sb.append(rateLimit.key().isEmpty() ? pjp.getSignature().toShortString() : rateLimit.key());
        sb.append(":").append(getUserId());
        return sb.toString();
    }

    private String getUserId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String userId = req.getHeader(USER_ID_HEADER);
                return userId != null ? userId : "anonymous";
            }
        } catch (Exception ignored) {
            // 非 Web 上下文
        }
        return "anonymous";
    }
}
