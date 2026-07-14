package com.njydsz.pmis.common.jdbc.interceptor;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

/**
 * 自定义乐观锁拦截器 - 替代 MyBatis-Plus 的 @Version 注解
 *
 * <p>功能说明：
 * <ul>
 *   <li>INSERT：自动为 revision 字段设置默认值（通常为 0）</li>
 *   <li>UPDATE：自动追加 revision 乐观锁条件，防止并发更新覆盖</li>
 * </ul>
 *
 * <p>乐观锁工作原理：
 * <ul>
 *   <li>更新时在 WHERE 条件中添加 revision = ? 条件，? 值为传入的 revision 值</li>
 *   <li>同时在 SET 列表中添加 revision = revision + 1，实现版本号递增</li>
 *   <li>如果数据库中 revision 与传入值不匹配，则更新 0 条记录，程序可据此抛出乐观锁异常</li>
 * </ul>
 *
 * <p>SQL 转换示例：
 * <pre>
 * // 原始 UPDATE 语句
 * UPDATE rs_company SET name = ? WHERE id = ?
 *
 * // 转换后的 UPDATE 语句（追加乐观锁）
 * UPDATE rs_company SET name = ?, revision = revision + 1 WHERE id = ? AND revision = ?
 * </pre>
 *
 * <p>配置说明：
 * <pre>
 * # application.yml
 * ydsz:
 *   sql-intercept:
 *     optimistic-lock:
 *       enable: true                           # 是否启用
 *       revision-column: revision              # 版本号字段名
 *       default-revision-value: 0               # INSERT 时的默认值
 * </pre>
 *
 * <p>注意事项：
 * <ul>
 *   <li>与 @Version 注解互斥，启用本拦截器后应移除实体类的 @Version 注解</li>
 *   <li>UPDATE 时需要确保传入实体的 revision 字段有正确的值（非 null）</li>
 *   <li>建议在业务层捕获更新 0 条记录的情况，抛出乐观锁冲突异常</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 * @see LogicalDeleteInterceptor 逻辑删除拦截器
 * @see InnerInterceptor MyBatis-Plus 内部拦截器接口
 */
@Slf4j
@Getter
@EqualsAndHashCode(callSuper = true)
public class OptimisticLockInterceptor extends JsqlParserSupport implements InnerInterceptor {

    /**
     * 默认的版本号字段名
     */
    private static final String DEFAULT_REVISION_COLUMN = "revision";

    /**
     * 版本号字段名（可配置）
     */
    private String revisionColumn = DEFAULT_REVISION_COLUMN;

    /**
     * INSERT 时 revision 的默认值
     * <p>通常设为 0 或 1，表示初始版本
     */
    private Long defaultRevisionValue = 0L;

    /**
     * 是否启用乐观锁拦截
     * <p>设为 false 时，所有乐观锁逻辑将被跳过
     */
    private boolean enabled = true;

    /**
     * 设置版本号字段名
     *
     * @param revisionColumn 版本号字段名
     */
    public void setRevisionColumn(String revisionColumn) {
        this.revisionColumn = revisionColumn;
    }

    /**
     * 设置 INSERT 时 revision 的默认值
     *
     * @param defaultRevisionValue 默认值
     */
    public void setDefaultRevisionValue(Long defaultRevisionValue) {
        this.defaultRevisionValue = defaultRevisionValue;
    }

    /**
     * SQL 执行前拦截处理
     *
     * <p>该方法在 SQL 执行前被调用，根据 SQL 类型决定是否需要处理乐观锁：
     * <ul>
     *   <li>INSERT：添加 revision 默认值</li>
     *   <li>UPDATE：添加乐观锁条件和版本递增</li>
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
            case INSERT:
            case UPDATE:
                if (!isBaseEntityParameter(ms)) {
                    log.debug("OptimisticLockInterceptor: 跳过非 BaseEntity 类型参数的 SQL 处理, commandType={}", sct);
                    return;
                }
                processIntercept(sh);
                break;
            default:
                break;
        }
    }

    /**
     * 检查参数对象是否为 BaseEntity 类型
     *
     * @param ms MappedStatement 实例
     * @return 如果参数是 BaseEntity 类型返回 true
     */
    private boolean isBaseEntityParameter(MappedStatement ms) {
        try {
            Class<?> parameterType = ms.getParameterMap().getType();
            if (parameterType == null) {
                return false;
            }
            Class<?> current = parameterType;
            while (current != null) {
                if ("com.njydsz.pmis.common.domain.entity.BaseEntity".equals(current.getName())) {
                    return true;
                }
                current = current.getSuperclass();
            }
            for (Class<?> iface : parameterType.getInterfaces()) {
                if (isBaseEntityInterface(iface)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("OptimisticLockInterceptor: 获取参数类型失败，跳过乐观锁处理, error={}", e.getMessage());
            return false;
        }
    }

    private boolean isBaseEntityInterface(Class<?> iface) {
        if ("com.njydsz.pmis.common.domain.entity.BaseEntity".equals(iface.getName())) {
            return true;
        }
        for (Class<?> parent : iface.getInterfaces()) {
            if (isBaseEntityInterface(parent)) {
                return true;
            }
        }
        return false;
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
     *   <li>根据 SQL 类型调用对应的处理方法（processInsert/processUpdate）</li>
     *   <li>将修改后的 SQL 重新设置到 BoundSql 中</li>
     * </ul>
     *
     * @param sh StatementHandler 实例
     */
    private void processIntercept(StatementHandler sh) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
        mpBs.sql(parserMulti(mpBs.sql(), null));
    }

    /**
     * 处理 SELECT 语句（乐观锁不涉及 SELECT，此方法为空）
     *
     * @param select SELECT 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    附加对象
     */
    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
    }

    /**
     * 处理 INSERT 语句 - 添加 revision 默认值
     *
     * <p>在 INSERT 语句的字段列表和值列表中追加 revision 字段和默认值：
     * <ul>
     *   <li>检查是否已存在 revision 字段，避免重复添加</li>
     *   <li>支持单条插入（ExpressionList）和批量插入（MultiExpressionList）</li>
     * </ul>
     *
     * <p>SQL 转换示例：
     * <pre>
     * // 原始
     * INSERT INTO rs_company (name) VALUES ('test')
     *
     * // 转换后
     * INSERT INTO rs_company (name, revision) VALUES ('test', 0)
     * </pre>
     *
     * @param insert INSERT 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    附加对象
     */
    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        List<Column> columns = JSqlParserHelper.getInsertColumns(insert);
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }

        boolean hasRevision = columns.stream()
                .anyMatch(col -> col.getColumnName().equalsIgnoreCase(revisionColumn));
        if (hasRevision) {
            return;
        }

        columns.add(new Column(revisionColumn));

        Values values = insert.getValues();
        if (values != null) {
            ExpressionList<?> expressionList = values.getExpressions();
            if (expressionList != null) {
                ExpressionList<Expression> typedList = new ExpressionList<>();
                for (Object item : expressionList) {
                    typedList.add(Expression.class.cast(item));
                }
                typedList.add(new LongValue(defaultRevisionValue));
                values.setExpressions(typedList);
            }
        }

        log.debug("OptimisticLockInterceptor: Added {}={} to INSERT statement", revisionColumn, defaultRevisionValue);
    }

    /**
     * 处理 UPDATE 语句 - 添加乐观锁条件和版本递增
     *
     * <p>对 UPDATE 语句进行两处修改：
     * <ol>
     *   <li>在 SET 列表中追加 revision = revision + 1（版本号递增）</li>
     *   <li>在 WHERE 条件中追加 revision = ?（乐观锁条件）</li>
     * </ol>
     *
     * <p>SQL 转换示例：
     * <pre>
     * // 原始
     * UPDATE rs_company SET name = 'new_name' WHERE id = 1
     *
     * // 转换后
     * UPDATE rs_company SET name = 'new_name', revision = revision + 1
     * WHERE id = 1 AND revision = ?
     * </pre>
     *
     * <p>注意事项：
     * <ul>
     *   <li>revision = -1 是占位符，实际值由 MyBatis 参数绑定提供</li>
     *   <li>如果数据库中 revision 值与传入的不匹配，更新将影响 0 条记录</li>
     *   <li>业务层应根据更新结果判断是否发生了乐观锁冲突</li>
     * </ul>
     *
     * @param update UPDATE 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    附加对象
     */
    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        List<Column> columns = JSqlParserHelper.getUpdateSetsColumns(update);
        List<Expression> expressions = JSqlParserHelper.getUpdateSetsExpressions(update);

        Expression originalRevisionValue = null;
        if (CollectionUtils.isNotEmpty(columns) && CollectionUtils.isNotEmpty(expressions)) {
            for (int i = 0; i < columns.size(); i++) {
                if (isRevisionColumn(columns.get(i))) {
                    originalRevisionValue = expressions.get(i);
                    columns.remove(i);
                    expressions.remove(i);
                    break;
                }
            }
        }

        if (originalRevisionValue == null) {
            List<UpdateSet> updateSets = update.getUpdateSets();
            if (CollectionUtils.isNotEmpty(updateSets)) {
                for (int setIndex = 0; setIndex < updateSets.size(); setIndex++) {
                    UpdateSet updateSet = updateSets.get(setIndex);
                    List<Column> setColumns = updateSet.getColumns();
                    ExpressionList<?> expressionList = updateSet.getValues();
                    if (CollectionUtils.isEmpty(setColumns) || expressionList == null) {
                        continue;
                    }
                    if (expressionList.isEmpty()) {
                        continue;
                    }
                    for (int columnIndex = 0; columnIndex < setColumns.size(); columnIndex++) {
                        if (isRevisionColumn(setColumns.get(columnIndex))) {
                            originalRevisionValue = Expression.class.cast(expressionList.get(columnIndex));
                            setColumns.remove(columnIndex);
                            expressionList.remove(columnIndex);
                            if (CollectionUtils.isEmpty(setColumns) || expressionList.isEmpty()) {
                                updateSets.remove(setIndex);
                            }
                            break;
                        }
                    }
                    if (originalRevisionValue != null) {
                        break;
                    }
                }
            }
        }

        if (originalRevisionValue == null) {
            return;
        }

        Addition addition = new Addition();
        addition.setLeftExpression(new Column(revisionColumn));
        addition.setRightExpression(new LongValue(1));
        update.addUpdateSet(new Column(revisionColumn), addition);
        log.debug("OptimisticLockInterceptor: Replaced revision with increment in UPDATE statement");

        Expression where = update.getWhere();
        EqualsTo equalsTo = new EqualsTo(new Column(revisionColumn), originalRevisionValue);

        if (where == null) {
            update.setWhere(equalsTo);
        } else {
            update.setWhere(new AndExpression(equalsTo, where));
        }

        log.debug("OptimisticLockInterceptor: Added revision check to UPDATE WHERE clause");
    }

    private boolean isRevisionColumn(Column column) {
        if (column == null || StringUtils.isBlank(revisionColumn)) {
            return false;
        }
        String columnName = column.getColumnName();
        if (StringUtils.isBlank(columnName)) {
            columnName = column.getFullyQualifiedName();
        }
        if (StringUtils.isBlank(columnName)) {
            return false;
        }
        String normalized = columnName.replace("`", "");
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex >= 0) {
            normalized = normalized.substring(dotIndex + 1);
        }
        return normalized.equalsIgnoreCase(revisionColumn);
    }

    /**
     * 处理 DELETE 语句（乐观锁不涉及 DELETE，此方法为空）
     *
     * <p>保留此方法是因为父类 JsqlParserSupport 要求实现该抽象方法。
     * 实际上乐观锁主要作用于 INSERT 和 UPDATE，DELETE 操作通常不受影响。
     *
     * @param delete DELETE 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    附加对象
     */
    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
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
     * 设置拦截器配置属性
     *
     * <p>该方法由 Spring 在初始化拦截器时自动调用，
     * 将配置文件中的属性值绑定到拦截器的成员变量上。
     *
     * <p>支持的配置项：
     * <ul>
     *   <li>revisionColumn：版本号字段名，默认 "revision"</li>
     *   <li>defaultRevisionValue：INSERT 时的默认值，默认 0</li>
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

        String column = properties.getProperty("revisionColumn");
        if (StringUtils.isNotBlank(column)) {
            this.revisionColumn = column;
        }

        String defaultVal = properties.getProperty("defaultRevisionValue");
        if (StringUtils.isNotBlank(defaultVal)) {
            try {
                this.defaultRevisionValue = Long.parseLong(defaultVal);
            } catch (NumberFormatException e) {
                log.warn("Invalid defaultRevisionValue: {}, using default: 0", defaultVal);
            }
        }

        String enabledStr = properties.getProperty("enabled", "true");
        this.enabled = Boolean.parseBoolean(enabledStr);

        log.info("OptimisticLockInterceptor initialized: revisionColumn={}, defaultRevisionValue={}, enabled={}",
                revisionColumn, defaultRevisionValue, enabled);
    }
}
