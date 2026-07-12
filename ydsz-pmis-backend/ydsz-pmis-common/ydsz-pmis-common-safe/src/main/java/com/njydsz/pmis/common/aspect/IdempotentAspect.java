package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

/**
 * 幂等（防重提交）AOP
 *
 * <p>使用 Redis SETNX + Lua 原子脚本，保证并发场景下仅一次执行通过。
 * 支持 SpEL 表达式从方法参数中提取 key 片段。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final String USER_ID_HEADER = "X-User-Id";

    /** Redis SETNX 原子脚本 */
    private static final String LUA = "if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1 else return 0 end";

    /** Redis 脚本对象 */
    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    /** Redis 操作模板 */
    private final StringRedisTemplate redisTemplate;

    /** SpEL 表达式解析器 */
    private final SpelExpressionParser parser = new SpelExpressionParser();
    /** 参数名发现器 */
    private final ParameterNameDiscoverer paramNames = new DefaultParameterNameDiscoverer();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String key = buildKey(pjp, idempotent);
        Long ok = redisTemplate.execute(
                SCRIPT,
                Collections.singletonList(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(idempotent.ttlSeconds())
        );
        if (ok == null || ok == 0L) {
            log.warn("[Idempotent] 重复提交被拦截 key={}", key);
            throw new BizException(idempotent.message());
        }
        try {
            return pjp.proceed();
        } catch (RuntimeException e) {
            redisTemplate.delete(key);
            throw e;
        }
    }

    private String buildKey(ProceedingJoinPoint pjp, Idempotent ann) {
        StringBuilder sb = new StringBuilder("pmis:idempotent:");
        sb.append(ann.key());
        if (!ann.key().endsWith(":")) {
            sb.append(":");
        }
        if (ann.useUser()) {
            sb.append("u").append(getUserId()).append(":");
        }
        if (ann.keyFromArg() != null && !ann.keyFromArg().isEmpty()) {
            String extracted = extractSpEL(pjp, ann.keyFromArg());
            sb.append(extracted == null ? "null" : extracted);
        } else {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            sb.append(sig.getMethod().getName()).append(":");
            int h = argsHash(pjp.getArgs());
            sb.append(Integer.toHexString(h));
        }
        return sb.toString();
    }

    private String extractSpEL(ProceedingJoinPoint pjp, String expr) {
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            EvaluationContext ctx = new StandardEvaluationContext();
            String[] names = paramNames.getParameterNames(sig.getMethod());
            Object[] values = pjp.getArgs();
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    ctx.setVariable(names[i], values[i]);
                }
            }
            Expression e = parser.parseExpression(expr);
            Object v = e.getValue(ctx);
            return v == null ? "null" : String.valueOf(v);
        } catch (Exception ex) {
            log.warn("[Idempotent] SpEL 解析失败: {} {}", expr, ex.getMessage());
            return "spel-error";
        }
    }

    private int argsHash(Object[] args) {
        if (args == null) return 0;
        int h = 1;
        for (Object a : args) {
            h = 31 * h + (a == null ? 0 : a.toString().hashCode());
        }
        return h;
    }

    private String getUserId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String userId = attrs.getRequest().getHeader(USER_ID_HEADER);
                return userId != null ? userId : "anonymous";
            }
        } catch (Exception ignored) {
            // 非 Web 上下文
        }
        return "anonymous";
    }
}
