package com.njydsz.common.jdbc.interceptor;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import com.njydsz.common.jdbc.datasource.DynamicDataSourceContextHolder;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据库熔断器拦截器（MyBatis 外层拦截器）
 *
 * <p>基于 Resilience4j {@link CircuitBreaker} 实现，在每条 SQL 执行前检查熔断器状态，
 * 执行后根据结果记录成功或失败，在数据库连续异常时自动切断请求，避免线程池耗尽和级联故障。
 *
 * <p><b>按数据源分桶：</b>熔断器实例名称为 {@code db-<datasource>}，
 * 通过 {@link DynamicDataSourceContextHolder} 获取当前数据源名称，
 * 避免单个数据源故障导致所有数据源被一起熔断。
 *
 * <p>拦截范围：
 * <ul>
 *   <li>{@link Executor#query} — SELECT 查询</li>
 *   <li>{@link Executor#update} — INSERT/UPDATE/DELETE 写操作</li>
 * </ul>
 *
 * <p>异常分类（由配置的 {@code recordException} 谓词控制）：
 * <ul>
 *   <li>{@link SQLException} 及其子类 — 计为数据库故障，触发熔断计数</li>
 *   <li>由 SQLException 包装的 RuntimeException（如 Spring DataAccessException）— 同样计数</li>
 *   <li>其他 RuntimeException（业务异常） — 不计入熔断计数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CircuitBreaker
 */
@Slf4j
@Intercepts({
    @Signature(type = Executor.class, method = "query",
               args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query",
               args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
    @Signature(type = Executor.class, method = "update",
               args = {MappedStatement.class, Object.class})
})
public class CircuitBreakerInterceptor implements Interceptor {

    /** 熔断器名称前缀：{@code db-<datasource>} */
    private static final String BREAKER_NAME_PREFIX = "db-";

    /** 无显式数据源时的熔断器名称后缀 */
    private static final String DEFAULT_DATASOURCE = "default";

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * 构造数据库熔断器拦截器
     *
     * @param circuitBreakerRegistry Resilience4j 熔断器注册表
     */
    public CircuitBreakerInterceptor(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        CircuitBreaker circuitBreaker = resolveCircuitBreaker();
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("数据库熔断器[{}]处于 OPEN 状态，请求被拒绝", circuitBreaker.getName());
            throw new SQLException("Database circuit breaker is OPEN, request rejected");
        }

        long startNanos = System.nanoTime();
        try {
            Object result = invocation.proceed();
            circuitBreaker.onSuccess(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
            return result;
        } catch (Throwable t) {
            // onError 内部依据配置的 recordException 谓词判定是否计入失败（业务异常不计数）
            circuitBreaker.onError(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS, t);
            throw t;
        }
    }

    /**
     * 按当前数据源解析熔断器实例（同一数据源共享同一实例，实现故障隔离）。
     *
     * @return Resilience4j 熔断器实例
     */
    private CircuitBreaker resolveCircuitBreaker() {
        String datasource = DynamicDataSourceContextHolder.peek();
        if (datasource == null || datasource.isBlank()) {
            datasource = DEFAULT_DATASOURCE;
        }
        return circuitBreakerRegistry.circuitBreaker(BREAKER_NAME_PREFIX + datasource);
    }
}
