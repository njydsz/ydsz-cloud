package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.ApiMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * API 耗时监控切面（P2-2：API 响应时间 P99 监控）
 *
 * <p>对标注 {@link ApiMetrics} 的方法自动记录执行耗时，
 * 输出到 Prometheus Timer 和日志。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ApiMetricsAspect {

    private final MeterRegistry meterRegistry;

    @Around("@annotation(apiMetrics)")
    public Object around(ProceedingJoinPoint joinPoint, ApiMetrics apiMetrics) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String metricName = apiMetrics.value();
        if (metricName == null || metricName.isEmpty()) {
            metricName = signature.getDeclaringTypeName() + "." + signature.getName();
        }

        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long elapsed = System.nanoTime() - start;
            recordMetric(metricName, elapsed, "success");
            return result;
        } catch (Throwable e) {
            long elapsed = System.nanoTime() - start;
            recordMetric(metricName, elapsed, "error");
            throw e;
        }
    }

    private void recordMetric(String metricName, long elapsedNanos, String status) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        Timer.builder("api.request.duration")
                .tag("api", metricName)
                .tag("status", status)
                .description("API request duration in milliseconds")
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);

        if (elapsedMs > 1000) {
            log.warn("[ApiMetrics] 慢API: {} 耗时 {}ms (status={})", metricName, elapsedMs, status);
        }
    }
}