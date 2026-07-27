package com.njydsz.common.tenant.interceptor;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.common.jdbc.interceptor.JSqlParserHelper;
import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.tenant.config.TenantProperties.TenantField;
import com.njydsz.common.tenant.config.TenantProperties.TenantSource;
import com.njydsz.common.jdbc.exception.TenantIsolationException;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;

/**
 * 多租户隔离拦截器。
 *
 * <p>基于 MyBatis-Plus InnerInterceptor 实现，通过 JSqlParser SQL 改写
 * 自动在 SQL 语句中注入租户条件，实现数据行级别的租户隔离。
 *
 * <p>支持单租户模式（SINGLE）和多级租户模式（MULTI）：
 * <ul>
 *   <li>SINGLE：只注入第一个字段，SQL 效果 {@code WHERE tenant_id = ?}</li>
 *   <li>MULTI：注入全部字段，SQL 效果 {@code WHERE group_tenant_id = ? AND company_tenant_id = ?}</li>
 * </ul>
 *
 * <p>支持 per-table 列名覆盖：通过 {@link TenantProperties#getTableColumnMapping()}
 * 或 {@code @TenantColumn} 注解自定义列名。
 *
 * <p><b>fail-closed 原则：</b>当无法获取租户上下文时抛出 {@link TenantIsolationException}
 * 拒绝执行 SQL，避免数据泄露。
 *
 * <p>此拦截器通过 {@link TenantInterceptorProvider} SPI 注册到
 * {@code MybatisPlusInterceptor} 链中（order=400）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see TenantProperties
 * @see TenantContextHolder
 * @see TenantInterceptorProvider
 */
@Slf4j
public class TenantIsolationInterceptor extends JsqlParserSupport implements InnerInterceptor {

    private final TenantProperties properties;
    private final Set<String> ignoreTables;

    /**
     * 构造租户隔离拦截器。
     *
     * @param properties 租户配置
     */
    public TenantIsolationInterceptor(TenantProperties properties) {
        this.properties = properties;
        this.ignoreTables = properties.getNormalizedIgnoreTables();
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        SqlCommandType sct = ms.getSqlCommandType();

        if (sct == SqlCommandType.INSERT || sct == SqlCommandType.SELECT ||
            sct == SqlCommandType.UPDATE || sct == SqlCommandType.DELETE) {
            if (!InterceptorIgnoreHelper.willIgnoreTenantLine(ms.getId())) {
                PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
                mpBs.sql(parserMulti(mpBs.sql(), null));
            }
        }
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        List<TenantFieldValue> values = resolveTenantValues();
        processSelectBody(select, values);
    }

    private void processSelectBody(Select select, List<TenantFieldValue> values) {
        if (select == null) {
            return;
        }
        if (select instanceof PlainSelect) {
            PlainSelect plain = (PlainSelect) select;
            applyTenantToFromItem(plain, values);
            applyTenantToJoins(plain, values);
            return;
        }
        if (select instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) select;
            if (setOperationList.getSelects() != null) {
                setOperationList.getSelects().forEach(it -> processSelectBody(it, values));
            }
        }
    }

    private void applyTenantToFromItem(PlainSelect plain, List<TenantFieldValue> values) {
        FromItem fromItem = plain.getFromItem();
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            if (!shouldIgnoreTable(table.getName())) {
                plain.setWhere(mergeWhere(plain.getWhere(), buildTenantConditions(table, values)));
            }
            return;
        }
        if (fromItem instanceof ParenthesedSelect) {
            ParenthesedSelect parenthesedSelect = (ParenthesedSelect) fromItem;
            processSelectBody(parenthesedSelect.getSelect(), values);
        }
    }

    private void applyTenantToJoins(PlainSelect plain, List<TenantFieldValue> values) {
        if (plain.getJoins() == null || plain.getJoins().isEmpty()) {
            return;
        }
        for (Join join : plain.getJoins()) {
            if (join.getRightItem() instanceof Table) {
                Table table = (Table) join.getRightItem();
                if (!shouldIgnoreTable(table.getName())) {
                    Expression existingOn = JSqlParserHelper.getJoinOnExpression(join);
                    Expression newOn = mergeWhere(existingOn, buildTenantConditions(table, values));
                    JSqlParserHelper.setJoinOnExpression(join, newOn);
                }
                continue;
            }
            if (join.getRightItem() instanceof ParenthesedSelect) {
                ParenthesedSelect parenthesedSelect = (ParenthesedSelect) join.getRightItem();
                processSelectBody(parenthesedSelect.getSelect(), values);
            }
        }
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        List<TenantFieldValue> values = resolveTenantValues();

        Table table = insert.getTable();
        if (table != null && !shouldIgnoreTable(table.getName())) {
            List<Column> columns = insert.getColumns();
            if (columns == null) {
                log.warn("INSERT 语句未显式声明列，跳过租户字段注入，table={}", table.getName());
                return;
            }

            for (TenantFieldValue tfv : values) {
                String resolvedColumn = resolveColumn(table.getName(), tfv.column);
                boolean hasColumn = columns.stream()
                    .anyMatch(col -> col.getColumnName() != null
                        && col.getColumnName().equalsIgnoreCase(resolvedColumn));

                if (!hasColumn) {
                    columns.add(new Column(resolvedColumn));
                    if (insert.getSelect() != null
                        && insert.getSelect().getPlainSelect() != null
                        && insert.getSelect().getPlainSelect().getSelectItems() != null) {
                        insert.getSelect().getPlainSelect().getSelectItems()
                            .add(new SelectItem<>(new StringValue(tfv.value)));
                    } else {
                        log.warn("INSERT 语句结构不支持自动注入 {}，table={}, sql={}",
                            resolvedColumn, table.getName(), sql);
                    }
                }
            }
        }
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        List<TenantFieldValue> values = resolveTenantValues();

        Table table = update.getTable();
        if (table != null && !shouldIgnoreTable(table.getName())) {
            Expression tenantCondition = buildTenantConditions(table, values);
            update.setWhere(mergeWhere(update.getWhere(), tenantCondition));
        }
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        List<TenantFieldValue> values = resolveTenantValues();

        Table table = delete.getTable();
        if (table != null && !shouldIgnoreTable(table.getName())) {
            Expression tenantCondition = buildTenantConditions(table, values);
            delete.setWhere(mergeWhere(delete.getWhere(), tenantCondition));
        }
    }

    /**
     * 解析表对应的租户列名（支持 per-table 覆盖）。
     *
     * @param tableName    表名
     * @param defaultColumn 默认列名
     * @return 解析后的列名
     */
    private String resolveColumn(String tableName, String defaultColumn) {
        String mapped = properties.resolveColumn(tableName);
        return mapped != null ? mapped : defaultColumn;
    }

    private Expression buildTenantConditions(Table table, List<TenantFieldValue> values) {
        Expression result = null;
        for (TenantFieldValue tfv : values) {
            String columnName = resolveColumn(table.getName(), tfv.column);
            Column column = buildAliasedColumn(table, columnName);
            Expression condition = new EqualsTo(column, new StringValue(tfv.value));
            result = mergeWhere(result, condition);
        }
        return result;
    }

    private Column buildAliasedColumn(Table table, String columnName) {
        Column column = new Column(columnName);
        if (table.getAlias() != null && table.getAlias().getName() != null
            && !table.getAlias().getName().isEmpty()) {
            column.setTable(new Table(table.getAlias().getName()));
        }
        return column;
    }

    private Expression mergeWhere(Expression existing, Expression additional) {
        if (additional == null) {
            return existing;
        }
        if (existing == null) {
            return additional;
        }
        return new AndExpression(existing, additional);
    }

    private boolean shouldIgnoreTable(String tableName) {
        if (tableName == null) {
            return true;
        }
        return ignoreTables.contains(tableName.toLowerCase());
    }

    /**
     * 解析当前请求的租户字段值列表（fail-closed）。
     *
     * <p>从 {@link TenantContextHolder} 获取租户上下文，根据配置的
     * {@link TenantField} 列表逐字段取值。任意字段缺失则抛异常。
     *
     * @return 租户字段值列表（非空）
     * @throws TenantIsolationException 任一字段值缺失时抛出
     */
    private List<TenantFieldValue> resolveTenantValues() {
        TenantContext context = TenantContextHolder.get();

        // 跳过隔离（匿名 URL）
        if (context != null && context.isSkipIsolation()) {
            return new ArrayList<>(0);
        }

        // 超级管理员不隔离
        if (context != null && context.isSuperAdmin()) {
            return new ArrayList<>(0);
        }

        // fail-closed：无上下文
        if (context == null || context.isEmpty()) {
            throw new TenantIsolationException(
                "无法获取租户上下文，已拒绝执行 SQL 以避免跨租户数据泄露。"
                + "请检查 TenantContextWebFilter 是否正确注册，"
                + "或使用 SystemTenantContextRunner 包装异步/定时任务，"
                + "或将相关表加入 ignore-tables，或将 URL 加入 anon-urls。");
        }

        List<TenantField> activeFields = properties.getActiveTenantFields();
        List<TenantFieldValue> result = new ArrayList<>(activeFields.size());
        for (TenantField field : activeFields) {
            String value = resolveTenantValue(context, field.getSource());
            if (value == null || value.isEmpty()) {
                throw new TenantIsolationException(
                    "无法获取租户字段 [" + field.getColumn() + "] 的值（source=" + field.getSource()
                    + "），已拒绝执行 SQL。");
            }
            result.add(new TenantFieldValue(field.getColumn(), value));
        }
        return result;
    }

    /**
     * 根据 {@link TenantSource} 从 {@link TenantContext} 获取租户字段值。
     *
     * @param context 租户上下文
     * @param source  值来源标识
     * @return 字段值；上下文未设置返回 null
     */
    private String resolveTenantValue(TenantContext context, TenantSource source) {
        if (source == null || context == null) {
            return null;
        }
        switch (source) {
            case TENANT:
                return context.getTenantId();
            case GROUP:
                return context.getDimension(com.njydsz.common.tenant.TenantDimension.GROUP);
            case COMPANY:
                return context.getDimension(com.njydsz.common.tenant.TenantDimension.COMPANY);
            case USER:
                // USER source 需要从认证上下文获取（暂时回退到 tenantId）
                return context.getTenantId();
            default:
                return null;
        }
    }

    private static class TenantFieldValue {
        final String column;
        final String value;

        TenantFieldValue(String column, String value) {
            this.column = column;
            this.value = value;
        }
    }
}
