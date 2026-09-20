package com.njydsz.common.seata.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Seata 分布式事务 Metrics 切面。
 *
 * <p>通过 AOP 拦截所有标注 {@code @YdszGlobalTransactional} 的方法，记录：
 * <ul>
 *   <li>{@code seata.tx.count} — 事务执行计数（按 result 标签区分 success/异常类型）</li>
 *   <li>{@code seata.tx.duration} — 事务执行耗时（含 P50/P90/P99 分位数直方图）</li>
 * </ul>
 *
 * <p>规范 §25.7 强制要求暴露 {@code seata.tx.count} 和 {@code seata.tx.duration} 监控指标。
 *
 * <p><b>前置条件：</b>
 * <ul>
 *   <li>classpath 中存在 Micrometer {@link MeterRegistry}</li>
 *   <li>项目已引入 spring-boot-starter-aop</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SeataTransactionMetricsAspect {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(SeataTransactionMetricsAspect.class);

    /** Micrometer 指标注册器 */
    private final MeterRegistry meterRegistry;

    /**
     * 构造 Metrics 切面。
     *
     * @param meterRegistry Micrometer 指标注册器
     */
    public SeataTransactionMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 拦截 YdszGlobalTransactional 方法，记录事务耗时和结果。
     *
     * @param pjp 连接点
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(com.njydsz.common.seata.annotation.YdszGlobalTransactional)")
    public Object measureTransaction(ProceedingJoinPoint pjp) throws Throwable {
        String methodName = resolveMethodName(pjp);

        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "success";

        try {
            return pjp.proceed();
        } catch (Throwable e) {
            result = e.getClass().getSimpleName();
            incrementCounter(methodName, result);
            throw e;
        } finally {
            sample.stop(buildTimer(methodName));
        }
    }

    /**
     * 解析方法标识（类名 + 方法名）。
     */
    private String resolveMethodName(ProceedingJoinPoint pjp) {
        return pjp.getSignature().getDeclaringType().getSimpleName()
            + "#" + pjp.getSignature().getName();
    }

    /**
     * 记录事务计数。
     */
    private void incrementCounter(String methodName, String result) {
        try {
            meterRegistry.counter("seata.tx.count",
                Tags.of("method", methodName, "result", result))
                .increment();
        } catch (Exception e) {
            LOG.debug("Failed to increment seata.tx.count: {}", e.getMessage());
        }
    }

    /**
     * 构建 Timer 并记录耗时。
     */
    private Timer buildTimer(String methodName) {
        return Timer.builder("seata.tx.duration")
            .tags(Tags.of("method", methodName))
            .publishPercentiles(0.5, 0.9, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);
    }
}
