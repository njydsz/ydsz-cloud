package com.njydsz.pmis.common.jdbc.interceptor;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.util.StringUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;

/**
 * 自定义逻辑删除拦截器 - 替代 MyBatis-Plus 的 @TableLogic 注解
 *
 * <p>功能说明：
 * <ul>
 *   <li>SELECT：自动追加 deleted = 0 条件，只查询未删除的记录（含 JOIN 表）</li>
 *   <li>DELETE：自动转换为 UPDATE SET deleted = 1，实现逻辑删除</li>
 * </ul>
 *
 * <p>逻辑删除工作原理：
 * <ul>
 *   <li>不真正删除数据，而是通过 deleted 字段标记记录状态</li>
 *   <li>deleted = 0 表示正常记录，deleted = 1 表示已删除</li>
 *   <li>所有查询自动过滤已删除记录，无需在每个查询中手动添加条件</li>
 * </ul>
 *
 * <p>SQL 转换示例：
 * <pre>
 * // 原始 SELECT 语句
 * SELECT * FROM rs_company WHERE id = 1
 *
 * // 转换后的 SELECT 语句（追加逻辑删除条件）
 * SELECT * FROM rs_company WHERE id = 1 AND deleted = 0
 *
 * // 原始 DELETE 语句
 * DELETE FROM rs_company WHERE id = 1
 *
 * // 转换后的 UPDATE 语句（自动将 DELETE 转为逻辑删除 UPDATE）
 * UPDATE rs_company SET deleted = 1 WHERE id = 1 AND deleted = 0
 * </pre>
 *
 * <p>配置说明：
 * <pre>
 * # application.yml
 * remi:
 *   sql-intercept:
 *     logical-delete:
 *       enable: true                       # 是否启用
 *       deleted-column: deleted           # 删除标记字段名
 *       deleted-value: 1                   # 已删除标记值
 *       normal-value: 0                   # 正常记录标记值
 * </pre>
 *
 * <p>注意事项：
 * <ul>
 *   <li>与 @TableLogic 注解互斥，启用本拦截器后应移除实体类的 @TableLogic 注解</li>
 *   <li>DELETE 语句自动转换为 UPDATE SET deleted = 1，无需业务层手动处理</li>
 *   <li>对于需要查询已删除记录的场景，可以提供单独的方法并在 SQL 中手动排除条件</li>
 *   <li>不支持多表删除（DELETE t1, t2 FROM ...），遇到时会抛出异常以防止物理删除</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 * @see OptimisticLockInterceptor 乐观锁拦截器
 * @see InnerInterceptor MyBatis-Plus 内部拦截器接口
 */
@Slf4j
@Getter
@EqualsAndHashCode(callSuper = true)
public class LogicalDeleteInterceptor extends JsqlParserSupport implements InnerInterceptor {

    /**
     * 默认的删除标记字段名
     */
    private static final String DEFAULT_DELETED_COLUMN = "deleted";

    /**
     * 删除标记字段名（可配置）
     */
    private String deletedColumn = DEFAULT_DELETED_COLUMN;

    /**
     * 已删除记录的标记值
     * <p>默认为 1，表示已删除
     */
    private Long deletedValue = 1L;

    /**
     * 正常记录的标记值
     * <p>默认为 0，表示正常记录
     */
    private Long normalValue = 0L;

    /**
     * 是否启用逻辑删除拦截
     * <p>设为 false 时，所有逻辑删除逻辑将被跳过
     */
    private boolean enabled = true;

    /**
     * 设置删除标记字段名
     *
     * @param deletedColumn 删除标记字段名
     */
    public void setDeletedColumn(String deletedColumn) {
        this.deletedColumn = deletedColumn;
    }

    /**
     * 设置已删除记录的标记值
     *
     * @param deletedValue 已删除标记值
     */
    public void setDeletedValue(Long deletedValue) {
        this.deletedValue = deletedValue;
    }

    /**
     * 设置正常记录的标记值
     *
     * @param normalValue 正常记录标记值
     */
    public void setNormalValue(Long normalValue) {
        this.normalValue = normalValue;
    }

    /**
     * SQL 执行前拦截处理
     *
     * <p>该方法在 SQL 执行前被调用，根据 SQL 类型决定是否需要处理逻辑删除：
     * <ul>
     *   <li>SELECT：添加 deleted = 0 条件，自动过滤已删除记录</li>
     *   <li>DELETE：转换为 UPDATE SET deleted = 1，实现逻辑删除</li>
     * </ul>
     *
     * @param sh                  StatementHandler 实例
     * @param connection          数据库连接
     * @param transactionTimeout  事务超时时间
     */
    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        if (!enabled) {
            return;
        }

        MappedStatement ms = getMappedStatement(sh);
        if (ms == null) {
            return;
        }

        SqlCommandType sct = ms.getSqlCommandType();
        if (sct == null) {
            return;
        }

        switch (sct) {
            case DELETE:
                processDeleteIntercept(sh);
                break;
            case SELECT:
                processSelectIntercept(sh);
                break;
            default:
                break;
        }
    }

    /**
     * 从 StatementHandler 获取 MappedStatement
     *
     * <p>通过 MyBatis 的反射机制获取当前 SQL 对应的 MappedStatement，
     * 以便判断 SQL 命令类型（INSERT/UPDATE/DELETE/SELECT）
     *
     * @param sh StatementHandler 实例
     * @return MappedStatement 对象，获取失败返回 null
     */
    private MappedStatement getMappedStatement(StatementHandler sh) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        return mpSh.mappedStatement();
    }

    /**
     * 执行 SQL 拦截处理
     *
     * <p>使用 JsqlParser 解析并修改 SQL 语句：
     * <ul>
     *   <li>获取当前的 SQL 语句</li>
     *   <li>通过 parserMulti 解析 SQL</li>
     *   <li>根据 SQL 类型调用对应的处理方法（processSelect/processDelete）</li>
     *   <li>将修改后的 SQL 重新设置到 BoundSql 中</li>
     * </ul>
     *
     * @param sh StatementHandler 实例
     */
    private void processSelectIntercept(StatementHandler sh) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
        mpBs.sql(parserMulti(mpBs.sql(), null));
    }

    private void processDeleteIntercept(StatementHandler sh) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
        String originalSql = mpBs.sql();
        try {
            Statement statement = CCJSqlParserUtil.parse(originalSql);
            if (!(statement instanceof Delete)) {
                throw new IllegalStateException(
                        "Expected DELETE statement but got: " + statement.getClass().getSimpleName());
            }
            String rewrittenSql = convertDeleteToLogicalUpdateSql((Delete) statement);
            mpBs.sql(rewrittenSql);
        } catch (JSQLParserException e) {
            log.error("LogicalDeleteInterceptor: Failed to parse DELETE SQL, aborting to prevent physical delete: {}", originalSql, e);
            throw new SysException(
                    "Failed to parse DELETE SQL, logical delete conversion aborted to prevent physical delete", e);
        }
    }

    /**
     * 处理 SELECT 语句 - 自动追加逻辑删除条件
     *
     * <p>在 SELECT 语句的 WHERE 条件中追加 deleted = 0 条件，
     * 确保查询结果只包含未删除的记录。
     *
     * <p>支持以下 SELECT 场景：
     * <ul>
     *   <li>PlainSelect：普通 SELECT 语句</li>
     *   <li>WithItem：公用表表达式（CTE）</li>
     *   <li>SetOperationList：UNION/UNION ALL 等集合操作</li>
     * </ul>
     *
     * <p>SQL 转换示例：
     * <pre>
     * // 原始
     * SELECT * FROM rs_company WHERE name LIKE '%test%'
     *
     * // 转换后
     * SELECT * FROM rs_company WHERE name LIKE '%test%' AND deleted = 0
     * </pre>
     *
     * @param select SELECT 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    附加对象
     */
    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        if (select instanceof PlainSelect) {
            processPlainSelect((PlainSelect) select);
        } else if (select instanceof SetOperationList) {
            SetOperationList operationList = (SetOperationList) select;
            List<Select> selects = operationList.getSelects();
            if (CollectionUtils.isNotEmpty(selects)) {
                selects.forEach(sb -> {
                    if (sb instanceof PlainSelect) {
                        processPlainSelect((PlainSelect) sb);
                    }
                });
            }
        }
    }

    /**
     * 处理普通 SELECT 语句 - 追加逻辑删除条件
     *
     * <p>将 deleted = 0 条件添加到 WHERE 子句和 JOIN ON 条件中：
     * <ul>
     *   <li>主表：在 WHERE 子句中追加 deleted = 0</li>
     *   <li>JOIN 表：在 ON 条件中追加 alias.deleted = 0，避免列名歧义</li>
     * </ul>
     *
     * @param plainSelect 普通 SELECT 语句对象
     */
    protected void processPlainSelect(PlainSelect plainSelect) {
        if (plainSelect == null) {
            return;
        }

        Expression where = plainSelect.getWhere();
        Expression deletedCondition = new EqualsTo(
                new Column(deletedColumn), new LongValue(normalValue));

        if (where == null) {
            plainSelect.setWhere(deletedCondition);
        } else {
            plainSelect.setWhere(new AndExpression(where, deletedCondition));
        }

        List<Join> joins = plainSelect.getJoins();
        if (CollectionUtils.isNotEmpty(joins)) {
            for (Join join : joins) {
                if (join.getRightItem() instanceof Table) {
                    Table joinTable = (Table) join.getRightItem();
                    Column joinDeletedColumn = buildAliasColumn(joinTable);
                    Expression joinDeletedCondition = new EqualsTo(
                            joinDeletedColumn, new LongValue(normalValue));
                    Expression onExpression = JSqlParserHelper.getJoinOnExpression(join);
                    if (onExpression == null) {
                        JSqlParserHelper.setJoinOnExpression(join, joinDeletedCondition);
                    } else {
                        JSqlParserHelper.setJoinOnExpression(join,
                                new AndExpression(onExpression, joinDeletedCondition));
                    }
                }
            }
        }

        log.debug("LogicalDeleteInterceptor: Added {}={} to SELECT WHERE clause and JOIN ON conditions", deletedColumn, normalValue);
    }

    /**
     * 处理 INSERT 语句（逻辑删除不涉及 INSERT，此方法为空）
     *
     * <p>保留此方法是因为父类 JsqlParserSupport 要求实现该抽象方法。
     * 实际上逻辑删除主要作用于 SELECT 和 DELETE，INSERT 操作不受影响。
     *
     * @param insert INSERT 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    附加对象
     */
    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
    }

    /**
     * 处理 UPDATE 语句（逻辑删除不涉及 UPDATE，此方法为空）
     *
     * <p>保留此方法是因为父类 JsqlParserSupport 要求实现该抽象方法。
     * UPDATE 操作通常不需要自动追加逻辑删除条件。
     *
     * @param update UPDATE 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    附加对象
     */
    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
    }

    /**
     * 处理 DELETE 语句 - 对 JsqlParser AST 进行安全校验并追加 deleted=0 兜底条件
     *
     * <p>该方法负责以下处理：
     * <ul>
     *   <li>校验 DELETE 语句的合法性（拒绝多表删除等不支持场景）</li>
     *   <li>在现有 WHERE 条件（如有）末尾追加 deleted = 0 条件作为安全兜底</li>
     * </ul>
     *
     * <p><b>注意：实际的 DELETE→UPDATE 转换由 {@link #processDeleteIntercept} 方法完成</b>，
     * 该方法从原始 SQL 字符串层面解析 DELETE 语句，调用 {@link #convertDeleteToLogicalUpdateSql}
     * 生成 UPDATE 语句，并将转换后的 SQL 设置回 BoundSql。本方法（processDelete）仅在
     * JsqlParser AST 层面追加安全条件，不负责 SQL 字符串的替换。
     *
     * <p>SQL 转换示例：
     * <pre>
     * // 原始 DELETE 语句
     * DELETE FROM rs_company WHERE id = 1
     *
     * // 最终生成的 UPDATE 语句（由 processDeleteIntercept 完成）
     * UPDATE rs_company SET deleted = 1 WHERE id = 1 AND deleted = 0
     * </pre>
     *
     * <p>不支持的场景（将抛出异常以防止物理删除）：
     * <ul>
     *   <li>多表删除（DELETE t1, t2 FROM ...）</li>
     *   <li>缺少表引用的 DELETE 语句</li>
     * </ul>
     *
     * @param delete DELETE 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    附加对象
     */
    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        if (delete == null) {
            return;
        }

        Table table = delete.getTable();
        if (table == null) {
            throw new IllegalStateException(
                    "Cannot convert DELETE to UPDATE: missing table reference, possibly multi-table delete");
        }

        if (CollectionUtils.isNotEmpty(delete.getTables())) {
            throw new IllegalStateException(
                    "Cannot convert multi-table DELETE to UPDATE: " + delete);
        }

        // 追加 deleted = 0 条件作为安全兜底，防止物理删除未标记为已删除的记录
        // 实际的 DELETE→UPDATE SQL 字符串转换由 processDeleteIntercept 方法完成
        Expression existingWhere = delete.getWhere();
        Expression deletedCondition = new EqualsTo(new Column(deletedColumn), new LongValue(normalValue));

        if (existingWhere == null) {
            delete.setWhere(deletedCondition);
        } else {
            delete.setWhere(new AndExpression(existingWhere, deletedCondition));
        }

        log.debug("LogicalDeleteInterceptor: Appended {}={} safety condition to DELETE WHERE clause",
                deletedColumn, normalValue);
    }

    /**
     * 处理 SELECT 语句体 - 递归处理复杂查询
     *
     * <p>该方法用于处理以下情况:
     * <ul>
     *   <li>SetOperationList:UNION/UNION ALL 等集合操作</li>
     * </ul>
     *
     * @param select SELECT 语句
     */
    protected void processSelectBody(Select select) {
        if (select == null) {
            return;
        }

        if (select instanceof SetOperationList) {
            SetOperationList operationList = (SetOperationList) select;
            List<Select> selects = operationList.getSelects();
            if (CollectionUtils.isNotEmpty(selects)) {
                selects.forEach(this::processSelectBody);
            }
        }
    }

    /**
     * 构建带表别名的删除标记列
     *
     * <p>如果表有别名，则返回 alias.deletedColumn 格式，否则返回 deletedColumn。
     * 用于在 JOIN 场景中避免列名歧义。
     *
     * @param table 表对象
     * @return 带别名的列对象
     */
    private Column buildAliasColumn(Table table) {
        if (table.getAlias() != null) {
            return new Column(table.getAlias().getName() + "." + deletedColumn);
        }
        return new Column(deletedColumn);
    }

    /**
     * 将 DELETE 语句转换为 UPDATE 语句（工具方法）
     *
     * <p>基于 JSqlParser AST 将 DELETE 转换为逻辑删除 UPDATE，
     * 避免正则解析的 SQL 注入风险和复杂语句匹配失败问题。
     *
     * <p>SQL 转换示例：
     * <pre>
     * // 原始
     * DELETE FROM rs_company WHERE id = 1
     *
     * // 转换后
     * UPDATE rs_company SET deleted = 1 WHERE id = 1 AND deleted = 0
     * </pre>
     *
     * <p>不支持的场景（将抛出异常以防止物理删除）：
     * <ul>
     *   <li>多表删除（DELETE t1, t2 FROM ...）</li>
     *   <li>缺少表引用的 DELETE 语句</li>
     * </ul>
     *
     * @param delete JSqlParser 解析后的 Delete 对象
     * @return 转换后的 UPDATE SQL 字符串
     * @throws IllegalStateException 当 DELETE 语句无法安全转换为 UPDATE 时
     */
    public String convertDeleteToLogicalUpdateSql(Delete delete) {
        if (delete == null) {
            throw new IllegalArgumentException("Delete statement cannot be null");
        }

        Table table = delete.getTable();
        if (table == null) {
            throw new IllegalStateException(
                    "Cannot convert DELETE to UPDATE: missing table reference, possibly multi-table delete");
        }

        if (CollectionUtils.isNotEmpty(delete.getTables())) {
            throw new IllegalStateException(
                    "Cannot convert multi-table DELETE to UPDATE: " + delete);
        }

        Update update = new Update();
        update.setTable(table);
        update.addUpdateSet(new Column(deletedColumn), new LongValue(deletedValue));

        Expression where = delete.getWhere();
        Expression deletedCondition = new EqualsTo(new Column(deletedColumn), new LongValue(normalValue));

        if (where == null) {
            update.setWhere(deletedCondition);
        } else {
            update.setWhere(new AndExpression(where, deletedCondition));
        }

        return update.toString();
    }

    /**
     * 设置拦截器配置属性
     *
     * <p>该方法由 Spring 在初始化拦截器时自动调用，
     * 将配置文件中的属性值绑定到拦截器的成员变量上。
     *
     * <p>支持的配置项：
     * <ul>
     *   <li>deletedColumn：删除标记字段名，默认 "deleted"</li>
     *   <li>deletedValue：已删除标记值，默认 1</li>
     *   <li>normalValue：正常记录标记值，默认 0</li>
     *   <li>enabled：是否启用，默认 true</li>
     * </ul>
     *
     * @param properties 配置属性
     */
    @Override
    public void setProperties(Properties properties) {
        if (properties == null) {
            return;
        }

        String column = properties.getProperty("deletedColumn");
        if (StringUtils.isNotBlank(column)) {
            this.deletedColumn = column;
        }

        String deletedVal = properties.getProperty("deletedValue");
        if (StringUtils.isNotBlank(deletedVal)) {
            try {
                this.deletedValue = Long.parseLong(deletedVal);
            } catch (NumberFormatException e) {
                log.warn("Invalid deletedValue: {}, using default: 1", deletedVal);
            }
        }

        String normalVal = properties.getProperty("normalValue");
        if (StringUtils.isNotBlank(normalVal)) {
            try {
                this.normalValue = Long.parseLong(normalVal);
            } catch (NumberFormatException e) {
                log.warn("Invalid normalValue: {}, using default: 0", normalVal);
            }
        }

        String enabledStr = properties.getProperty("enabled", "true");
        this.enabled = Boolean.parseBoolean(enabledStr);

        log.info("LogicalDeleteInterceptor initialized: deletedColumn={}, deletedValue={}, normalValue={}, enabled={}",
                deletedColumn, deletedValue, normalValue, enabled);
    }
}
