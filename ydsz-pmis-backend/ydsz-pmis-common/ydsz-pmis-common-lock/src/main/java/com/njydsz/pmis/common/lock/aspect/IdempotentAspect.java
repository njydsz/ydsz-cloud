package com.njydsz.pmis.common.lock.aspect;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.lock.exception.IdempotentException;
import com.njydsz.pmis.common.lock.metrics.LockMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * 接口幂等性 AOP 切面
 *
 * <p>拦截标注 {@link Idempotent} 的 Controller 方法，基于 Redis {@code SET NX EX} Lua 脚本
 * 实现"在 TTL 窗口内同一幂等键只处理一次"的语义，防止用户重复提交或网络重试造成脏数据。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>解析 {@link Idempotent#key()}，支持 SpEL（{@code #{...}} 包裹）；
 *       为空时按"类名#方法名#参数摘要"自动生成</li>
 *   <li>执行 Redis Lua 脚本：成功返回 1（拿到幂等锁），失败返回 0（重复提交）</li>
 *   <li>返回 0 时抛出 {@link IdempotentException}（HTTP 409 Conflict）</li>
 *   <li>目标方法执行完成后，按"业务异常自动释放锁"规则处理：
 *       <ul>
 *         <li>正常返回 / 无异常：保留幂等锁至 TTL 自然过期</li>
 *         <li>抛出 {@code BusinessException}（含子类）：立即释放幂等锁，
 *             让客户端修正参数后可重试提交（项目硬约束 P2-9）</li>
 *         <li>抛出其他异常（如 SysException / RuntimeException）：保留幂等锁，
 *             防止重试风暴击穿下游</li>
 *       </ul>
 *   </li>
 *   <li>Redis 不可用时降级放行，仅记录 WARN 日志，避免拖垮主流程</li>
 * </ol>
 *
 * <h3>Redis 键命名空间</h3>
 * <ul>
 *   <li>固定前缀：{@code pmis:idem:}</li>
 *   <li>可选命名空间：{@code ${ydsz.lock.namespace}:}（多服务共享 Redis 时隔离）</li>
 *   <li>完整 key：{@code pmis:idem:[namespace:]${userKey}}</li>
 * </ul>
 *
 * <h3>与 FlowUrgeLimiter / RedisStringOps 的关系</h3>
 * <p>三者均使用 {@code SET NX EX} Lua 脚本，但语义不同：
 * <ul>
 *   <li>{@code FlowUrgeLimiter}：业务限流（催办冷却），业务侧自行判断 boolean</li>
 *   <li>{@code RedisStringOps.setIfAbsent}：通用工具方法，自动添加抖动防雪崩</li>
 *   <li>本切面：接口幂等，业务异常自动释放锁，Redis 故障降级放行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class IdempotentAspect {

    /** Redis key 固定前缀，所有幂等键统一以此开头便于排查 */
    private static final String KEY_PREFIX = "pmis:idem:";

    /** Redis SET NX EX 原子 Lua 脚本（与 FlowUrgeLimiter 同款，保证并发安全） */
    private static final String ACQUIRE_LUA =
            "if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1 else return 0 end";

    /** 释放幂等锁的 Lua 脚本：仅当 value 匹配时才 DEL，避免误删他人持有的锁 */
    private static final String RELEASE_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(ACQUIRE_LUA, Long.class);
    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(RELEASE_LUA, Long.class);

    /** SpEL 表达式解析器 */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /** 参数名发现器（用于 SpEL 上下文绑定） */
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /** SpEL 表达式缓存，避免每次反射解析 */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /** Redis 客户端（StringRedisTemplate，避免 Jackson 序列化导致 value 带 class 信息） */
    private final StringRedisTemplate redisTemplate;

    /** 可选命名空间（多服务共享 Redis 时隔离键），来自 ydsz.lock.namespace */
    private final String namespace;

    /** 锁指标收集器（可选，用于观测幂等命中率） */
    private final LockMetrics lockMetrics;

    /**
     * 构造 IdempotentAspect
     *
     * @param redisTemplate Redis 客户端
     * @param namespace     命名空间（可为 null）
     * @param lockMetrics   锁指标收集器（可为 null）
     */
    public IdempotentAspect(StringRedisTemplate redisTemplate, String namespace, LockMetrics lockMetrics) {
        this.redisTemplate = redisTemplate;
        this.namespace = namespace;
        this.lockMetrics = lockMetrics;
    }

    /**
     * 拦截 {@link Idempotent} 注解方法，执行幂等校验
     *
     * @param joinPoint  AOP 连接点
     * @param idempotent 幂等注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String userKey = resolveUserKey(idempotent.key(), method, joinPoint.getArgs());
        String redisKey = buildRedisKey(userKey);
        String token = generateToken();

        boolean acquired = tryAcquire(redisKey, token, idempotent.ttlSeconds());
        if (!acquired) {
            recordIdempotentHit(idempotent, redisKey);
            throw new IdempotentException(idempotent.message(), redisKey);
        }

        try {
            Object result = joinPoint.proceed();
            // 正常返回：保留幂等锁至 TTL 自然过期，防止重复提交
            return result;
        } catch (BusinessException bizEx) {
            // 业务异常：自动释放幂等锁，允许客户端修正后重试（项目硬约束 P2-9）
            releaseLock(redisKey, token);
            log.debug("[IdempotentAspect] 业务异常释放幂等锁 key={} cause={}", redisKey, bizEx.getMessage());
            throw bizEx;
        } catch (Throwable ex) {
            // 非 BusinessException（如 SysException / RuntimeException / Error）：保留幂等锁
            // 防止下游异常时客户端重试风暴击穿系统
            log.warn("[IdempotentAspect] 非 BusinessException 抛出，保留幂等锁 key={} cause={}",
                    redisKey, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // ============================== 私有 ==============================

    /**
     * 尝试获取幂等锁
     *
     * @param redisKey    Redis key
     * @param token       锁 token（value）
     * @param ttlSeconds  TTL 秒数
     * @return true=获取成功；false=已被占用（重复提交）
     */
    private boolean tryAcquire(String redisKey, String token, int ttlSeconds) {
        if (ttlSeconds <= 0) {
            // TTL 非法时降级放行，避免误杀正常请求
            log.warn("[IdempotentAspect] ttlSeconds={} 非法，降级放行 key={}", ttlSeconds, redisKey);
            return true;
        }
        try {
            Long ok = redisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    Collections.singletonList(redisKey),
                    token,
                    String.valueOf(ttlSeconds)
            );
            return ok != null && ok == 1L;
        } catch (Exception e) {
            // Redis 不可用：降级放行，避免拖垮主流程（与 FlowUrgeLimiter 一致）
            log.warn("[IdempotentAspect] Redis 不可用，降级放行 key={} cause={}", redisKey, e.getMessage());
            return true;
        }
    }

    /**
     * 释放幂等锁（仅当 token 匹配时才删除）
     *
     * @param redisKey Redis key
     * @param token    锁 token（value）
     */
    private void releaseLock(String redisKey, String token) {
        try {
            redisTemplate.execute(
                    RELEASE_SCRIPT,
                    Collections.singletonList(redisKey),
                    token
            );
        } catch (Exception e) {
            log.warn("[IdempotentAspect] 释放幂等锁失败 key={} cause={}", redisKey, e.getMessage());
        }
    }

    /**
     * 解析用户幂等键
     * <p>支持两种形式：
     * <ul>
     *   <li>空字符串：自动按"类名#方法名#参数摘要"生成</li>
     *   <li>{@code #{...}} 包裹的 SpEL 表达式：解析后作为 key</li>
     *   <li>纯字符串（如 {@code "order:create"}）：直接使用</li>
     * </ul>
     *
     * @param keyExpression 注解上的 key 表达式
     * @param method        目标方法
     * @param args          方法参数
     * @return 解析后的幂等键
     */
    private String resolveUserKey(String keyExpression, Method method, Object[] args) {
        if (keyExpression == null || keyExpression.isEmpty()) {
            return generateAutoKey(method, args);
        }
        if (!keyExpression.contains("#{")) {
            return keyExpression;
        }
        // 提取 #{...} 内的 SpEL 表达式并解析
        String spelExpression = keyExpression.replaceAll("#\\{(.+?)}", "$1");
        try {
            Expression expression = expressionCache.computeIfAbsent(spelExpression,
                    expr -> expressionParser.parseExpression(expr));
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length; i++) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
            String evaluated = expression.getValue(context, String.class);
            return evaluated != null ? evaluated : generateAutoKey(method, args);
        } catch (Exception e) {
            log.warn("[IdempotentAspect] SpEL 解析失败，降级为自动 key expr={} cause={}",
                    keyExpression, e.getMessage());
            return generateAutoKey(method, args);
        }
    }

    /**
     * 自动生成幂等键（类名#方法名#参数摘要）
     *
     * @param method 目标方法
     * @param args   方法参数
     * @return 自动生成的幂等键
     */
    private String generateAutoKey(Method method, Object[] args) {
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        String argsDigest = digestArgs(args);
        return className + "#" + methodName + "#" + argsDigest;
    }

    /**
     * 计算参数摘要（SHA-256 前 16 字节十六进制，避免过长 key）
     *
     * @param args 方法参数
     * @return 参数摘要
     */
    private String digestArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "no-args";
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append("|");
                }
                sb.append(args[i] == null ? "null" : args[i].toString());
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16 && i < digest.length; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            // 降级为 hashCode
            return String.valueOf(Arrays.hashCode(args));
        }
    }

    /**
     * 构建 Redis key（添加前缀和命名空间）
     *
     * @param userKey 用户幂等键
     * @return 完整 Redis key
     */
    private String buildRedisKey(String userKey) {
        if (namespace == null || namespace.isEmpty()) {
            return KEY_PREFIX + userKey;
        }
        return KEY_PREFIX + namespace + ":" + userKey;
    }

    /**
     * 生成锁 token（value），用于释放锁时校验
     *
     * @return token 字符串
     */
    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 记录幂等命中（可选指标收集）
     *
     * @param idempotent 注解
     * @param redisKey   Redis key
     */
    private void recordIdempotentHit(Idempotent idempotent, String redisKey) {
        log.info("[IdempotentAspect] 幂等命中拒绝 key={} ttl={}s", redisKey, idempotent.ttlSeconds());
        if (lockMetrics != null) {
            lockMetrics.recordIdempotentHit();
        }
    }

    // ============================== 测试可见方法 ==============================

    /**
     * 暴露 KEY_PREFIX 供单元测试断言使用
     *
     * @return 固定前缀
     */
    static String keyPrefix() {
        return KEY_PREFIX;
    }

    /**
     * 暴露命名空间供单元测试断言使用
     *
     * @return 命名空间（可能为 null）
     */
    String namespace() {
        return namespace;
    }
}
