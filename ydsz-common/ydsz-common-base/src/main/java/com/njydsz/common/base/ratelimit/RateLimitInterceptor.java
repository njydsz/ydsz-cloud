package com.njydsz.common.base.ratelimit;

import java.lang.reflect.Method;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.safe.util.ClientIpResolver;

/**
 * 限流拦截器。
 *
 * <p>拦截标注了 {@link RateLimit} 注解的方法，限制访问频率。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = findRateLimitAnnotation(handlerMethod);
        if (rateLimit == null) {
            return true;
        }

        String key = buildRateLimitKey(request, handlerMethod, rateLimit);
        Duration window = Duration.ofMillis(rateLimit.timeUnit().toMillis(rateLimit.window()));

        if (rateLimiter.tryAcquire(key, rateLimit.limit(), window)) {
            return true;
        }

        // 限流拒绝
        log.debug("限流拒绝 | key={} | uri={}", key, request.getRequestURI());
        rejectRequest(response, rateLimit.message());
        return false;
    }

    /**
     * 查找方法上的 @RateLimit 注解（优先方法级，其次类级）。
     */
    private RateLimit findRateLimitAnnotation(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit != null) {
            return rateLimit;
        }
        return handlerMethod.getBeanType().getAnnotation(RateLimit.class);
    }

    /**
     * 构建限流键。
     */
    private String buildRateLimitKey(HttpServletRequest request, HandlerMethod handlerMethod,
                                     RateLimit rateLimit) {
        StringBuilder key = new StringBuilder("ratelimit:");
        key.append(handlerMethod.getBeanType().getSimpleName());
        key.append("#");
        key.append(handlerMethod.getMethod().getName());

        if (rateLimit.byClientIp()) {
            String clientIp = getClientIp(request);
            key.append(":").append(clientIp);
        } else if (!rateLimit.key().isBlank()) {
            key.append(":").append(rateLimit.key());
        }

        return key.toString();
    }

    /**
     * 获取客户端真实 IP。
     */
    private String getClientIp(HttpServletRequest request) {
        // 优先从 X-Forwarded-For 获取（代理/负载均衡场景）
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * 拒绝限流请求。
     */
    private void rejectRequest(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "1");
        BaseResponse<?> body = BaseResponse.error("RATE_LIMIT_REJECT", message);
        response.getWriter().write(body.toString());
    }
}
