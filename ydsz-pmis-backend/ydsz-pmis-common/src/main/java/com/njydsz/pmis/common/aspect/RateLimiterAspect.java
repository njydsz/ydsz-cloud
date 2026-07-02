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

    /** Redis 操作模板 */
    private final StringRedisTemplate redisTemplate;

    /**
     * 环绕增强：基于 Redis 滑动窗口校验限流，超限抛出 RATE_LIMIT 异常
     *
     * @param pjp       连接点
     * @param rateLimit 限流注解
     * @return 目标方法返回值
     * @throws Throwable    目标方法抛出的异常
     * @throws BizException 触发限流时抛出
     */
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

    /**
     * 构造限流 key。
     *
     * <p>组成：前缀 {@code pmis:ratelimit:{bizKey}} + 维度（登录用户 userId 或匿名 IP）。
     * 无业务 key 时回退到 "default"。</p>
     *
     * @param rateLimit 限流注解
     * @return 完整 Redis key
     */
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

    /**
     * 兜底业务 key：当注解未指定 key 时使用固定前缀。
     *
     * @return "default"
     */
    private String pjpClassName() {
        // 简单使用固定前缀，实际可拼接方法签名
        return "default";
    }

    /**
     * 解析客户端真实 IP。
     *
     * <p>优先取 X-Forwarded-For 第一个值，否则回退到 remoteAddr，兜底 "unknown"。</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP 字符串
     */
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
     *
     * @param key   Redis 计数 key
     * @param qps   允许的请求数
     * @param window 时间窗口（秒）
     * @return true 表示放行；false 表示触发限流
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
