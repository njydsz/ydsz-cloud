package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 限流 AOP
 *
 * <p>基于 Redis 滑动窗口实现，简单高效。
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

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = buildKey(rateLimit);
        int qps = rateLimit.qps();
        int window = rateLimit.windowSeconds();

        Boolean allowed = checkRateLimit(key, qps, window);
        if (Boolean.FALSE.equals(allowed)) {
            log.warn("[RateLimit] 触发限流 key={} qps={}", key, qps);
            throw new BizException(BizErrorCode.RATE_LIMIT, rateLimit.message());
        }

        return pjp.proceed();
    }

    private String buildKey(RateLimit rateLimit) {
        String prefix = "pmis:ratelimit:";
        String bizKey = rateLimit.key();
        if (bizKey == null || bizKey.isEmpty()) {
            bizKey = pjpClassName();
        }

        // 维度：按 IP 或用户
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String dimension;
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String token = request.getHeader("Authorization");
            if (token != null && !token.isEmpty()) {
                try {
                    dimension = String.valueOf(SecurityContext.getUserId());
                } catch (Exception e) {
                    dimension = getIp(request);
                }
            } else {
                dimension = getIp(request);
            }
        } else {
            dimension = "anonymous";
        }

        return prefix + bizKey + ":" + dimension;
    }

    private String pjpClassName() {
        // 简单使用固定前缀，实际可拼接方法签名
        return "default";
    }

    private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > -1 ? ip.substring(0, idx) : ip;
        }
        return Objects.requireNonNullElse(request.getRemoteAddr(), "unknown");
    }

    /**
     * 滑动窗口限流（基于 INCR + EXPIRE）
     */
    private Boolean checkRateLimit(String key, int qps, int window) {
        String countKey = key + ":" + (System.currentTimeMillis() / 1000 / window);
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(countKey, window, TimeUnit.SECONDS);
        }
        return count == null || count <= qps;
    }
}
