package com.njydsz.common.lock.aspect;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.lock.exception.IdempotentException;
import com.njydsz.common.lock.idempotent.IdempotentStrategy;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.util.LockExpressionUtils;


/**
 * 接口幂等性 AOP 切面
 *
 * <p>拦截标注 {@link Idempotent} 的 Controller 方法，委托 {@link IdempotentStrategy}
 * 实现"在 TTL 窗口内同一幂等键只处理一次"的语义，防止用户重复提交或网络重试造成脏数据。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>解析 {@link Idempotent#key()}，支持 SpEL（{@code #{...}} 包裹）；
 *       为空时按"类名#方法名#参数摘要"自动生成</li>
 *   <li>委托 {@link IdempotentStrategy#acquire} 获取幂等锁，成功返回 token</li>
 *   <li>获取失败时抛出 {@link IdempotentException}（HTTP 409 Conflict）</li>
 *   <li>目标方法执行完成后，按"业务异常自动释放锁"规则处理：
 *       <ul>
 *         <li>正常返回 / 无异常：保留幂等锁至 TTL 自然过期</li>
 *         <li>抛出 {@code BusinessException}（含子类）：立即释放幂等锁，
 *             让客户端修正参数后可重试提交（项目硬约束 P2-9）</li>
 *         <li>抛出其他异常（如 SysException / RuntimeException）：保留幂等锁，
 *             防止重试风暴击穿下游</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Redis 键命名空间</h3>
 * <ul>
 *   <li>前缀：从 {@code ydsz.lock.idempotent.key-prefix} 读取（默认 {@code ydsz:idem:}）</li>
 *   <li>可选命名空间：{@code ${ydsz.lock.namespace}:}（多服务共享 Redis 时隔离）</li>
 *   <li>完整 key：{@code ${keyPrefix}${namespace}:${userKey}}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class IdempotentAspect {

    /** 幂等策略（委托实现，消除平行 Lua 脚本） */
    private final IdempotentStrategy idempotentStrategy;

    /** 幂等键 Redis 前缀（从配置读取，不再硬编码） */
    private final String keyPrefix;

    /** 可选命名空间（多服务共享 Redis 时隔离键），来自 ydsz.lock.namespace */
    private final String namespace;

    /** 锁指标收集器（可选，用于观测幂等命中率） */
    private final LockMetrics lockMetrics;

    /**
     * 构造 IdempotentAspect
     *
     * @param idempotentStrategy 幂等策略
     * @param keyPrefix          幂等键 Redis 前缀（从 ydsz.lock.idempotent.key-prefix 读取）
     * @param namespace          命名空间（可为 null）
     * @param lockMetrics        锁指标收集器（可为 null）
     */
    public IdempotentAspect(IdempotentStrategy idempotentStrategy, String keyPrefix,
                             String namespace, LockMetrics lockMetrics) {
        this.idempotentStrategy = idempotentStrategy;
        this.keyPrefix = keyPrefix != null && !keyPrefix.isEmpty() ? keyPrefix : "ydsz:idem:";
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
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        // 方法级 @IdempotentExempt 检查：直接放行
        if (method.isAnnotationPresent(IdempotentExempt.class)) {
            log.debug("[ydsz-lock] [idempotent] 方法标注 @IdempotentExempt，跳过幂等检查 method={}", method.getName());
            return proceed(joinPoint);
        }

        String userKey = resolveUserKey(idempotent.key(), method, joinPoint.getArgs());
        String redisKey = buildRedisKey(userKey);

        long acquireStart = System.currentTimeMillis();
        String token = idempotentStrategy.acquire(redisKey, idempotent.ttlSeconds() * SECONDS_TO_MILLIS);
        if (token == null) {
            // 幂等命中：同一 key 在 TTL 窗口内被重复提交，拒绝处理
            recordIdempotentHit(idempotent, redisKey);
            recordAcquireFail("idempotent");
            throw new IdempotentException(idempotent.message(), redisKey);
        }

        // acquire 成功
        recordAcquireSuccess(System.currentTimeMillis() - acquireStart, "idempotent");
        long heldStart = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            // 正常返回：保留幂等锁至 TTL 自然过期，防止重复提交
            log.debug("[ydsz-lock] [idempotent] 方法正常完成，保留幂等锁至 TTL 过期 key={}", redisKey);
            return result;
        } catch (BusinessException bizEx) {
            // 业务异常：自动释放幂等锁，允许客户端修正后重试（项目硬约束 P2-9）
            idempotentStrategy.release(redisKey, token);
            recordRelease(System.currentTimeMillis() - heldStart, "idempotent");
            log.debug("[ydsz-lock] [idempotent] 业务异常释放幂等锁 key={} cause={}", redisKey, bizEx.getMessage());
            throw bizEx;
        } catch (RuntimeException | Error e) {
            // 非 BusinessException（如 SysException / RuntimeException / Error）：保留幂等锁
            // 防止下游异常时客户端重试风暴击穿系统
            log.warn("[ydsz-lock] [idempotent] 非 BusinessException 抛出，保留幂等锁 key={} cause={}",
                    redisKey, e.getClass().getSimpleName());
            throw e;
        } catch (Throwable ex) {
            log.warn("[ydsz-lock] [idempotent] 检查型异常包装后抛出，保留幂等锁 key={} cause={}",
                    redisKey, ex.getClass().getSimpleName());
            throw wrapCheckedException(ex);
        }
    }

    /**
     * 执行目标方法并传播异常
     *
     * <p>切面不声明 {@code throws Throwable}（遵循编码规范），
     * 运行时异常与 Error 原样传播，检查型异常包装为业务异常。</p>
     *
     * @param joinPoint 连接点
     * @return 目标方法返回值
     */
    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw wrapCheckedException(t);
        }
    }

    /**
     * 将检查型异常包装为业务异常
     *
     * @param cause 原始异常
     * @return 包装后的业务异常
     */
    private BusinessException wrapCheckedException(Throwable cause) {
        BusinessException wrapped = new BusinessException(CoreExceptionCode.FAIL, cause);
        wrapped.setMessage("接口执行异常: " + cause.getMessage());
        return wrapped;
    }

    /**
     * 记录 acquire 成功指标
     *
     * @param waitMillis 等待耗时（毫秒）
     * @param lockType   锁类型
     */
    private void recordAcquireSuccess(long waitMillis, String lockType) {
        if (lockMetrics != null) {
            lockMetrics.recordAcquireSuccess(waitMillis, lockType);
        }
    }

    /**
     * 记录 acquire 失败指标
     *
     * @param lockType 锁类型
     */
    private void recordAcquireFail(String lockType) {
        if (lockMetrics != null) {
            lockMetrics.recordAcquireFail(lockType);
        }
    }

    /**
     * 记录 release 指标
     *
     * @param holdMillis 持锁耗时（毫秒）
     * @param lockType   锁类型
     */
    private void recordRelease(long holdMillis, String lockType) {
        if (lockMetrics != null) {
            lockMetrics.recordRelease(holdMillis, lockType);
        }
    }

    // ============================== 私有 ==============================

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
        try {
            String resolved = LockExpressionUtils.resolve(keyExpression, method, args);
            if (resolved == null || resolved.isEmpty()) {
                log.warn("[ydsz-lock] [idempotent] SpEL 解析结果为空，降级为自动 key expr={}", keyExpression);
                return generateAutoKey(method, args);
            }
            return resolved;
        } catch (Exception e) {
            log.warn("[ydsz-lock] [idempotent] SpEL 解析失败，降级为自动 key expr={} cause={}",
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
        String argsDigest = digestArgs(method, args);
        return className + "#" + methodName + "#" + argsDigest;
    }

    /**
     * 计算参数摘要（SHA-256 前 16 字节十六进制，避免过长 key）
     * <p>自动过滤标注 {@link IdempotentExempt} 的参数，排除分页参数/时间戳等。
     *
     * @param method 目标方法（用于获取参数级 @IdempotentExempt 注解）
     * @param args   方法参数
     * @return 参数摘要
     */
    private String digestArgs(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return "no-args";
        }
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                // 过滤参数级 @IdempotentExempt 注解
                if (i < parameters.length && parameters[i] != null
                        && parameters[i].isAnnotationPresent(IdempotentExempt.class)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("|");
                }
                sb.append(args[i] == null ? "null" : args[i].toString());
            }
            // 如果全部参数都被豁免，使用 args 数量作为标识
            if (sb.length() == 0) {
                sb.append("all-exempt:").append(args.length);
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
            int activeCount = 0;
            for (int i = 0; i < args.length && i < parameters.length; i++) {
                if (parameters[i] == null || !parameters[i].isAnnotationPresent(IdempotentExempt.class)) {
                    activeCount++;
                }
            }
            return activeCount == 0 ? "all-exempt" : String.valueOf(Arrays.hashCode(args)) + ":" + activeCount;
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
            return keyPrefix + userKey;
        }
        return keyPrefix + namespace + ":" + userKey;
    }

    /**
     * 记录幂等命中（可选指标收集）
     *
     * @param idempotent 注解
     * @param redisKey   Redis key
     */
    private void recordIdempotentHit(Idempotent idempotent, String redisKey) {
        log.info("[ydsz-lock] [idempotent] 幂等命中拒绝 key={} ttl={}s", redisKey, idempotent.ttlSeconds());
        if (lockMetrics != null) {
            lockMetrics.recordIdempotentHit();
        }
    }
}
