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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Objects;

/**
 * 限流 AOP
 *
 * <p>基于 Redis + Lua 脚本实现真正的滑动窗口限流，解决固定窗口算法的"临界突刺"问题。
 *
 * <p>滑动窗口原理：使用 Redis Sorted Set，以时间戳为 score，每次请求：
 * <ol>
 *   <li>移除窗口外的旧记录（ZREMRANGEBYSCORE）</li>
 *   <li>统计当前窗口内请求数（ZCARD）</li>
 *   <li>若未超限，添加当前请求记录（ZADD）并设置过期时间</li>
 * </ol>
 * 整个流程通过 Lua 脚本保证原子性。
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
     * 滑动窗口限流 Lua 脚本。
     *
     * 参数（KEYS[1]=限流 key，ARGV[1]=当前时间戳ms，ARGV[2]=窗口起始时间戳ms，ARGV[3]=最大请求数，ARGV[4]=窗口秒数）：
     * 1. ZREMRANGEBYSCORE 移除窗口外的旧记录
     * 2. ZCARD 统计当前窗口内请求数
     * 3. 若未超限，ZADD 添加当前请求并 EXPIRE 设置过期
     * 4. 返回 1（放行）或 0（限流）
     */
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

    /** 预编译 Lua 脚本（返回 Long） */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(SLIDING_WINDOW_LUA, Long.class);

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
        String key = buildKey(rateLimit, pjp);
        int qps = rateLimit.qps();
        int window = rateLimit.windowSeconds();

        Boolean allowed = checkRateLimit(key, qps, window);
        if (Boolean.FALSE.equals(allowed)) {
            log.warn("[RateLimit] 触发限流 key={} qps={} window={}s", key, qps, window);
            throw new BizException(BizErrorCode.RATE_LIMIT, rateLimit.message());
        }

        return pjp.proceed();
    }

    /**
     * 构造限流 key。
     *
     * <p>组成：前缀 {@code pmis:ratelimit:{bizKey}} + 维度（登录用户 userId 或匿名 IP）。
     * 无业务 key 时回退到方法签名。</p>
     *
     * @param rateLimit 限流注解
     * @param pjp       连接点（用于提取方法签名）
     * @return 完整 Redis key
     */
    private String buildKey(RateLimit rateLimit, ProceedingJoinPoint pjp) {
        String prefix = "pmis:ratelimit:";
        String bizKey = rateLimit.key();
        if (bizKey == null || bizKey.isEmpty()) {
            // 使用类名+方法名作为 bizKey，避免不同方法限流 key 冲突
            String className = pjp.getSignature().getDeclaringType().getSimpleName();
            String methodName = pjp.getSignature().getName();
            bizKey = className + ":" + methodName;
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
     * 滑动窗口限流（基于 Redis Sorted Set + Lua 脚本）
     *
     * <p>使用 ZREMRANGEBYSCORE + ZCARD + ZADD 的原子组合，实现真正的滑动窗口：
     * <ul>
     *   <li>不存在固定窗口算法的"临界突刺"问题</li>
     *   <li>Lua 脚本保证 ZREMRANGEBYSCORE/ZCARD/ZADD 三步原子执行</li>
     *   <li>EXPIRE 防止冷 key 永不过期</li>
     * </ul>
     *
     * @param key   Redis Sorted Set key
     * @param qps   允许的请求数
     * @param window 时间窗口（秒）
     * @return true 表示放行；false 表示触发限流
     */
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
        return result != null && result.equals("1");
    }
}
