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

import com.njydsz.common.jdbc.monitor.DatabaseCircuitBreaker;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据库熔断器拦截器（MyBatis 外层拦截器）
 *
 * <p>基于 MyBatis {@link Interceptor} 实现，在每条 SQL 执行前检查熔断器状态，
 * 执行后根据结果记录成功或失败。与 {@link DatabaseCircuitBreaker} 配合使用，
 * 在数据库连续异常时自动切断请求，避免线程池耗尽和级联故障。
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

/**
 * CircuitBreakerInterceptor 类。
 *
 * <p>所属包：{@code com.njydsz.common.jdbc.interceptor}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
})
public class CircuitBreakerInterceptor implements Interceptor {

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
            log.warn("数据库熔断器处于 OPEN 状态，请求被拒绝");
            throw new SQLException("Database circuit breaker is OPEN, request rejected");
        }

        try {
            Object result = invocation.proceed();
            circuitBreaker.recordSuccess();
            return result;
        } catch (Throwable t) {
            if (t instanceof SQLException) {
                circuitBreaker.recordFailure();
            } else if (t instanceof RuntimeException rt && isDatabaseException(rt)) {
                circuitBreaker.recordFailure();
            }
            throw t;
        }
    }

    /**
     * 判断异常链中是否包含 SQLException（如 Spring DataAccessException 包装的数据库异常）
     *
     * @param e 运行时异常
     * @return 异常链中包含 SQLException 时返回 true
     */
    private boolean isDatabaseException(RuntimeException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SQLException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
