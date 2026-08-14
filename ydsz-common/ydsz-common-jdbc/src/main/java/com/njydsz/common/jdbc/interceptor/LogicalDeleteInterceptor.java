package com.njydsz.common.jdbc.interceptor;

import java.sql.Connection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;


import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.jdbc.monitor.SqlAstCache;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.update.Update;

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
 * ydsz:
 *   jdbc:
 *     logical-delete:
 *       enable: true                       # 是否启用
 *       deleted-column: deleted           # 删除标记字段名
 *       deleted-value: 1                   # 已删除标记值
 *       normal-value: 0                   # 正常记录标记值
 *       ignore-tables: [sys_config]       # 忽略的表
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
 * @author ydsz-team
 * @since 1.0.0
 * @see OptimisticLockInterceptor 乐观锁拦截器
 * @see InnerInterceptor MyBatis-Plus 内部拦截器接口
 */
@Slf4j
@Getter
@EqualsAndHashCode(callSuper = true)
public class LogicalDeleteInterceptor extends CachingJsqlParserSupport implements InnerInterceptor {

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
     * 忽略逻辑删除拦截的表集合（小写化）
     */
    private Set<String> ignoreTables = Collections.emptySet();

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
     * 设置忽略逻辑删除拦截的表集合
     *
     * @param ignoreTables 忽略表集合（将被小写化存储）
     */
    public void setIgnoreTables(Set<String> ignoreTables) {
        if (ignoreTables == null || ignoreTables.isEmpty()) {
            this.ignoreTables = Collections.emptySet();
        } else {
            Set<String> normalized = new HashSet<>(ignoreTables.size());
            for (String table : ignoreTables) {
                if (table != null) {
                    normalized.add(table.trim().toLowerCase());
                }
            }
            this.ignoreTables = normalized;
        }
    }

    /**
     * 判断是否应该忽略该表
     *
     * @param tableName 表名
     * @return 在忽略列表中返回 true，否则返回 false
     */
    private boolean shouldIgnoreTable(String tableName) {
        if (tableName == null) {
            return true;
        }
        return ignoreTables.contains(tableName.toLowerCase());
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
            case INSERT:
                processInsertIntercept(sh);
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
            Statement statement = SqlAstCache.getInstance().parse(originalSql);
            if (!(statement instanceof Delete)) {
                throw new IllegalStateException(
                        "Expected DELETE statement but got: " + statement.getClass().getSimpleName());
            }
            String rewrittenSql = convertDeleteToLogicalUpdateSql((Delete) statement);
            mpBs.sql(rewrittenSql);
        } catch (JSQLParserException e) {
            log.error("LogicalDeleteInterceptor: Failed to parse DELETE SQL, aborting to prevent physical delete: {}", originalSql, e);
            throw SysException.builder()
                    .message("Failed to parse DELETE SQL, logical delete conversion aborted to prevent physical delete")
                    .cause(e)
                    .build();
        }
    }

    /**
     * 处理 INSERT 语句拦截
     *
     * <p>委托父类 {@link JsqlParserSupport#parserMulti(String, Object)} 解析 SQL，
     * 自动调用 {@link #processInsert(Insert, int, String, Object)} 完成列与值的补充，
     * 最后将改写后的 SQL 写回 BoundSql。
     *
     * @param sh StatementHandler 实例
     */
    private void processInsertIntercept(StatementHandler sh) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
        mpBs.sql(parserMulti(mpBs.sql(), null));
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

        // 处理主表（FROM）
        if (plainSelect.getFromItem() instanceof Table) {
            Table fromTable = (Table) plainSelect.getFromItem();
            if (!shouldIgnoreTable(fromTable.getName())) {
                Expression where = plainSelect.getWhere();
                Expression deletedCondition = new EqualsTo(
                        new Column(deletedColumn), new LongValue(normalValue));

                if (where == null) {
                    plainSelect.setWhere(deletedCondition);
                } else {
                    plainSelect.setWhere(new AndExpression(where, deletedCondition));
                }
            }
        } else if (plainSelect.getFromItem() instanceof ParenthesedSelect) {
            // 递归处理子查询 FROM
            ParenthesedSelect parenthesedSelect = (ParenthesedSelect) plainSelect.getFromItem();
            processSelectBody(parenthesedSelect.getSelect());
        }

        // 处理 JOIN 表
        List<Join> joins = plainSelect.getJoins();
        if (CollectionUtils.isNotEmpty(joins)) {
            for (Join join : joins) {
                if (join.getRightItem() instanceof Table) {
                    Table joinTable = (Table) join.getRightItem();
                    if (shouldIgnoreTable(joinTable.getName())) {
                        continue;
                    }
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
                } else if (join.getRightItem() instanceof ParenthesedSelect) {
                    // 递归处理 JOIN 子查询
                    ParenthesedSelect parenthesedSelect = (ParenthesedSelect) join.getRightItem();
                    processSelectBody(parenthesedSelect.getSelect());
                }
            }
        }

        log.debug("LogicalDeleteInterceptor: Added {}={} to SELECT WHERE clause and JOIN ON conditions", deletedColumn, normalValue);
    }

    /**
     * 处理 INSERT 语句 - 自动补充 deleted = normalValue 字面量
     *
     * <p>对 {@code INSERT INTO table (col1, col2) VALUES (v1, v2)} 形式的语句，
     * 若列声明中未显式包含 {@code deleted} 列，则自动追加该列与对应字面量值，
     * 避免因 DDL 未设置 DEFAULT 或实体未填充字段导致记录变为 NULL，
     * 进而被 {@code WHERE deleted = 0} 过滤掉的问题。
     *
     * <p><b>处理规则：</b>
     * <ul>
     *   <li>忽略表（{@link #shouldIgnoreTable(String)}）跳过</li>
     *   <li>无显式列声明（{@code INSERT INTO t VALUES (...)}）跳过，无法对齐列值位置</li>
     *   <li>已显式声明 deleted 列，跳过，保留业务侧明确语义</li>
     *   <li>仅处理 {@code INSERT ... VALUES} 单行形式，{@code INSERT ... SELECT}
     *       由 {@link #processSelect} 走 SELECT 链路递归处理</li>
     * </ul>
     *
     * <p>SQL 转换示例：
     * <pre>
     * // 原始
     * INSERT INTO rs_company (id, name) VALUES (1, 'test')
     *
     * // 转换后
     * INSERT INTO rs_company (id, name, deleted) VALUES (1, 'test', 0)
     * </pre>
     *
     * @param insert INSERT 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    附加对象
     */
    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        if (!enabled) {
            return;
        }

        Table table = insert.getTable();
        if (table == null || shouldIgnoreTable(table.getName())) {
            return;
        }

        // 获取显式列声明；为空表示 INSERT INTO ... VALUES (...)（无列名），
        // 此时无法安全地追加列值对应（位置不对齐），跳过让 DDL DEFAULT 兜底
        List<Column> columns = JSqlParserHelper.getInsertColumns(insert);
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }

        // 若已显式声明 deleted 列，不重复添加（避免覆盖业务侧明确语义）
        boolean hasDeleted = columns.stream()
                .anyMatch(col -> col.getColumnName() != null
                        && col.getColumnName().equalsIgnoreCase(deletedColumn));
        if (hasDeleted) {
            return;
        }

        // 仅处理 INSERT ... VALUES 形式；INSERT ... SELECT 由 processSelect 走 SELECT 链路
        Values values = insert.getValues();
        if (values == null) {
            return;
        }
        ExpressionList<?> expressionList = values.getExpressions();
        if (expressionList == null || expressionList.isEmpty()) {
            return;
        }

        // 追加 deleted 列到列声明
        columns.add(new Column(deletedColumn));

        // 重建 VALUES 列表，追加 normalValue 字面量
        // 注意：JSqlParser 将 VALUES (...),(...) 的多行插入解析为单个 ExpressionList，
        // 其中每个元素是 ExpressionList（子列表形式），而非平铺的表达式。
        // 这里仅处理所有元素均为 JdbcParameter/字面量的简单单行场景；
        // 对于批量 INSERT（多条 VALUES 行），需要为每行追加一个 normalValue。
        if (expressionList.size() == 1 && expressionList.get(0) instanceof ExpressionList) {
            // VALUES (...) 单行形式（JSqlParser 将整个 VALUES 后的括号视为一个 ExpressionList）
            ExpressionList<Expression> typedList = new ExpressionList<>();
            for (Object item : expressionList) {
                typedList.add(Expression.class.cast(item));
            }
            typedList.add(new LongValue(normalValue));
            values.setExpressions(typedList);
        } else {
            // VALUES (...), (...), ... 多行批量插入：每个表达式是一行的值列表
            // 将每个元素包装为新的 ExpressionList 并追加 normalValue
            ExpressionList<Expression> newExpressionList = new ExpressionList<>();
            for (Object rowObj : expressionList) {
                if (rowObj instanceof ExpressionList<?> rowList) {
                    ExpressionList<Expression> typedRow = new ExpressionList<>();
                    for (Object item : rowList) {
                        typedRow.add(Expression.class.cast(item));
                    }
                    typedRow.add(new LongValue(normalValue));
                    newExpressionList.add(typedRow);
                } else {
                    // 单行形式的兜底（所有表达式在同一层级）
                    ExpressionList<Expression> typedRow = new ExpressionList<>();
                    typedRow.add(Expression.class.cast(rowObj));
                    typedRow.add(new LongValue(normalValue));
                    newExpressionList.add(typedRow);
                }
            }
            values.setExpressions(newExpressionList);
        }

        log.debug("LogicalDeleteInterceptor: Added {}={} to INSERT VALUES for table {}",
                deletedColumn, normalValue, table.getName());
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
     * 处理 DELETE 语句（空实现）
     *
     * <p>DELETE 语句的转换由 {@link #processDeleteIntercept} 直接完成，
     * 该方法调用 {@link #convertDeleteToLogicalUpdateSql} 生成 UPDATE SQL。
     * 此方法仅为满足 JsqlParserSupport 抽象方法要求，不做任何处理。
     *
     * @param delete DELETE 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    附加对象
     */
    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        // DELETE→UPDATE 转换由 processDeleteIntercept 完成，此处无需处理
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

        if (select instanceof PlainSelect) {
            processPlainSelect((PlainSelect) select);
        } else if (select instanceof SetOperationList) {
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

        // 检查是否在忽略列表中
        if (shouldIgnoreTable(table.getName())) {
            // 忽略的表不转换，返回原始 DELETE SQL
            return delete.toString();
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
     * <p>在 Spring Boot 环境下，配置通过 @ConfigurationProperties + setter 注入，
     * 此方法仅为兼容 MyBatis 原生 XML 配置方式保留。
     *
     * @param properties 配置属性
     */
    @Override
    public void setProperties(Properties properties) {
        // Spring Boot 环境下配置通过 @ConfigurationProperties 注入，此方法不会被调用
    }
}
