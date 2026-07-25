package com.njydsz.common.sentry.sla;


import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import com.njydsz.common.sentry.spi.SlaCollector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SLA 指标 AOP 切面
 *
 * <p>拦截 {@link SlaMetric} 和 {@link SlaStep} 注解，自动采集执行耗时。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class SlaMetricAspect {

    private final SlaCollector slaCollector;

    /**
     * 拦截 @SlaMetric 注解
     */
    @Around("@annotation(slaMetric)")
    public Object aroundSlaMetric(ProceedingJoinPoint joinPoint, SlaMetric slaMetric) throws Throwable {
        long startMillis = System.currentTimeMillis();
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            success = false;
            throw e;
        } finally {
            long tookMillis = System.currentTimeMillis() - startMillis;
            String stepName = joinPoint.getSignature().getName();
            try {
                slaCollector.recordTotal(slaMetric.name(), tookMillis, success);
                slaCollector.record(slaMetric.name(), stepName, tookMillis, success);
            } catch (Exception e) {
                log.debug("[Sentry] SLA 采集异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 拦截 @SlaStep 注解
     */
    @Around("@annotation(slaStep)")
    public Object aroundSlaStep(ProceedingJoinPoint joinPoint, SlaStep slaStep) throws Throwable {
        long startMillis = System.currentTimeMillis();
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            success = false;
            throw e;
        } finally {
            long tookMillis = System.currentTimeMillis() - startMillis;
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            // 从类名推断 SLA 名称
            String slaName = signature.getDeclaringType().getSimpleName();
            try {
                slaCollector.record(slaName, slaStep.name(), tookMillis, success);
            } catch (Exception e) {
                log.debug("[Sentry] SLA Step 采集异常: {}", e.getMessage());
            }
        }
    }
}
