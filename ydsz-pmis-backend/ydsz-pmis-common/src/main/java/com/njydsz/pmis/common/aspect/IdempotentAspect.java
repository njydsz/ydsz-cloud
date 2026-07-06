package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
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

import java.util.Collections;

/**
 * 幂等（防重提交）AOP
 *
 * <p>使用 Redis SETNX + Lua 原子脚本，保证并发场景下仅一次执行通过。
 *
 * <p>Lua 脚本：
 * <pre>
 *   if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
 *     return 1
 *   else
 *     return 0
 *   end
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

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

    /**
     * 环绕增强：基于 Redis SETNX 抢占幂等 key，重复提交抛出 BAD_REQUEST
     *
     * @param pjp        连接点
     * @param idempotent 幂等注解
     * @return 目标方法返回值
     * @throws Throwable    目标方法抛出的异常
     * @throws BizException 重复提交时抛出
     */
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
            throw new BizException(BizErrorCode.BAD_REQUEST, idempotent.message());
        }
        try {
            return pjp.proceed();
        } catch (RuntimeException e) {
            // 业务异常时主动释放锁，避免短暂窗口内无法重试
            redisTemplate.delete(key);
            throw e;
        }
    }

    /**
     * 构造幂等 key。
     *
     * <p>组成：前缀 {@code pmis:idempotent:{key}} + 可选 userId 维度 + 可选 SpEL 提取值；
     * 若未指定 keyFromArg，则兜底使用方法签名 + 参数 hash 作为区分维度。</p>
     *
     * @param pjp 连接点
     * @param ann 幂等注解
     * @return 完整 Redis key
     */
    private String buildKey(ProceedingJoinPoint pjp, Idempotent ann) {
        StringBuilder sb = new StringBuilder("pmis:idempotent:");
        sb.append(ann.key());
        if (!ann.key().endsWith(":")) {
            sb.append(":");
        }
        if (ann.useUser()) {
            String user = "anon";
            try {
                LoginUser u = SecurityContext.getCurrentOrNull();
                if (u != null && u.getUserId() != null) {
                    user = String.valueOf(u.getUserId());
                }
            } catch (Exception ignored) {
                log.debug("[Idempotent] 获取当前用户失败，使用匿名标识 key={}", ann.key(), ignored);
            }
            sb.append("u").append(user).append(":");
        }
        if (ann.keyFromArg() != null && !ann.keyFromArg().isEmpty()) {
            String extracted = extractSpEL(pjp, ann.keyFromArg());
            sb.append(extracted == null ? "null" : extracted);
        } else {
            // 兜底：附加 method 签名 + args hash
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            sb.append(sig.getMethod().getName()).append(":");
            int h = argsHash(pjp.getArgs());
            sb.append(Integer.toHexString(h));
        }
        return sb.toString();
    }

    /**
     * 使用 SpEL 从方法参数中提取 key 片段。
     *
     * <p>解析失败时返回 "spel-error" 以保证 key 仍可生成，并打印 warn 日志。</p>
     *
     * @param pjp  连接点
     * @param expr SpEL 表达式，如 {@code #dto.username}
     * @return 提取到的字符串值
     */
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

    /**
     * 计算方法参数 hash，作为未指定 keyFromArg 时的兜底区分维度。
     *
     * @param args 方法参数数组
     * @return hash 值
     */
    private int argsHash(Object[] args) {
        if (args == null) return 0;
        int h = 1;
        for (Object a : args) {
            h = 31 * h + (a == null ? 0 : a.toString().hashCode());
        }
        return h;
    }
}
