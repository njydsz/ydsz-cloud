package com.njydsz.common.safe.ratelimit.aop;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.safe.ratelimit.core.RateLimitManager;
import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 限流 AOP 切面
 *
 * <p>拦截 {@link RateLimit} 注解，执行限流决策。
 * 限流被拒绝时抛出 {@link BusinessException}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitManager rateLimitManager;

    /** 方法签名缓存：避免重复解析 */
    private final ConcurrentHashMap<Method, RateLimitRule> ruleCache = new ConcurrentHashMap<>();

    /**
     * 拦截 {@link RateLimit} 注解
     */
    @Around("@annotation(com.njydsz.common.safe.ratelimit.annotation.RateLimit)")
    public Object aroundSentinel(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        if (annotation == null) {
            return pjp.proceed();
        }

        RateLimitRule rule = ruleCache.computeIfAbsent(method, m -> buildRule(annotation));
        RateLimitContext context = buildContext(pjp, annotation, rule);
        return executeWithLimit(pjp, context, rule, annotation.errorCode(), annotation.message());
    }

    /**
     * 执行限流决策
     */
    private Object executeWithLimit(ProceedingJoinPoint pjp, RateLimitContext context,
                                    RateLimitRule rule, String errorCode, String message) throws Throwable {
        RateLimitDecision decision = rateLimitManager.decide(context);
        if (decision.isBlocked()) {
            log.warn("Rate limit blocked: resource={}, key={}, reason={}",
                    decision.getResource(), context.getResource(), decision.getReason());
            String code = (errorCode == null || errorCode.isEmpty()) ? "D02001" : errorCode;
            throw BusinessException.builder()
                    .code(code)
                    .key(message)
                    .build();
        }
        try {
            return pjp.proceed();
        } finally {
            // 并发数限流需要在 finally 中释放许可
            if (rule.getAlgorithm() == RateLimitAlgorithm.CONCURRENCY) {
                rateLimitManager.getRuleCache().getLimiter(rule.getResource())
                        .ifPresent(limiter -> limiter.release(context));
            }
        }
    }

    private RateLimitRule buildRule(RateLimit annotation) {
        return RateLimitRule.builder()
                .resource(annotation.resource())
                .algorithm(annotation.algorithm())
                .dimension(annotation.dimension())
                .mode(annotation.mode())
                .threshold(annotation.threshold())
                .window(Duration.ofMillis(annotation.windowMillis()))
                .burstCapacity(annotation.burstCapacity())
                .queueTimeout(Duration.ofMillis(annotation.queueTimeoutMillis()))
                .warmupPeriod(Duration.ofMillis(annotation.warmupMillis()))
                .errorCode(annotation.errorCode())
                .fallback(annotation.fallback())
                .enabled(true)
                .build();
    }

    private RateLimitContext buildContext(ProceedingJoinPoint pjp, RateLimit annotation, RateLimitRule rule) {
        Object[] args = pjp.getArgs();
        StringBuilder keyBuilder = new StringBuilder(rule.getResource());

        if (annotation.dimension() == RateLimitDimension.USER
                || annotation.dimension() == RateLimitDimension.HOT_USER) {
            // 从上下文中取 userId
            String userId = extractUserId(args);
            if (userId != null) {
                keyBuilder.append(":user:").append(userId);
            }
        } else if (annotation.dimension() == RateLimitDimension.IP) {
            String ip = extractIp();
            if (ip != null) {
                keyBuilder.append(":ip:").append(ip);
            }
        } else if (annotation.dimension() == RateLimitDimension.HOT_PARAM
                || annotation.dimension() == RateLimitDimension.HOT_GOODS) {
            int idx = annotation.keyParam();
            if (idx >= 0 && idx < args.length && args[idx] != null) {
                keyBuilder.append(":hot:").append(args[idx]);
            }
        }

        if (annotation.keyParam() >= 0 && annotation.keyParam() < args.length
                && args[annotation.keyParam()] != null) {
            keyBuilder.append(":").append(args[annotation.keyParam()]);
        }
        if (annotation.keyParam2() >= 0 && annotation.keyParam2() < args.length
                && args[annotation.keyParam2()] != null) {
            keyBuilder.append(":").append(args[annotation.keyParam2()]);
        }

        return RateLimitContext.builder()
                .resource(keyBuilder.toString())
                .args(args)
                .methodSignature(pjp.getSignature().toLongString())
                .build();
    }

    private String extractUserId(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg == null) continue;
            try {
                Method m = arg.getClass().getMethod("getUserId");
                Object val = m.invoke(arg);
                if (val != null) return val.toString();
            } catch (Exception ignored) {
                log.debug("Caught exception (ignored): {}", ignored.getMessage());
            }
            try {
                Method m = arg.getClass().getMethod("getCurrentUserId");
                Object val = m.invoke(arg);
                if (val != null) return val.toString();
            } catch (Exception ignored) {
                log.debug("Caught exception (ignored): {}", ignored.getMessage());
            }
        }
        return null;
    }

    private String extractIp() {
        try {
            HttpServletRequest request = currentRequest();
            if (request == null) return null;
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty()) {
                return ip.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception ex) {
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        try {
            RequestAttributes attrs =
                    RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest();
            }
        } catch (Exception ignored) {
            log.debug("Caught exception (ignored): {}", ignored.getMessage());
        }
        return null;
    }
}
