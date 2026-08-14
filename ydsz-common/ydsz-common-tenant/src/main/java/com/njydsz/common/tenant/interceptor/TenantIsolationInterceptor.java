package com.njydsz.common.tenant.interceptor;

import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.jdbc.interceptor.JSqlParserHelper;
import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.tenant.config.TenantProperties.TenantField;
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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.njydsz.common.tenant.metrics.TenantMetrics;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
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
    private final TenantMetrics metrics;

    /**
     * SQL 改写缓存：缓存「原始 SQL + 完整租户字段签名」→ 改写后的 SQL。
     *
     * <p>使用 Caffeine 实现 LRU 淘汰 + TTL 过期，Key 包含完整租户字段签名
     *（tenantId + companyId + deptId + ...），避免 MULTI 模式不同维度取值
     * 命中错误缓存导致的跨租户数据泄露。
     *
     * <p>缓存上限 2000 条，10 分钟未访问自动过期。
     *
     * @since 1.1.0 由 ConcurrentHashMap 迁移至 Caffeine（修复 P0-1 缓存 Key 不完整缺陷）
     */
    private final Cache<String, String> sqlCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public TenantIsolationInterceptor(TenantProperties properties, TenantMetrics metrics) {
        this.properties = properties;
        this.ignoreTables = properties.getNormalizedIgnoreTables();
        this.metrics = metrics;
    }

    public TenantIsolationInterceptor(TenantProperties properties) {
        this(properties, null);
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
                String originalSql = mpBs.sql();
                String cacheKey = buildCacheKey(originalSql);

                String cachedSql = sqlCache.getIfPresent(cacheKey);
                if (cachedSql != null) {
                    // 缓存命中
                    mpBs.sql(cachedSql);
                    if (metrics != null) {
                        metrics.recordSqlCacheHit();
                    }
                } else {
                    // 缓存未命中，执行 JSqlParser 解析并缓存
                    String rewrittenSql = parserMulti(originalSql, null);
                    mpBs.sql(rewrittenSql);
                    sqlCache.put(cacheKey, rewrittenSql);
                    if (metrics != null) {
                        metrics.recordSqlCacheMiss();
                    }
                }
            }
        }
    }

    /**
     * 构建缓存 Key。
     *
     * <p>Key 由「完整原始 SQL + 全部租户字段签名」拼接而成：
     * <ul>
     *   <li>MULTI 模式下所有字段值均参与 Key 计算，确保不同维度取值不会命中错误缓存</li>
     *   <li>跳过隔离或超级管理员时使用 "skip" / "superadmin" 标记</li>
     *   <li>无租户上下文时使用 "none" 标记（触发 fail-closed）</li>
     * </ul>
     *
     * @param originalSql 原始 SQL（保持原样，不做 normalize）
     * @return 缓存 Key
     */
    private String buildCacheKey(String originalSql) {
        TenantContext context = TenantContextHolder.get();

        if (context == null) {
            return "none:" + originalSql;
        }
        if (context.isSkipIsolation()) {
            return "skip:" + originalSql;
        }
        if (context.isSuperAdmin()) {
            return "superadmin:" + originalSql;
        }

        // 拼接全部租户字段值作为签名
        StringJoiner joiner = new StringJoiner("|", "", ":" + originalSql);
        joiner.add(String.valueOf(context.getTenantId()));
        for (Map.Entry<String, Object> entry : context.getFields().entrySet()) {
            if (!"tenantId".equals(entry.getKey())) {
                joiner.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return joiner.toString();
    }

    /**
     * 获取当前缓存条目数估算值（用于监控）。
     *
     * @return 缓存条目数估算值
     */
    public long getSqlCacheSize() {
        return sqlCache.estimatedSize();
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
                            .add(new SelectItem<>(new StringValue(String.valueOf(tfv.value))));
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
            Expression condition;
            if (tfv.value instanceof List<?> list) {
                // 多值 → IN (?, ?, ...)
                StringBuilder inClause = new StringBuilder();
                inClause.append(column.toString()).append(" IN (");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) inClause.append(", ");
                    inClause.append("'").append(list.get(i)).append("'");
                }
                inClause.append(")");
                try {
                    condition = CCJSqlParserUtil.parseCondExpression(inClause.toString());
                } catch (JSQLParserException e) {
                    throw new RuntimeException("解析 IN 表达式失败: " + inClause, e);
                }
            } else {
                condition = new EqualsTo(column, new StringValue(String.valueOf(tfv.value)));
            }
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
            if (metrics != null) {
                metrics.recordContextSkip();
                metrics.recordInterceptSkipped();
            }
            return new ArrayList<>(0);
        }

        // 超级管理员不隔离
        if (context != null && context.isSuperAdmin()) {
            if (metrics != null) {
                metrics.recordSuperAdminBypass();
                metrics.recordInterceptSkipped();
            }
            return new ArrayList<>(0);
        }

        // SCHEMA 模式：由 PostgreSQL search_path 在数据库层隔离，无需列注入
        if (properties.getMode() == TenantProperties.TenantMode.SCHEMA) {
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
            String claimName = field.getClaim() != null ? field.getClaim() : "tenantId";
            Object value;
            if (field.isMultiValue()) {
                value = context.getFieldValues(claimName);
            } else {
                value = context.getFieldValue(claimName);
            }
            if (value == null || (value instanceof String s && s.isEmpty())
                    || (value instanceof List<?> l && l.isEmpty())) {
                if (metrics != null) metrics.recordFailClosed();
                throw new TenantIsolationException(
                    "无法获取租户字段 [" + field.getColumn() + "] 的值（claim=" + claimName
                    + "），已拒绝执行 SQL。");
            }
            // 跨租户共享：将主租户字段值扩展为 [当前租户, 共享租户...]
            if (context.hasSharedTenantIds() && isPrimaryTenantField(field)) {
                value = expandWithSharedTenants(value, context.getSharedTenantIds());
            }
            result.add(new TenantFieldValue(field.getColumn(), value));
        }
        if (metrics != null) metrics.recordInterceptPass();
        return result;
    }

    /**
     * 判断字段是否为主租户 ID 字段（tenantId 列）。
     *
     * <p>仅主租户字段参与跨租户共享扩展，其他字段（如 companyId、deptId）保持原值。
     */
    private boolean isPrimaryTenantField(TenantField field) {
        return "tenantId".equals(field.getColumn());
    }

    /**
     * 将主租户字段值与共享租户 ID 列表合并。
     *
     * <p>若原始值为单值 String，返回 {@code List<当前, 共享1, 共享2...>}；
     * 若原始值已是 List，将共享租户追加到末尾（去重）。
     *
     * @param originalValue 原始字段值（String 或 List）
     * @param sharedTenantIds 共享租户 ID 列表
     * @return 合并后的 List（非空）
     */
    private List<String> expandWithSharedTenants(Object originalValue, List<String> sharedTenantIds) {
        Set<String> merged = new java.util.LinkedHashSet<>();
        if (originalValue instanceof String s) {
            merged.add(s);
        } else if (originalValue instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    merged.add(s);
                }
            }
        }
        if (sharedTenantIds != null) {
            merged.addAll(sharedTenantIds);
        }
        return new ArrayList<>(merged);
    }

    /**
     * 租户字段取值载体：持有目标列名与实际取值。
     *
     * <p>{@code value} 可能为单值或 {@link List} 多值（对应 IN 条件），
     * 由 {@link #buildTenantConditions} 按值类型构造等价条件。
     *
     * @author ydsz-team
     * @since 1.0.0
     */
    private static class TenantFieldValue {
        final String column;
        final Object value;

        TenantFieldValue(String column, Object value) {
            this.column = column;
            this.value = value;
        }
    }
}
