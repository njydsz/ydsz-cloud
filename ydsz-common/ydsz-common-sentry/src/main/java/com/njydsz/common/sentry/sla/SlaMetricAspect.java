package com.njydsz.common.sentry.sla;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import com.njydsz.common.sentry.spi.SlaCollector;

/**
 * SLA 指标 AOP 切面
 *
 * <p>拦截 {@link SlaMetric} 和 {@link SlaStep} 注解，自动采集执行耗时。
 *
 * <p><b>与 Micrometer Observation 的协同</b>：
 * <ul>
 *   <li>本切面通过 AspectJ 实现，不依赖 Micrometer Observation API</li>
 *   <li>如果项目已使用 Micrometer Observation，推荐使用 {@code TimedAspect} + {@code ObservationConvention}
 *       实现方法级监控，本切面专注于步骤级 SLA 跟踪</li>
 *   <li>两者可共存：Micrometer Observation 采集方法级指标，本切面采集步骤级 SLA 违反</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.0.0
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
