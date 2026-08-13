package com.njydsz.common.redis.interceptor;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;

import com.njydsz.common.redis.annotation.RedisOperation;
import com.njydsz.common.redis.enums.RedisBusinessException;
import com.njydsz.common.redis.enums.RedisConnectionException;
import com.njydsz.common.redis.enums.RedisOperationException;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 操作统一异常处理拦截器
 *
 * <p>基于 Spring AOP 的统一异常兜底处理器，提供：
 * <ol>
 *   <li>将 Spring Data Redis 常见异常统一转换为内部异常体系</li>
 *   <li>区分可恢复异常（连接失败/超时）与不可恢复异常（序列化/参数错误）</li>
 *   <li>结构化日志记录，包含操作类型、方法名、key 信息</li>
 * </ol>
 *
 * <p><b>异常转换规则：</b>
 * <ul>
 *   <li>RedisConnectionFailureException → RedisConnectionException（可重试）</li>
 *   <li>QueryTimeoutException → RedisConnectionException（可重试）</li>
 *   <li>SerializationException → RedisBusinessException（不可重试）</li>
 *   <li>IllegalArgumentException → RedisBusinessException（不可重试）</li>
 * </ul>
 *
 * <p><b>注意：</b>此拦截器仅作为全局兜底，不替代各组件内部的精细化异常处理。
 * 对于已有 FailOpenPolicy 策略的组件（如 RedisRateLimiter），优先使用其内部策略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class RedisOperationExceptionHandler {

    /**
     * 拦截所有 Redis Ops 子组件的方法
     *
     * <p>通过 AOP 切面统一捕获异常并转换为内部异常体系。
     * 各组件内部如已有更精细的异常处理（如 FailOpenPolicy），可正常执行，
     * 此拦截器仅兜底未被内部处理的异常。
     */
    @Around("execution(* com.njydsz.common.redis.service.ops.*.*(..))")
    public Object handleOperationException(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        try {
            return joinPoint.proceed();
        } catch (RedisConnectionFailureException e) {
            // 连接失败 → 可恢复异常
            String key = extractKey(joinPoint.getArgs());
            log.error("【Redis】连接失败 | method={} | key={} | error={}", methodName, key, e.getMessage());
            throw new RedisConnectionException(key, methodName, e);
        } catch (QueryTimeoutException e) {
            // 查询超时 → 可恢复异常
            String key = extractKey(joinPoint.getArgs());
            log.error("【Redis】查询超时 | method={} | key={} | error={}", methodName, key, e.getMessage());
            throw new RedisConnectionException(key, methodName, e);
        } catch (SerializationException e) {
            // 序列化失败 → 不可恢复异常
            log.error("【Redis】序列化失败 | method={} | error={}", methodName, e.getMessage());
            throw new RedisBusinessException(null, methodName, e);
        } catch (IllegalArgumentException e) {
            // 参数非法 → 不可恢复异常
            log.error("【Redis】参数非法 | method={} | error={}", methodName, e.getMessage());
            throw new RedisBusinessException(null, methodName, e);
        } catch (RedisOperationException e) {
            // 已经是内部异常，直接抛出
            throw e;
        } catch (Exception e) {
            // 未知异常 → 包装为业务异常
            log.error("【Redis】未知异常 | method={} | error={}", methodName, e.getMessage());
            throw new RedisBusinessException(null, methodName, e);
        }
    }

    /**
     * 从方法参数中提取 Key（取第一个非空字符串参数）
     *
     * @param args 方法参数数组
     * @return Key 值，未找到时返回 null
     */
    private String extractKey(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        // 通常第一个参数是 key
        Object firstArg = args[0];
        if (firstArg instanceof String key && !key.isEmpty()) {
            return key;
        }
        return null;
    }
}
