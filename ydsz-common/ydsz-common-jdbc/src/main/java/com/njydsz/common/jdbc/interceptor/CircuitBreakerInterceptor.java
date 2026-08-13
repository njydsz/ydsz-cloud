package com.njydsz.common.jdbc.interceptor;

import java.sql.SQLException;

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
import com.njydsz.common.jdbc.monitor.DatabaseCircuitBreaker;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据库熔断器拦截器（MyBatis 外层拦截器）
 *
 * <p>基于自研 {@link DatabaseCircuitBreaker} 实现，在每条 SQL 执行前检查熔断器状态，
 * 执行后根据结果记录成功或失败，在数据库连续异常时自动切断请求，避免线程池耗尽和级联故障。
 *
 * <p><b>按数据源分桶：</b>通过 {@link DynamicDataSourceContextHolder} 获取当前数据源名称，
 * 每个数据源对应独立的熔断器实例，避免单个数据源故障导致所有数据源被一起熔断。
 *
 * <p>拦截范围：
 * <ul>
 *   <li>{@link Executor#query} — SELECT 查询</li>
 *   <li>{@link Executor#update} — INSERT/UPDATE/DELETE 写操作</li>
 * </ul>
 *
 * <p>异常分类：
 * <ul>
 *   <li>{@link SQLException} 及其子类 — 计为数据库故障，触发熔断计数</li>
 *   <li>由 SQLException 包装的 RuntimeException（如 Spring DataAccessException）— 同样计数</li>
 *   <li>其他 RuntimeException（业务异常） — 不计入熔断计数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DatabaseCircuitBreaker
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

    /** 无显式数据源时的熔断器标识 */
    private static final String DEFAULT_DATASOURCE = "default";

    private final DatabaseCircuitBreaker circuitBreaker;

    /**
     * 构造数据库熔断器拦截器
     *
     * @param circuitBreaker 数据库熔断器实例
     */
    public CircuitBreakerInterceptor(DatabaseCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!circuitBreaker.tryAcquire()) {
            String datasource = resolveCurrentDatasource();
            log.warn("数据库熔断器[db-{}]处于 OPEN 状态，请求被拒绝", datasource);
            throw new SQLException("Database circuit breaker is OPEN for datasource: " + datasource);
        }

        try {
            Object result = invocation.proceed();
            circuitBreaker.recordSuccess();
            return result;
        } catch (Throwable t) {
            if (isDatabaseException(t)) {
                circuitBreaker.recordFailure();
            }
            throw t;
        }
    }

    /**
     * 解析当前数据源名称
     *
     * @return 数据源名称，未设置时返回 "default"
     */
    private String resolveCurrentDatasource() {
        String datasource = DynamicDataSourceContextHolder.peek();
        if (datasource == null || datasource.isBlank()) {
            return DEFAULT_DATASOURCE;
        }
        return datasource;
    }

    /**
     * 判断异常是否为数据库异常
     *
     * <p>SQLException 及其包装的 RuntimeException 视为数据库故障；
     * 其他业务异常（如参数校验失败）不计入熔断计数。</p>
     *
     * @param t 异常对象
     * @return true 如果是数据库异常
     */
    private boolean isDatabaseException(Throwable t) {
        if (t instanceof SQLException) {
            return true;
        }
        Throwable cause = t.getCause();
        while (cause != null && cause != t) {
            if (cause instanceof SQLException) {
                return true;
            }
            t = cause;
            cause = cause.getCause();
        }
        return false;
    }
}
