package com.njydsz.common.safe.ratelimit.aop;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.annotation.RateLimit;
import com.njydsz.common.safe.annotation.RateLimit.Dimension;
import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import com.njydsz.common.safe.ratelimit.core.RateLimitManager;
import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.common.safe.ratelimit.enums.RateLimitMode;
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
 * <p>同时支持两个注解：
 * <ul>
 *   <li>{@link SentinelRateLimit}：新版多维度限流注解（推荐使用）</li>
 *   <li>{@link RateLimit}：旧版 SPEL Key 限流注解（兼容保留）</li>
 * </ul>
 *
 * <p>注解选择策略：
 * <ul>
 *   <li>方法同时标注两个注解时，优先使用 {@link SentinelRateLimit}</li>
 *   <li>仅标注 {@link RateLimit} 时，自动适配为等效的 {@link RateLimitRule} 并执行限流</li>
 *   <li>限流被拒绝时抛出 {@link BusinessException}</li>
 * </ul>
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
     * 拦截 {@link SentinelRateLimit} 注解
     */
    @Around("@annotation(com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit)")
    public Object aroundSentinel(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        SentinelRateLimit annotation = method.getAnnotation(SentinelRateLimit.class);
        if (annotation == null) {
            return pjp.proceed();
        }

        RateLimitRule rule = ruleCache.computeIfAbsent(method, m -> buildRule(annotation));
        RateLimitContext context = buildContext(pjp, annotation, rule);
        return executeWithLimit(pjp, context, rule, annotation.errorCode(), annotation.message());
    }

    /**
     * 拦截旧版 {@link RateLimit} 注解（兼容保留）
     */
    @Around("@annotation(com.njydsz.common.safe.annotation.RateLimit)")
    public Object aroundLegacy(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        if (annotation == null) {
            return pjp.proceed();
        }

        // 如果同时存在 @SentinelRateLimit，交给 aroundSentinel 处理
        if (method.isAnnotationPresent(SentinelRateLimit.class)) {
            return pjp.proceed();
        }

        RateLimitRule rule = ruleCache.computeIfAbsent(method, m -> buildRuleFromLegacy(annotation));
        RateLimitContext context = buildContextFromLegacy(pjp, annotation, rule);
        return executeWithLimit(pjp, context, rule, "", annotation.message());
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

    private RateLimitRule buildRule(SentinelRateLimit annotation) {
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

    /**
     * 将旧版 {@link RateLimit} 注解适配为 {@link RateLimitRule}
     *
     * <p>语义映射：
     * <ul>
     *   <li>旧版 {@code qps} → {@code threshold}（每秒令牌数）</li>
     *   <li>旧版 {@code windowSeconds} → {@code window}（统计窗口）</li>
     *   <li>旧版 {@code burstCapacity} → {@code burstCapacity}（桶容量）</li>
     *   <li>旧版 {@code dimension} → {@link RateLimitDimension}（IP/USER/GLOBAL）</li>
     *   <li>算法固定为 {@link RateLimitAlgorithm#TOKEN_BUCKET}（旧版默认语义）</li>
     *   <li>模式固定为 {@link RateLimitMode#LOCAL}（旧版仅本地限流）</li>
     * </ul>
     */
    private RateLimitRule buildRuleFromLegacy(RateLimit annotation) {
        RateLimitDimension dimension = switch (annotation.dimension()) {
            case IP -> RateLimitDimension.IP;
            case USER -> RateLimitDimension.USER;
            case GLOBAL -> RateLimitDimension.API;
        };
        return RateLimitRule.builder()
                .resource(annotation.key())
                .algorithm(RateLimitAlgorithm.TOKEN_BUCKET)
                .dimension(dimension)
                .mode(RateLimitMode.LOCAL)
                .threshold(annotation.qps())
                .window(Duration.ofSeconds(annotation.windowSeconds()))
                .burstCapacity(annotation.burstCapacity())
                .enabled(true)
                .build();
    }

    private RateLimitContext buildContext(ProceedingJoinPoint pjp, SentinelRateLimit annotation, RateLimitRule rule) {
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

    /**
     * 为旧版 {@link RateLimit} 注解构造上下文
     *
     * <p>旧版 {@code key} 支持 SPEL 表达式，但为避免引入 SPEL 解析复杂度，
     * 这里将 {@code key} 作为资源前缀，再追加维度信息（IP/USER/GLOBAL）。
     * 若需要完整 SPEL 支持，建议迁移到 {@link SentinelRateLimit}。
     */
    private RateLimitContext buildContextFromLegacy(ProceedingJoinPoint pjp, RateLimit annotation, RateLimitRule rule) {
        Object[] args = pjp.getArgs();
        StringBuilder keyBuilder = new StringBuilder(annotation.key());

        // 维度后缀
        if (annotation.dimension() == Dimension.IP) {
            String ip = extractIp();
            if (ip != null) {
                keyBuilder.append(":ip:").append(ip);
            }
        } else if (annotation.dimension() == Dimension.USER) {
            String userId = extractUserId(args);
            if (userId != null) {
                keyBuilder.append(":user:").append(userId);
            }
        }
        // GLOBAL 不追加维度后缀

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
            }
            try {
                Method m = arg.getClass().getMethod("getCurrentUserId");
                Object val = m.invoke(arg);
                if (val != null) return val.toString();
            } catch (Exception ignored) {
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
        }
        return null;
    }
}
