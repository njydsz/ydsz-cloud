package com.njydsz.pmis.common.safe.ratelimit;

import java.lang.reflect.Method;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.redis.service.RedisService;
import com.njydsz.pmis.common.safe.annotation.RateLimit;
import com.njydsz.pmis.common.safe.ratelimit.RateLimitProperties.Dimension;
import com.njydsz.pmis.common.safe.util.ClientIpResolver;

/**
 * 方法级限流 AOP 切面
 *
 * <p>拦截标注了 {@link RateLimit} 注解的 Controller 方法，基于 Redis 滑动窗口
 * 实现方法级限流。支持 SPEL 表达式解析 key，实现按用户 ID、按 IP 等维度的精细限流。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li>SPEL Key 解析：支持 {@code #userId}、{@code #dto.type} 等方法参数引用</li>
 *   <li>多维度限流：IP / USER / GLOBAL 三种维度组合</li>
 *   <li>滑动窗口：Redis ZSet + Lua 脚本保证分布式原子性</li>
 *   <li>降级策略：Redis 异常时 fail-open（放行不中断服务）</li>
 *   <li>统一响应：限流触发时返回 HTTP 429 + BaseResponse JSON</li>
 * </ul>
 *
 * <p><b>执行流程：</b>
 * <ol>
 *   <li>解析 {@link RateLimit#key()} 的 SPEL 表达式（如有）</li>
 *   <li>根据 {@link RateLimit#dimension()} 组装最终限流 key</li>
 *   <li>调用 Redis Lua 脚本执行滑动窗口限流判定</li>
 *   <li>限流通过 → 执行原方法；限流拒绝 → 返回 429</li>
 * </ol>
 *
 * @since 1.3.0
 * @see RateLimit
 * @see RateLimitFilter
 */
@Aspect
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    /**
     * Redis 滑动窗口限流 Lua 脚本
     *
     * <p>使用 ZSet 实现滑动窗口：移除过期成员 → 统计当前窗口成员数 → 未超限则添加新成员。
     */
    private static final String LUA_SLIDING_WINDOW_SCRIPT =
            "local key = KEYS[1]\n" +
            "local window = tonumber(ARGV[1])\n" +
            "local limit = tonumber(ARGV[2])\n" +
            "local burst = tonumber(ARGV[3])\n" +
            "local now = tonumber(ARGV[4])\n" +
            "local windowStart = now - window * 1000\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "if count >= burst then\n" +
            "    return 0\n" +
            "end\n" +
            "redis.call('ZADD', key, now, now .. ':' .. math.random(100000))\n" +
            "redis.call('EXPIRE', key, window + 1)\n" +
            "return 1\n";

    private final RedisService redisService;
    private final RateLimitProperties properties;

    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * @param redisService Redis 服务
     * @param properties   限流配置属性
     */
    public RateLimitAspect(RedisService redisService, RateLimitProperties properties) {
        this.redisService = redisService;
        this.properties = properties;
    }

    /**
     * 拦截 {@link RateLimit} 注解方法，执行限流检查
     *
     * @param joinPoint   AOP 连接点
     * @param rateLimit   限流注解
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("@annotation(rateLimit)")
    public Object around(@NonNull ProceedingJoinPoint joinPoint, @NonNull RateLimit rateLimit) throws Throwable {
        String resolvedKey = resolveKey(rateLimit, joinPoint);
        String fullKey = buildFullKey(rateLimit.dimension(), resolvedKey);

        long now = System.currentTimeMillis();
        int window = rateLimit.windowSeconds();
        int limit = (int) rateLimit.qps() * window;
        int burst = Math.max(rateLimit.burstCapacity(), limit);

        boolean allowed;
        try {
            Long result = redisService.executeScript(
                    LUA_SLIDING_WINDOW_SCRIPT,
                    List.of(fullKey),
                    Long.class,
                    window, limit, burst, now
            );
            allowed = result != null && result == 1L;
        } catch (Exception e) {
            log.warn("【安全模块】Redis 限流不可用，放行请求 | key={}, error={}", fullKey, e.getMessage());
            return joinPoint.proceed();
        }

        if (!allowed) {
            log.warn("【安全模块】方法级限流触发 | key={}, target={}.{}()",
                    fullKey, joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName());
            return buildRateLimitResponse(rateLimit);
        }

        return joinPoint.proceed();
    }

    /**
     * 解析限流 Key（支持 SPEL 表达式）
     *
     * <p>如果 key 以 {@code #} 开头，视为 SPEL 表达式解析；否则作为固定字符串。
     * SPEL 上下文中可引用方法参数（按参数名绑定）。
     *
     * @param rateLimit 限流注解
     * @param joinPoint AOP 连接点
     * @return 解析后的 key 字符串
     */
    private String resolveKey(RateLimit rateLimit, ProceedingJoinPoint joinPoint) {
        String key = rateLimit.key();
        if (!StringUtils.hasText(key)) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return signature.getDeclaringType().getSimpleName() + ":" + signature.getName();
        }

        if (!key.contains("#")) {
            return key;
        }

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);

            EvaluationContext context = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            context.setVariable("targetType", signature.getDeclaringType().getSimpleName());
            context.setVariable("methodName", signature.getName());

            Expression expression = spelParser.parseExpression(key);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : key;
        } catch (Exception e) {
            log.warn("【安全模块】SPEL Key 解析失败，使用原始 key | key={}, error={}", key, e.getMessage());
            return key;
        }
    }

    /**
     * 根据限流维度组装最终 Redis Key
     *
     * @param dimension   限流维度
     * @param resolvedKey 解析后的 key
     * @return 完整 Redis Key
     */
    private String buildFullKey(Dimension dimension, String resolvedKey) {
        switch (dimension) {
            case USER:
                String userId = getCurrentUserId();
                if (StringUtils.hasText(userId)) {
                    return properties.getUserKey() + userId + ":" + resolvedKey;
                }
                return properties.getIpKey() + getCurrentIp() + ":" + resolvedKey;
            case GLOBAL:
                return properties.getGlobalKey() + ":" + resolvedKey;
            case IP:
            default:
                return properties.getIpKey() + getCurrentIp() + ":" + resolvedKey;
        }
    }

    /**
     * 从请求头获取当前用户 ID
     *
     * @return 用户 ID，未找到返回 null
     */
    private String getCurrentUserId() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            return request.getHeader("X-User-Id");
        }
        return null;
    }

    /**
     * 获取当前客户端 IP
     *
     * @return 客户端 IP
     */
    private String getCurrentIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            return ClientIpResolver.getClientIp(request);
        }
        return "unknown";
    }

    /**
     * 获取当前 HTTP 请求
     *
     * @return HttpServletRequest，非 Web 环境返回 null
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 构建限流响应体
     *
     * <p>返回 BaseResponse JSON，HTTP 状态码 429。
     *
     * @param rateLimit 限流注解（包含自定义提示消息）
     * @return BaseResponse 对象（由 Controller 全局异常处理转换为 HTTP 响应）
     */
    private Object buildRateLimitResponse(RateLimit rateLimit) {
        return BaseResponse.error(
                UnifiedExceptionCode.RATE_LIMIT.getCode(),
                rateLimit.message()
        );
    }
}
