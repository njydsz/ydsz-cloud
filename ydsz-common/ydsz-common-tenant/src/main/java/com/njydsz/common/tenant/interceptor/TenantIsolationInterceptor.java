package com.njydsz.common.tenant.interceptor;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.jdbc.exception.TenantIsolationException;
import com.njydsz.common.jdbc.interceptor.JSqlParserHelper;
import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.tenant.config.TenantProperties.TenantField;
import com.njydsz.common.tenant.metrics.TenantMetrics;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
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
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

/**
 * 多租户隔离拦截器。
 *
 * <p>基于 MyBatis-Plus InnerInterceptor 实现，通过 JSqlParser SQL 改写 自动在 SQL 语句中注入租户条件，实现数据行级别的租户隔离。
 *
 * <p>支持单租户模式（SINGLE）和多级租户模式（MULTI）：
 *
 * <ul>
 *   <li>SINGLE：只注入第一个字段，SQL 效果 {@code WHERE tenant_id = ?}
 *   <li>MULTI：注入全部字段，SQL 效果 {@code WHERE group_tenant_id = ? AND company_tenant_id = ?}
 * </ul>
 *
 * <p>支持 per-table 列名覆盖：通过 {@link TenantProperties#getTableColumnMapping()} 或 {@code @TenantColumn}
 * 注解自定义列名。
 *
 * <p><b>fail-closed 原则：</b>当无法获取租户上下文时抛出 {@link TenantIsolationException} 拒绝执行 SQL，避免数据泄露。
 *
 * <p>此拦截器通过 {@link TenantInterceptorProvider} SPI 注册到 {@code MybatisPlusInterceptor} 链中（order=400）。
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
   * <p>使用 ydsz-common-cache 实现 LRU 淘汰 + TTL 过期，Key 包含完整租户字段签名 （tenantId + companyId + deptId +
   * ...），避免 MULTI 模式不同维度取值 命中错误缓存导致的跨租户数据泄露。
   *
   * <p>缓存上限 2000 条，10 分钟未访问自动过期。
   *
   * <p><b>注意：</b>默认关闭，需通过 {@code ydsz.tenant.sql-cache.enabled=true} 开启。
   *
   * @since 1.1.0 由 ConcurrentHashMap 迁移至 ydsz-common-cache（修复 P0-1 缓存 Key 不完整缺陷）
   */
  private final Cache<String, String> sqlCache;

  public TenantIsolationInterceptor(TenantProperties properties, TenantMetrics metrics) {
    this.properties = properties;
    this.ignoreTables = properties.getNormalizedIgnoreTables();
    this.metrics = metrics;
    this.sqlCache =
        properties.getSqlCache().isEnabled()
            ? YdszCache.<String, String>newBuilder()
                .name("tenant:sql-cache")
                .maximumSize(properties.getSqlCache().getMaxSize())
                .expireAfterAccess(properties.getSqlCache().getExpireMinutes(), TimeUnit.MINUTES)
                .build()
            : null;
  }

  public TenantIsolationInterceptor(TenantProperties properties) {
    this(properties, null);
  }

  @Override
  public void beforePrepare(
      StatementHandler sh, Connection connection, Integer transactionTimeout) {
    PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
    MappedStatement ms = mpSh.mappedStatement();
    SqlCommandType sct = ms.getSqlCommandType();

    if (sct == SqlCommandType.INSERT
        || sct == SqlCommandType.SELECT
        || sct == SqlCommandType.UPDATE
        || sct == SqlCommandType.DELETE) {
      if (!InterceptorIgnoreHelper.willIgnoreTenantLine(ms.getId())) {
        PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
        String originalSql = mpBs.sql();

        // 缓存未开启时直接走 JSqlParser 解析路径
        if (sqlCache == null) {
          mpBs.sql(parserMulti(originalSql, null));
          return;
        }

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
   *
   * <ul>
   *   <li>MULTI 模式下所有字段值均参与 Key 计算，确保不同维度取值不会命中错误缓存
   *   <li>跳过隔离或超级管理员时使用 "skip" / "superadmin" 标记
   *   <li>无租户上下文时使用 "none" 标记（触发 fail-closed）
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
   * <p>缓存未开启时返回 0。
   *
   * @return 缓存条目数估算值
   */
  public long getSqlCacheSize() {
    return sqlCache != null ? sqlCache.estimatedSize() : 0L;
  }

  @Override
  protected void processSelect(Select select, int index, String sql, Object obj) {
    List<TenantFieldValue> values = resolveTenantValues();
    processSelectBody(select, values);
  }

  /**
   * 递归处理 Select 节点，注入租户条件。
   *
   * <p>覆盖以下结构：
   *
   * <ul>
   *   <li>{@link PlainSelect}：主查询体（FROM/JOIN/WHERE/HAVING/selectItems）
   *   <li>{@link SetOperationList}：UNION/INTERSECT 等集合操作各分支
   *   <li>{@link WithItem}：WITH CTE 子查询（防止 CTE 引用漏注入）
   *   <li>{@link ParenthesedSelect}：括号子查询（FROM 子查询 / WHERE 标量子查询）
   * </ul>
   *
   * @param select Select 节点，可为 null
   * @param values 租户字段值列表（已解析，非空）
   */
  private void processSelectBody(Select select, List<TenantFieldValue> values) {
    if (select == null) {
      return;
    }
    // WITH CTE：递归处理每个 CTE 子查询，防止 CTE 引用漏注入租户条件
    if (select.getWithItemsList() != null) {
      for (WithItem withItem : select.getWithItemsList()) {
        if (withItem.getSelect() != null) {
          processSelectBody(withItem.getSelect(), values);
        }
      }
    }
    if (select instanceof PlainSelect) {
      PlainSelect plain = (PlainSelect) select;
      applyTenantToFromItem(plain, values);
      applyTenantToJoins(plain, values);
      // WHERE 条件中的标量子查询（如 WHERE id = (SELECT ...)）
      processExpressionSubqueries(plain.getWhere(), values);
      // HAVING 条件中的标量子查询
      processExpressionSubqueries(plain.getHaving(), values);
      // selectItems 中的标量子查询（如 SELECT (SELECT name FROM t2) FROM t1）
      if (plain.getSelectItems() != null) {
        for (SelectItem<?> selectItem : plain.getSelectItems()) {
          processExpressionSubqueries(selectItem.getExpression(), values);
        }
      }
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
   * 递归遍历表达式树，处理其中嵌套的标量子查询（{@link ParenthesedSelect}）。
   *
   * <p>防止「WHERE 标量子查询 / HAVING 标量子查询 / selectItems 标量子查询」 中的子表漏注入租户条件，导致跨租户数据泄露。
   *
   * @param expr 表达式节点，可为 null
   * @param values 租户字段值列表（已解析，非空）
   */
  private void processExpressionSubqueries(Expression expr, List<TenantFieldValue> values) {
    if (expr == null) {
      return;
    }
    if (expr instanceof ParenthesedSelect) {
      ParenthesedSelect subSelect = (ParenthesedSelect) expr;
      if (subSelect.getSelect() != null) {
        processSelectBody(subSelect.getSelect(), values);
      }
      return;
    }
    if (expr instanceof BinaryExpression) {
      BinaryExpression binary = (BinaryExpression) expr;
      processExpressionSubqueries(binary.getLeftExpression(), values);
      processExpressionSubqueries(binary.getRightExpression(), values);
      return;
    }
    if (expr instanceof InExpression) {
      InExpression in = (InExpression) expr;
      processExpressionSubqueries(in.getLeftExpression(), values);
      processExpressionSubqueries(in.getRightExpression(), values);
      return;
    }
    if (expr instanceof NotExpression) {
      processExpressionSubqueries(((NotExpression) expr).getExpression(), values);
    }
    // 其他表达式类型（Column/StringValue/LongValue 等）不包含子查询，无需处理
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
        boolean hasColumn =
            columns.stream()
                .anyMatch(
                    col ->
                        col.getColumnName() != null
                            && col.getColumnName().equalsIgnoreCase(resolvedColumn));

        if (!hasColumn) {
          columns.add(new Column(resolvedColumn));
          Select select = insert.getSelect();
          if (select != null) {
            // INSERT ... SELECT 形式：向 SELECT 列列表末尾追加租户字段值
            PlainSelect plainSelect = select.getPlainSelect();
            if (plainSelect != null && plainSelect.getSelectItems() != null) {
              plainSelect
                  .getSelectItems()
                  .add(new SelectItem<>(new StringValue(String.valueOf(tfv.value))));
            } else {
              // INSERT ... SELECT 使用复杂结构（集合操作/括号子查询）时无法对齐列数，
              // fail-closed：拒绝执行，防止租户字段漏注入导致跨租户数据写入
              throw new TenantIsolationException(
                  "INSERT ... SELECT 语句结构无法对齐租户字段 ["
                      + resolvedColumn
                      + "] 的列数，已拒绝执行 SQL 以防跨租户数据写入。table="
                      + table.getName()
                      + ", sql="
                      + sql);
            }
          } else if (insert.getValues() != null && insert.getValues().getExpressions() != null) {
            // INSERT ... VALUES 形式：向 VALUES 列表末尾追加租户字段值，
            // 保持列数与值数一致，防止列数不匹配导致 SQL 执行失败
            @SuppressWarnings("unchecked")
            List<Expression> valueExprs = (List<Expression>) insert.getValues().getExpressions();
            valueExprs.add(new StringValue(String.valueOf(tfv.value)));
          } else {
            log.warn(
                "INSERT 语句结构不支持自动注入 {}，table={}, sql={}", resolvedColumn, table.getName(), sql);
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
   * @param tableName 表名
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
        // 多值 → IN (...)：构建表达式树，避免字符串拼接导致的注入风险
        condition = buildInExpression(column, list);
      } else {
        condition = new EqualsTo(column, new StringValue(String.valueOf(tfv.value)));
      }
      result = mergeWhere(result, condition);
    }
    return result;
  }

  private Column buildAliasedColumn(Table table, String columnName) {
    Column column = new Column(columnName);
    if (table.getAlias() != null
        && table.getAlias().getName() != null
        && !table.getAlias().getName().isEmpty()) {
      column.setTable(new Table(table.getAlias().getName()));
    }
    return column;
  }

  /**
   * 构建类型安全的 IN 表达式。
   *
   * <p>基于 JSqlParser 表达式树构造，{@link StringValue} 内部已正确处理 单引号转义，避免字符串拼接导致的 SQL 注入风险。
   *
   * @param column 目标列
   * @param list 值列表
   * @return InExpression 表达式
   */
  private Expression buildInExpression(Column column, List<?> list) {
    ExpressionList<Expression> expressionList = new ExpressionList<>();
    List<Expression> items = new ArrayList<>(list.size());
    for (Object item : list) {
      items.add(new StringValue(String.valueOf(item)));
    }
    expressionList.setExpressions(items);
    return new InExpression(column, expressionList);
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
   * <p>从 {@link TenantContextHolder} 获取租户上下文，根据配置的 {@link TenantField} 列表逐字段取值。任意字段缺失则抛异常。
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
      if (value == null
          || (value instanceof String s && s.isEmpty())
          || (value instanceof List<?> l && l.isEmpty())) {
        if (metrics != null) metrics.recordFailClosed();
        throw new TenantIsolationException(
            "无法获取租户字段 [" + field.getColumn() + "] 的值（claim=" + claimName + "），已拒绝执行 SQL。");
      }
      // 跨租户共享：将主租户字段值扩展为 [当前租户, 共享租户...]
      if (context.hasSharing() && isPrimaryTenantField(field)) {
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
   * <p>若原始值为单值 String，返回 {@code List<当前, 共享1, 共享2...>}； 若原始值已是 List，将共享租户追加到末尾（去重）。
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
   * <p>{@code value} 可能为单值或 {@link List} 多值（对应 IN 条件）， 由 {@link #buildTenantConditions} 按值类型构造等价条件。
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
