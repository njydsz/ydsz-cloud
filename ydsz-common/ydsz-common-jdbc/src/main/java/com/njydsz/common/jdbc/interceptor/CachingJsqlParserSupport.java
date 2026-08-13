package com.njydsz.common.jdbc.interceptor;

import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.njydsz.common.jdbc.monitor.SqlAstCache;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

import lombok.extern.slf4j.Slf4j;

/**
 * 带缓存的 JSqlParserSupport 基类
 *
 * <p>MyBatis-Plus 的 {@link JsqlParserSupport#parserMulti} / {@link JsqlParserSupport#parserSingle}
 * 每次调用都会通过 {@code CCJSqlParserUtil.parse()} 重新解析 SQL。在拦截器链中，
 * 多个拦截器（逻辑删除、行权限、列权限）各自独立解析同一条 SQL，造成 N 倍解析开销。
 *
 * <p>本基类覆盖解析入口，通过 {@link SqlAstCache} 复用已解析的 AST：
 * <ul>
 *   <li>首次解析：通过缓存获取（未命中时自动解析并缓存）</li>
 *   <li>后续拦截器处理相同 SQL 模板时：直接从缓存返回深拷贝，零解析开销</li>
 *   <li>所有改写操作在深拷贝上进行，保证线程安全</li>
 * </ul>
 *
 * <p>使用方式：将拦截器继承的父类从 {@code JsqlParserSupport} 改为 {@code CachingJsqlParserSupport}，
 * 即可透明获得缓存能力，无需修改拦截器内部逻辑。
 *
 * <p>性能收益：假设 SQL 模板重复率 80%，拦截器链长度为 3，
 * 则总解析次数从 3N 降至 N + 2×0（缓存命中），理论提升 67%。
 *
 * @author ydsz-team
 * @since 1.8.0
 * @see SqlAstCache
 * @see JsqlParserSupport
 */
@Slf4j
public abstract class CachingJsqlParserSupport extends JsqlParserSupport {

    /**
     * 解析 SQL（带缓存），处理多语句批量场景。
     *
     * <p>覆盖父类实现，将解析步骤替换为缓存查找。缓存命中时返回 AST 深拷贝直接改写；
     * 缓存未命中时解析后存入缓存。后续拦截器处理相同 SQL 模板时零解析。
     *
     * @param sql 原始 SQL 语句
     * @param obj 传递对象（如数据权限上下文）
     * @return 改写后的 SQL 语句
     */
    @Override
    public String parserMulti(String sql, Object obj) {
        if (sql == null) {
            return null;
        }
        try {
            Statement statement = SqlAstCache.getInstance().parse(sql);
            return processStatement(statement, sql, obj);
        } catch (JSQLParserException e) {
            if (log.isDebugEnabled()) {
                log.debug("SQL 解析失败, 跳过改写: {}", e.getMessage());
            }
            return sql;
        }
    }

    /**
     * 解析 SQL（带缓存），处理单语句场景。
     *
     * <p>覆盖父类实现，与 {@link #parserMulti} 共享同一缓存。
     *
     * @param sql 原始 SQL 语句
     * @param obj 传递对象（如数据权限上下文）
     * @return 改写后的 SQL 语句
     */
    @Override
    public String parserSingle(String sql, Object obj) {
        if (sql == null) {
            return null;
        }
        try {
            Statement statement = SqlAstCache.getInstance().parse(sql);
            return processStatement(statement, sql, obj);
        } catch (JSQLParserException e) {
            if (log.isDebugEnabled()) {
                log.debug("SQL 解析失败, 跳过改写: {}", e.getMessage());
            }
            return sql;
        }
    }

    /**
     * 根据 AST 类型分发到对应的抽象处理方法。
     *
     * <p>此方法复现了 MyBatis-Plus {@code JsqlParserSupport} 的分发逻辑：
     * 根据 Statement 运行时类型调用对应的 {@code processXxx} 抽象方法，
     * 实现子类只需覆盖特定类型的处理逻辑即可。
     *
     * @param statement 解析后的 AST 深拷贝（可安全改写）
     * @param sql       原始 SQL 字符串（用于日志和调试）
     * @param obj       传递对象（如数据权限上下文）
     * @return 改写后的 SQL 字符串
     */
    protected String processStatement(Statement statement, String sql, Object obj) {
        if (statement instanceof Select) {
            processSelect((Select) statement, 0, sql, obj);
        } else if (statement instanceof Insert) {
            processInsert((Insert) statement, 0, sql, obj);
        } else if (statement instanceof Update) {
            processUpdate((Update) statement, 0, sql, obj);
        } else if (statement instanceof Delete) {
            processDelete((Delete) statement, 0, sql, obj);
        }
        return statement.toString();
    }
}
