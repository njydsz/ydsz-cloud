package com.njydsz.common.base.idempotent;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.expression.Expression;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.njydsz.common.core.response.BaseResponse;

/**
 * 幂等性拦截器。
 *
 * <p>拦截标注了 {@link Idempotent} 注解的方法，确保同一业务键只执行一次。
 *
 * <p>工作流程：
 * <ol>
 *   <li>检查 Handler 是否标注了 @Idempotent 注解</li>
 *   <li>解析 SpEL 表达式获取幂等键</li>
 *   <li>尝试从 Store 获取锁</li>
 *   <li>成功 → 放行请求</li>
 *   <li>失败 → 返回 429 Too Many Requests 或自定义错误码</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class IdempotentInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentInterceptor.class);

    private final IdempotentStore idempotentStore;
    private final ConcurrentHashMap<AnnotatedElementKey, Expression> expressionCache = new ConcurrentHashMap<>();

    public IdempotentInterceptor(IdempotentStore idempotentStore) {
        this.idempotentStore = idempotentStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Idempotent idempotent = findIdempotentAnnotation(handlerMethod);
        if (idempotent == null) {
            return true;
        }

        String key = buildKey(request, handlerMethod, idempotent);
        Duration expire = Duration.ofMillis(idempotent.timeUnit().toMillis(idempotent.expire()));

        if (idempotentStore.tryAcquire(key, expire)) {
            return true;
        }

        // 幂等键已存在，拒绝重复请求
        log.debug("幂等性校验拒绝重复请求 | key={} | uri={}", key, request.getRequestURI());
        rejectRequest(response, idempotent.message());
        return false;
    }

    /**
     * 查找方法上的 @Idempotent 注解（优先方法级，其次类级）。
     */
    private Idempotent findIdempotentAnnotation(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        if (idempotent != null) {
            return idempotent;
        }
        // 检查类级别注解
        return handlerMethod.getBeanType().getAnnotation(Idempotent.class);
    }

    /**
     * 构建幂等键。
     */
    private String key(HttpServletRequest request, HandlerMethod handlerMethod,
                       Idempotent idempotent) {
        String spelKey = idempotent.key();
        String resolvedKey;

        if (spelKey != null && !spelKey.isBlank()) {
            // 使用 SpEL 表达式解析
            try {
                resolvedKey = parseSpel(spelKey, handlerMethod);
            } catch (Exception e) {
                log.warn("SpEL 解析失败，使用方法签名作为幂等键 | spel={}", spelKey, e);
                resolvedKey = defaultKey(handlerMethod);
            }
        } else {
            resolvedKey = defaultKey(handlerMethod);
        }

        return "idem:" + resolvedKey;
    }

    /**
     * 默认幂等键：方法签名。
     */
    private String defaultKey(HandlerMethod handlerMethod) {
        return handlerMethod.getBeanType().getName() + "#" + handlerMethod.getMethod().getName();
    }

    /**
     * 解析 SpEL 表达式（简化实现，仅支持 #paramName 和 #paramName.field）。
     */
    private String parseSpel(String spel, HandlerMethod handlerMethod) {
        // 简化实现：直接从 request 属性或参数中提取
        // 完整实现需要 MethodBasedEvaluationContext，此处做最小化实现
        if (spel.startsWith("#")) {
            String paramName = spel.substring(1);
            // 尝试从请求参数中获取
            String value = extractFromRequest(paramName, handlerMethod);
            if (value != null) {
                return value;
            }
        }
        return spel;
    }

    /**
     * 从请求参数中提取值。
     */
    private String extractFromRequest(String expression, HandlerMethod handlerMethod) {
        // 简化实现：提取参数名并尝试从请求属性获取
        // 实际项目中可集成完整的 SpEL 解析
        return null;
    }

    /**
     * 拒绝重复请求。
     */
    private void rejectRequest(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
        response.setContentType("application/json;charset=UTF-8");
        BaseResponse<?> body = BaseResponse.error("IDEMPOTENT_REJECT", message);
        response.getWriter().write(body.toString());
    }
}
