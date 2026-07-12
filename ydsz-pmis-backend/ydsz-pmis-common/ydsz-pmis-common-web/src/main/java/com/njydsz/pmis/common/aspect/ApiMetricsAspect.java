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

    /** Micrometer 指标注册中心，用于注册和记录 Timer 指标 */
    private final MeterRegistry meterRegistry;

    /**
     * 环绕增强：记录目标方法执行耗时，无论成功或失败均上报 Prometheus Timer 指标。
     *
     * <p>指标名称：注解未指定时回退为 {@code 类全限定名.方法名}。
     * 超过 1 秒的请求打印 WARN 级别慢日志。</p>
     *
     * @param joinPoint  连接点
     * @param apiMetrics API 指标注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
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

    /**
     * 记录 Prometheus Timer 指标并打印慢日志。
     *
     * <p>指标名：{@code api.request.duration}，Tag：{@code api=方法名, status=success/error}。
     * 超过 1000ms 的请求打印 WARN 日志。</p>
     *
     * @param metricName   指标名称（方法签名或自定义名称）
     * @param elapsedNanos 方法执行耗时（纳秒）
     * @param status       执行状态：{@code success} 或 {@code error}
     */
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