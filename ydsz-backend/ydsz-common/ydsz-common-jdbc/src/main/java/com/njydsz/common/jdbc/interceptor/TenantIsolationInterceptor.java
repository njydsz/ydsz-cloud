package com.njydsz.common.jdbc.interceptor;

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
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.jdbc.config.TenantIsolationProperties;
import com.njydsz.common.jdbc.config.TenantIsolationProperties.TenantField;
import com.njydsz.common.jdbc.config.TenantIsolationProperties.TenantSource;
import com.njydsz.common.jdbc.exception.TenantIsolationException;
import com.njydsz.common.util.auth.AuthInfoUtils;

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
 * 多租户隔离拦截器
 *
 * <p>基于 MyBatis-Plus InnerInterceptor 实现的多租户数据隔离拦截器。
 * 自动在 SQL 语句中注入租户条件，实现数据行级别的租户隔离。
 *
 * <p>支持单租户模式（SINGLE）和多级租户模式（MULTI）：
 * <ul>
 *   <li>SINGLE：只注入第一个字段，SQL 效果 {@code WHERE tenant_id = ?}</li>
 *   <li>MULTI：注入全部字段，SQL 效果 {@code WHERE group_tenant_id = ? AND company_tenant_id = ?}</li>
 * </ul>
 *
 * <p>功能特性：
 * <ul>
 *   <li>SELECT 语句自动添加 WHERE 租户条件（递归处理 JOIN/UNION/子查询）</li>
 *   <li>INSERT 语句自动填充租户字段</li>
 *   <li>UPDATE/DELETE 语句自动添加 WHERE 租户条件</li>
 *   <li>支持忽略特定表（全局 ignore-tables）</li>
 *   <li>支持 URL 白名单跳过（anon-urls，通过 RequestContext 跳过标记）</li>
 *   <li>从 {@link AuthInfoUtils}/{@link RequestContext} 获取租户 ID</li>
 *   <li>fail-closed：无法获取租户 ID 时抛异常拒绝执行，避免数据泄露</li>
 *   <li>JOIN 场景自动追加表别名前缀，避免列名歧义</li>
 * </ul>
 *
 * <p><b>安全设计原则：</b>当无法获取租户 ID 时（如定时任务、异步线程未设置上下文），
 * 拦截器将抛出 {@link TenantIsolationException} 拒绝执行 SQL，遵循 fail-closed 原则。
 * 对于确需跳过租户隔离的场景（如登录/注册等公开接口），请将 URL 配置到
 * {@code anon-urls} 或将相关表配置到 {@code ignore-tables}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see TenantIsolationProperties
 */
@Slf4j
public class TenantIsolationInterceptor extends JsqlParserSupport implements InnerInterceptor {

    private final TenantIsolationProperties properties;
    private final Set<String> ignoreTables;

    /**
     * 构造租户隔离拦截器。
     *
     * @param properties 租户隔离配置
     */
    public TenantIsolationInterceptor(TenantIsolationProperties properties) {
        this.properties = properties;
        this.ignoreTables = properties.getNormalizedIgnoreTables();
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        // 跳过检查：Web 层可通过 RequestContext 标记跳过租户隔离
        if (RequestContext.isTenantIsolationSkipped()) {
            return;
        }

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
        // fail-closed：先校验租户上下文，缺失则抛异常
        List<TenantFieldValue> values = resolveTenantValues();
        processSelectBody(select, values);
    }

    /**
     * 递归处理 SELECT 语句体，支持 PlainSelect、SetOperationList（UNION/UNION ALL）和子查询。
     *
     * @param select SELECT 语句对象
     * @param values 租户字段值列表（已校验非空）
     */
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

    /**
     * 对 PlainSelect 的主表（FROM）追加租户条件；若 FROM 为子查询则递归处理。
     */
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

    /**
     * 对 PlainSelect 的 JOIN 表追加租户条件到 ON 子句（避免 LEFT JOIN 被改写为 INNER JOIN），
     * 若 JOIN 的是子查询则递归处理。
     */
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
                boolean hasColumn = columns.stream()
                    .anyMatch(col -> col.getColumnName() != null
                        && col.getColumnName().equalsIgnoreCase(tfv.column));

                if (!hasColumn) {
                    columns.add(new Column(tfv.column));
                    if (insert.getSelect() != null
                        && insert.getSelect().getPlainSelect() != null
                        && insert.getSelect().getPlainSelect().getSelectItems() != null) {
                        insert.getSelect().getPlainSelect().getSelectItems()
                            .add(new SelectItem<>(new StringValue(tfv.value)));
                    } else {
                        log.warn("INSERT 语句结构不支持自动注入 {}，table={}, sql={}",
                            tfv.column, table.getName(), sql);
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
     * 构建多字段租户条件表达式，自动带上表别名前缀避免 JOIN 时列名歧义。
     *
     * <p>多字段时用 AND 连接：
     * {@code [别名.]col1 = 'v1' AND [别名.]col2 = 'v2'}
     *
     * @param table  目标表（含别名信息）
     * @param values 租户字段值列表
     * @return AND 连接的条件表达式；values 为空时返回 null
     */
    private Expression buildTenantConditions(Table table, List<TenantFieldValue> values) {
        Expression result = null;
        for (TenantFieldValue tfv : values) {
            Column column = buildAliasedColumn(table, tfv.column);
            Expression condition = new EqualsTo(column, new StringValue(tfv.value));
            result = mergeWhere(result, condition);
        }
        return result;
    }

    /**
     * 根据表别名构建列引用。若表有别名则返回 {@code alias.column}，否则返回 {@code column}。
     */
    private Column buildAliasedColumn(Table table, String columnName) {
        Column column = new Column(columnName);
        if (table.getAlias() != null && table.getAlias().getName() != null
            && !table.getAlias().getName().isEmpty()) {
            column.setTable(new Table(table.getAlias().getName()));
        }
        return column;
    }

    /**
     * 合并 WHERE 条件：若原条件为 null 则返回新条件，否则用 AND 连接。
     */
    private Expression mergeWhere(Expression existing, Expression additional) {
        if (additional == null) {
            return existing;
        }
        if (existing == null) {
            return additional;
        }
        return new AndExpression(existing, additional);
    }

    /**
     * 判断是否应该忽略该表
     */
    private boolean shouldIgnoreTable(String tableName) {
        if (tableName == null) {
            return true;
        }
        return ignoreTables.contains(tableName.toLowerCase());
    }

    /**
     * 解析当前请求的租户字段值列表（fail-closed）。
     *
     * <p>根据 {@link TenantIsolationProperties#getActiveTenantFields()} 获取生效字段配置，
     * 逐字段根据 {@link TenantSource} 从上下文获取值。任意字段缺失则抛异常。
     *
     * @return 租户字段值列表（非空）
     * @throws TenantIsolationException 任一字段值缺失时抛出
     */
    private List<TenantFieldValue> resolveTenantValues() {
        List<TenantField> activeFields = properties.getActiveTenantFields();
        List<TenantFieldValue> result = new ArrayList<>(activeFields.size());
        for (TenantField field : activeFields) {
            String value = resolveTenantValue(field.getSource());
            if (value == null || value.isEmpty()) {
                throw new TenantIsolationException(
                    "无法获取租户字段 [" + field.getColumn() + "] 的值（source=" + field.getSource()
                    + "），已拒绝执行 SQL 以避免跨租户数据泄露。"
                    + "请检查认证上下文是否设置，或将相关表加入 ignore-tables，或将 URL 加入 anon-urls。");
            }
            result.add(new TenantFieldValue(field.getColumn(), value));
        }
        return result;
    }

    /**
     * 根据 {@link TenantSource} 从上下文获取租户字段值。
     *
     * @param source 值来源标识
     * @return 字段值；上下文未设置返回 null
     */
    private String resolveTenantValue(TenantSource source) {
        if (source == null) {
            return null;
        }
        switch (source) {
            case TENANT:
                String tenantId = AuthInfoUtils.getTenantId();
                if (tenantId != null && !tenantId.isEmpty()) {
                    return tenantId;
                }
                return RequestContext.getTenantId();
            case GROUP:
                return (String) RequestContext.get("groupTenantId");
            case COMPANY:
                return (String) RequestContext.get("companyTenantId");
            case USER:
                return AuthInfoUtils.getUniqueId();
            default:
                return null;
        }
    }

    /**
     * 租户字段值对（column + value）。
     */
    private static class TenantFieldValue {
        final String column;
        final String value;

        TenantFieldValue(String column, String value) {
            this.column = column;
            this.value = value;
        }
    }
}
