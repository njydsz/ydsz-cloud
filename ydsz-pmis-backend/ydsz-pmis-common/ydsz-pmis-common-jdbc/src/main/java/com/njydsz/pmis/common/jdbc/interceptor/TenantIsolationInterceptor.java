package com.njydsz.pmis.common.jdbc.interceptor;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.pmis.common.jdbc.config.TenantIsolationProperties;
import com.njydsz.pmis.common.jdbc.exception.TenantIsolationException;
import com.njydsz.pmis.common.context.AuthInfoUtils;
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
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * 多租户隔离拦截器
 *
 * <p>基于 MyBatis-Plus InnerInterceptor 实现的多租户数据隔离拦截器。
 * 自动在 SQL 语句中注入租户条件，实现数据行级别的租户隔离。
 *
 * <p>功能特性：
 * <ul>
 *   <li>SELECT 语句自动添加 WHERE tenant_id = ? 条件（递归处理 JOIN/UNION/子查询）</li>
 *   <li>INSERT 语句自动填充 tenant_id 字段</li>
 *   <li>UPDATE/DELETE 语句自动添加 WHERE tenant_id = ? 条件</li>
 *   <li>支持忽略特定表（如系统配置表）</li>
 *   <li>从 {@link AuthInfoUtils}/{@link com.njydsz.pmis.common.core.context.RequestContext} 获取当前租户 ID</li>
 *   <li>fail-closed：无法获取租户 ID 时抛异常拒绝执行，避免数据泄露</li>
 *   <li>JOIN 场景自动追加表别名前缀，避免列名歧义</li>
 * </ul>
 *
 * <p><b>安全设计原则：</b>当无法获取租户 ID 时（如定时任务、异步线程未设置上下文），
 * 拦截器将抛出 {@link TenantIsolationException} 拒绝执行 SQL，遵循 fail-closed 原则，
 * 避免因上下文缺失导致跨租户数据泄露。对于确需跨租户的场景（如系统初始化、数据迁移），
 * 请将相关表加入 {@code pmis.jdbc.tenant-isolation.ignore-tables} 配置。
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @see TenantIsolationProperties
 */
@Slf4j
public class TenantIsolationInterceptor extends JsqlParserSupport implements InnerInterceptor {

    private final TenantIsolationProperties properties;
    private final Set<String> ignoreTables;

    public TenantIsolationInterceptor(TenantIsolationProperties properties) {
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

    @SuppressWarnings("rawtypes")
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        if (!InterceptorIgnoreHelper.willIgnoreTenantLine(ms.getId())) {
            PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
            mpBs.sql(parserSingle(mpBs.sql(), null));
        }
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        // fail-closed：先校验租户上下文，缺失则抛异常
        String tenantId = requireTenantId();
        processSelectBody(select, tenantId);
    }

    /**
     * 递归处理 SELECT 语句体，支持 PlainSelect、SetOperationList（UNION/UNION ALL）和子查询。
     *
     * @param select   SELECT 语句对象
     * @param tenantId 当前租户 ID（已校验非空）
     */
    private void processSelectBody(Select select, String tenantId) {
        if (select == null) {
            return;
        }
        if (select instanceof PlainSelect) {
            PlainSelect plain = (PlainSelect) select;
            applyTenantToFromItem(plain, tenantId);
            applyTenantToJoins(plain, tenantId);
            return;
        }
        if (select instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) select;
            if (setOperationList.getSelects() != null) {
                setOperationList.getSelects().forEach(it -> processSelectBody(it, tenantId));
            }
        }
    }

    /**
     * 对 PlainSelect 的主表（FROM）追加租户条件；若 FROM 为子查询则递归处理。
     */
    private void applyTenantToFromItem(PlainSelect plain, String tenantId) {
        FromItem fromItem = plain.getFromItem();
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            if (!shouldIgnoreTable(table.getName())) {
                plain.setWhere(mergeWhere(plain.getWhere(), buildTenantCondition(table, tenantId)));
            }
            return;
        }
        if (fromItem instanceof ParenthesedSelect) {
            ParenthesedSelect parenthesedSelect = (ParenthesedSelect) fromItem;
            processSelectBody(parenthesedSelect.getSelect(), tenantId);
        }
    }

    /**
     * 对 PlainSelect 的 JOIN 表追加租户条件到 ON 子句（避免 LEFT JOIN 被改写为 INNER JOIN），
     * 若 JOIN 的是子查询则递归处理。
     */
    private void applyTenantToJoins(PlainSelect plain, String tenantId) {
        if (plain.getJoins() == null || plain.getJoins().isEmpty()) {
            return;
        }
        for (Join join : plain.getJoins()) {
            if (join.getRightItem() instanceof Table) {
                Table table = (Table) join.getRightItem();
                if (!shouldIgnoreTable(table.getName())) {
                    Expression existingOn = JSqlParserHelper.getJoinOnExpression(join);
                    Expression newOn = mergeWhere(existingOn, buildTenantCondition(table, tenantId));
                    JSqlParserHelper.setJoinOnExpression(join, newOn);
                }
                continue;
            }
            if (join.getRightItem() instanceof ParenthesedSelect) {
                ParenthesedSelect parenthesedSelect = (ParenthesedSelect) join.getRightItem();
                processSelectBody(parenthesedSelect.getSelect(), tenantId);
            }
        }
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        String tenantId = requireTenantId();

        Table table = insert.getTable();
        if (table != null && !shouldIgnoreTable(table.getName())) {
            List<Column> columns = insert.getColumns();
            if (columns == null) {
                // INSERT 未显式声明列，无法安全注入，跳过（建议业务方显式声明列）
                log.warn("INSERT 语句未显式声明列，跳过租户字段注入，table={}", table.getName());
                return;
            }
            String tenantColumn = properties.getTenantColumn();
            boolean hasTenantColumn = columns.stream()
                .anyMatch(col -> col.getColumnName() != null
                    && col.getColumnName().equalsIgnoreCase(tenantColumn));

            if (!hasTenantColumn) {
                columns.add(new Column(tenantColumn));
                // 兼容批量 INSERT（多 VALUES 行）与单行 INSERT
                if (insert.getSelect() != null
                    && insert.getSelect().getPlainSelect() != null
                    && insert.getSelect().getPlainSelect().getSelectItems() != null) {
                    insert.getSelect().getPlainSelect().getSelectItems().add(new net.sf.jsqlparser.statement.select.SelectItem<>(new StringValue(tenantId)));
                } else {
                    log.warn("INSERT 语句结构不支持自动注入 tenant_id，table={}, sql={}", table.getName(), sql);
                }
            }
        }
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        String tenantId = requireTenantId();

        Table table = update.getTable();
        if (table != null && !shouldIgnoreTable(table.getName())) {
            Expression tenantCondition = buildTenantCondition(table, tenantId);
            update.setWhere(mergeWhere(update.getWhere(), tenantCondition));
        }
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        String tenantId = requireTenantId();

        Table table = delete.getTable();
        if (table != null && !shouldIgnoreTable(table.getName())) {
            Expression tenantCondition = buildTenantCondition(table, tenantId);
            delete.setWhere(mergeWhere(delete.getWhere(), tenantCondition));
        }
    }

    /**
     * 构建租户条件表达式，自动带上表别名前缀避免 JOIN 时列名歧义。
     *
     * @param table    目标表（含别名信息）
     * @param tenantId 租户 ID
     * @return {@code [别名.]tenant_id = 'xxx'} 条件
     */
    private Expression buildTenantCondition(Table table, String tenantId) {
        String tenantColumn = properties.getTenantColumn();
        Column column = buildAliasedColumn(table, tenantColumn);
        return new EqualsTo(column, new StringValue(tenantId));
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
     * 获取当前租户 ID，优先从 {@link AuthInfoUtils} 获取，回退到
     * {@link com.njydsz.pmis.common.core.context.RequestContext}。
     *
     * @return 租户 ID；若上下文未设置返回 null
     */
    private String getCurrentTenantId() {
        // 优先从认证上下文获取（JWT Claim 解析后的值，可信）
        String tenantId = AuthInfoUtils.getTenantId();
        if (tenantId != null && !tenantId.isEmpty()) {
            return tenantId;
        }
        // 回退到 RequestContext（TTL 上下文，支持线程池透传）
        return com.njydsz.pmis.common.core.context.RequestContext.getTenantId();
    }

    /**
     * 校验租户上下文，fail-closed。
     *
     * <p>当无法获取租户 ID 时抛出 {@link TenantIsolationException}，
     * 拒绝执行 SQL 以避免跨租户数据泄露。
     *
     * @return 当前租户 ID（非空）
     * @throws TenantIsolationException 租户上下文缺失时抛出
     */
    private String requireTenantId() {
        String tenantId = getCurrentTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            throw new TenantIsolationException(
                "无法获取当前租户 ID，已拒绝执行 SQL 以避免跨租户数据泄露。"
                + "请检查认证上下文是否设置，或将相关表加入 pmis.jdbc.tenant-isolation.ignore-tables 配置。");
        }
        return tenantId;
    }
}
