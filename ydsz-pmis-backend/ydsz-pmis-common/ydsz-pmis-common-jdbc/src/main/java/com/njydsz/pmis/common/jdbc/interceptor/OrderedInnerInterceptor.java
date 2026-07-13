package com.njydsz.pmis.common.jdbc.interceptor;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.core.Ordered;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;

/**
 * 带顺序的 {@link InnerInterceptor} 包装器
 *
 * <p>MyBatis-Plus 的 {@link com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor}
 * 默认按 {@code addInnerInterceptor} 顺序执行，不支持基于 {@link Ordered} 的动态排序。
 * 本包装器让任意 {@link InnerInterceptor} 具备 {@link Ordered} 能力，便于在自定义拦截器链
 * 排序、或在 {@link com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor}
 * 外部按优先级重新组织执行顺序。
 *
 * <p>典型用法：
 * <pre>{@code
 * MybatisPlusInterceptor mp = new MybatisPlusInterceptor();
 * mp.addInnerInterceptor(new OrderedInnerInterceptor(new SqlTraceInnerInterceptor(), Ordered.HIGHEST_PRECEDENCE + 100));
 * mp.addInnerInterceptor(new OrderedInnerInterceptor(new PaginationInnerInterceptor(), Ordered.LOWEST_PRECEDENCE - 100));
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see Ordered
 * @see InnerInterceptor
 */
public class OrderedInnerInterceptor implements InnerInterceptor, Ordered {

    private final InnerInterceptor delegate;
    private final int order;

    /**
     * 构造包装器，使用默认优先级 {@link Ordered#LOWEST_PRECEDENCE}
     *
     * @param delegate 被包装的拦截器
     */
    public OrderedInnerInterceptor(InnerInterceptor delegate) {
        this(delegate, Ordered.LOWEST_PRECEDENCE);
    }

    /**
     * 构造包装器
     *
     * @param delegate 被包装的拦截器
     * @param order    Spring 优先级，值越小越靠前
     */
    public OrderedInnerInterceptor(InnerInterceptor delegate, int order) {
        if (delegate == null) {
            throw new IllegalArgumentException("被包装的 InnerInterceptor 不能为空");
        }
        this.delegate = delegate;
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    public InnerInterceptor getDelegate() {
        return delegate;
    }

    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter,
                               RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        return delegate.willDoQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        delegate.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    public boolean willDoUpdate(Executor executor, MappedStatement ms, Object parameter) throws SQLException {
        return delegate.willDoUpdate(executor, ms, parameter);
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) throws SQLException {
        delegate.beforeUpdate(executor, ms, parameter);
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        delegate.beforePrepare(sh, connection, transactionTimeout);
    }
}
