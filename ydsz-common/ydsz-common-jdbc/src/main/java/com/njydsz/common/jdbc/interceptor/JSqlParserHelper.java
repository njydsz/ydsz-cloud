package com.njydsz.common.jdbc.interceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

/**
 * JSqlParser 辅助工具类
 *
 * <p>提供 JSqlParser AST 节点的操作方法封装，简化常见的字段访问和修改逻辑。
 * 所有方法直接委托给 JSqlParser 原生 API，确保与最新版本兼容。</p>
 *
 * <h2>提供的功能</h2>
 * <ul>
 *   <li>INSERT 语句列操作</li>
 *   <li>UPDATE 语句列和值操作（使用 getUpdateSets）</li>
 *   <li>JOIN 语句 ON 表达式操作</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JSqlParserHelper {

    /**
     * 私有构造方法，工具类禁止实例化。
     */
    private JSqlParserHelper() {
    }

    /**
     * 解析 SQL 语句并返回 AST，利用 {@link SqlParseContext} 线程级缓存避免重复解析。
     *
     * <p>在 MyBatis-Plus 拦截器链中，多个 InnerInterceptor 会对同一条 SQL 进行多次解析。
     * 本方法通过 {@link SqlParseContext} 在同线程内缓存最新一条 SQL 的解析结果，
     * 后续拦截器对同一 SQL 字符串调用时直接返回缓存的 AST，消除重复解析的 CPU 开销。</p>
     *
     * <p>使用示例：</p>
     * <pre>
     * // 替代直接调用 CCJSqlParserUtil.parse(sql)
     * Statement stmt = JSqlParserHelper.parseWithCached(sql);
     * </pre>
     *
     * <p><b>注意：</b>返回的 Statement 为缓存引用，仅应用于瞬时读取场景。
     * 如需持有 AST 至下一次 SQL 调用，请自行深拷贝。</p>
     *
     * @param sql 原始 SQL 语句字符串
     * @return 解析后的 JSqlParser Statement AST 对象
     * @throws net.sf.jsqlparser.JSQLParserException 当 SQL 无法解析时抛出
     * @see SqlParseContext#parse(String)
     * @see SqlParseContext#clear()
     */
    public static Statement parseWithCached(String sql) throws net.sf.jsqlparser.JSQLParserException {
        return SqlParseContext.parse(sql);
    }

    /**
     * 获取 INSERT 语句的列列表
     *
     * @param insert INSERT 语句对象
     * @return 列列表
     */
    public static List<Column> getInsertColumns(Insert insert) {
        return insert.getColumns();
    }

    /**
     * 获取 UPDATE 语句的列列表（使用 UpdateSets API）
     *
     * <p>这是 JSqlParser 4.7+ 推荐的方式，通过 getUpdateSets() 获取列和值。</p>
     *
     * @param update UPDATE 语句对象
     * @return 列列表
     */
    public static List<Column> getUpdateSetsColumns(Update update) {
        List<UpdateSet> updateSets = update.getUpdateSets();
        if (updateSets == null || updateSets.isEmpty()) {
            return new ArrayList<>();
        }
        List<Column> columns = new ArrayList<>();
        for (UpdateSet updateSet : updateSets) {
            List<Column> setColumns = updateSet.getColumns();
            if (setColumns != null) {
                columns.addAll(setColumns);
            }
        }
        return columns;
    }

    /**
     * 获取 UPDATE 语句的值表达式列表（使用 UpdateSets API）
     *
     * <p>这是 JSqlParser 4.7+ 推荐的方式，通过 getUpdateSets() 获取列和值。
     * 注意：ExpressionList 直接继承 List，因此直接返回 ExpressionList 本身。</p>
     *
     * @param update UPDATE 语句对象
     * @return 值表达式列表
     */

    public static List<Expression> getUpdateSetsExpressions(Update update) {
        List<UpdateSet> updateSets = update.getUpdateSets();
        if (updateSets == null || updateSets.isEmpty()) {
            return new ArrayList<>();
        }
        List<Expression> expressions = new ArrayList<>();
        for (UpdateSet updateSet : updateSets) {
            ExpressionList<?> valueList = updateSet.getValues();
            if (valueList != null) {
                for (Object item : valueList) {
                    expressions.add(Expression.class.cast(item));
                }
            }
        }
        return expressions;
    }

    /**
     * 设置 JOIN 语句的 ON 表达式列表
     *
     * <p>这是 JSqlParser 4.7+ 推荐的方式，使用列表存储多个 ON 表达式。</p>
     *
     * @param join JOIN 语句对象
     * @param expressions ON 表达式列表
     */

    public static void setJoinOnExpressions(Join join, List<Expression> expressions) {
        join.setOnExpressions(expressions);
    }

    /**
     * 获取 JOIN 语句的 ON 表达式列表
     *
     * <p>这是 JSqlParser 4.7+ 推荐的方式，使用列表存储多个 ON 表达式。</p>
     *
     * @param join JOIN 语句对象
     * @return ON 表达式列表
     */

    public static List<Expression> getJoinOnExpressions(Join join) {
        Collection<Expression> onExpressions = join.getOnExpressions();
        return onExpressions != null ? new ArrayList<>(onExpressions) : new ArrayList<>();
    }

    /**
     * 设置 JOIN 语句的 ON 表达式
     *
     * <p>兼容性别名方法，如果只有一个表达式，设置到列表的第一个位置。</p>
     *
     * @param join JOIN 语句对象
     * @param expr ON 表达式
     */
    public static void setJoinOnExpression(Join join, Expression expr) {
        List<Expression> expressions = new ArrayList<>();
        expressions.add(expr);
        setJoinOnExpressions(join, expressions);
    }

    /**
     * 获取 JOIN 语句的第一个 ON 表达式
     *
     * <p>兼容性别名方法，返回列表中的第一个表达式。</p>
     *
     * @param join JOIN 语句对象
     * @return 第一个 ON 表达式，如果没有则返回 null
     */
    public static Expression getJoinOnExpression(Join join) {
        List<Expression> expressions = getJoinOnExpressions(join);
        if (expressions == null || expressions.isEmpty()) {
            return null;
        }
        return expressions.get(0);
    }
}
