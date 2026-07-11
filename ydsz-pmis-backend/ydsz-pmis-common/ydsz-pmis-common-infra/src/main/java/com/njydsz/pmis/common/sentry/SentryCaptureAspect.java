package com.njydsz.pmis.common.sentry;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Sentry 上报切面
 *
 * 工作流:
 *   1. 拦截 @SentryCapture 注解的方法
 *   2. 捕获业务异常, 异步上报到 Sentry
 *   3. 重新抛出原异常 (不能改变业务行为)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(prefix = "pmis.sentry", name = "enabled", havingValue = "true")
public class SentryCaptureAspect {

    /**
     * 环绕拦截 {@link SentryCapture} 标记的方法，捕获异常后异步上报 Sentry，并重新抛出原异常
     *
     * @param pjp            AOP 连接点
     * @param sentryCapture  当前方法上的 {@link SentryCapture} 注解实例
     * @return 目标方法原始返回值
     * @throws Throwable 目标方法抛出的原始异常（不吞异常，仅上报后重新抛出）
     */
    @Around("@annotation(sentryCapture)")
    public Object around(ProceedingJoinPoint pjp, SentryCapture sentryCapture) throws Throwable {
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            try {
                report(pjp, sentryCapture, t);
            } catch (Exception e) {
                log.warn("[sentry-aspect] 上报失败, 降级到 log", e);
                log.error("[sentry-fallback] {} - {}: {}", sentryCapture.module(), sentryCapture.bizType(), t.getMessage(), t);
            }
            throw t;
        }
    }

    /**
     * 通过反射调用 Sentry SDK 上报异常；SDK 未引入或调用失败时降级为日志
     *
     * @param pjp           AOP 连接点
     * @param sentryCapture 当前方法上的 {@link SentryCapture} 注解实例
     * @param t             业务方法抛出的异常
     */
    private void report(ProceedingJoinPoint pjp, SentryCapture sentryCapture, Throwable t) {
        // 通过反射调用 Sentry SDK (避免硬依赖)
        try {
            Class<?> sentryClz = Class.forName("io.sentry.Sentry");
            Method captureExceptionMethod = sentryClz.getMethod("captureException", Throwable.class, Map.class);
            Map<String, Object> hint = new HashMap<>();
            hint.put("module", sentryCapture.module());
            hint.put("bizType", sentryCapture.bizType());
            hint.put("level", sentryCapture.level());
            hint.put("method", pjp.getSignature().toShortString());
            captureExceptionMethod.invoke(null, t, hint);
        } catch (ClassNotFoundException e) {
            // Sentry SDK 未引入, 走 log
            log.error("[sentry-not-installed] {}/{}: {}", sentryCapture.module(), sentryCapture.bizType(), t.getMessage(), t);
        } catch (Exception e) {
            log.error("[sentry-aspect-error]", e);
        }
    }
}
