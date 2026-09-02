package com.njydsz.common.jdbc.interceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.mapping.SqlCommandType;

import com.njydsz.common.jdbc.config.DataPermissionConfiguration;
import com.njydsz.common.jdbc.exception.TenantIsolationException;
import com.njydsz.common.jdbc.monitor.SqlAstCache;
import com.njydsz.common.jdbc.permission.DataPermissionContext;
import com.njydsz.common.jdbc.permission.DataPermissionContextResolver;
import com.njydsz.common.util.string.StringUtils;

/**
 * 列级数据权限拦截器
 *
 * <p>基于 MyBatis-Plus {@link InnerInterceptor} 接口实现，继承 {@link JsqlParserSupport} 解析和改写 SQL，控制 SELECT
 * 返回列以及 INSERT/UPDATE 可写列。
 *
 * <p>功能：
 *
 * <ul>
 *   <li>SELECT：根据可见列规则，过滤 SELECT 语句中返回的列，防止越权访问敏感字段
 *   <li>UPDATE：根据可编辑列规则，过滤 UPDATE 语句中的 SET 列
 *   <li>INSERT：根据可编辑列规则，移除 INSERT 语句中不允许写入的列
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class ColPermissionInnerInterceptor extends DataPermissionInnerInterceptor {

  /**
   * 构造列级数据权限拦截器
   *
   * @param sqlAstCache SQL 解析缓存
   * @param config 数据权限配置
   * @param contextResolver 数据权限上下文解析器
   */
  public ColPermissionInnerInterceptor(
      SqlAstCache sqlAstCache,
      DataPermissionConfiguration config,
      DataPermissionContextResolver contextResolver) {
    super(sqlAstCache, config, contextResolver);
  }

  /**
   * 列级权限不需要检查系统级绕过（列过滤对后台任务同样生效）。
   *
   * @return false
   */
  @Override
  protected boolean shouldCheckBypass() {
    return false;
  }

  /**
   * 列级权限支持 SELECT/UPDATE/INSERT。
   *
   * @param sqlCommandType SQL 命令类型
   * @return 支持 SELECT/UPDATE/INSERT 时返回 true
   */
  @Override
  protected boolean isSupportedSqlType(SqlCommandType sqlCommandType) {
    return sqlCommandType == SqlCommandType.SELECT
        || sqlCommandType == SqlCommandType.UPDATE
        || sqlCommandType == SqlCommandType.INSERT;
  }

  /**
   * 处理 SELECT 语句，根据可见列规则过滤返回列
   *
   * @param select SELECT 语句对象
   * @param index 语句索引
   * @param sql 原始 SQL 字符串
   * @param obj 数据权限上下文
   */
  @Override
  protected void processSelect(Select select, int index, String sql, Object obj) {
    DataPermissionContext context = (DataPermissionContext) obj;
    processSelectBody(select, context);
  }

  /**
   * 处理 DELETE 语句（DELETE 不需要列级权限控制，空实现）
   *
   * @param delete DELETE 语句对象
   * @param index 语句索引
   * @param sql 原始 SQL 字符串
   * @param obj 数据权限上下文
   */
  @Override
  protected void processDelete(Delete delete, int index, String sql, Object obj) {
    // DELETE 不需要列级权限控制
  }

  /**
   * 处理 UPDATE 语句，根据可编辑列规则过滤 SET 列
   *
   * @param update UPDATE 语句对象
   * @param index 语句索引
   * @param sql 原始 SQL 字符串
   * @param obj 数据权限上下文
   */
  @Override
  protected void processUpdate(Update update, int index, String sql, Object obj) {
    DataPermissionContext context = (DataPermissionContext) obj;
    Table table = update.getTable();
    if (!shouldApply(table)) {
      return;
    }
    applyEditableColumns(
        table,
        JSqlParserHelper.getUpdateSetsColumns(update),
        JSqlParserHelper.getUpdateSetsExpressions(update),
        context);
  }

  /**
   * 处理 INSERT 语句，根据可编辑列规则移除不允许写入的列
   *
   * @param insert INSERT 语句对象
   * @param index 语句索引
   * @param sql 原始 SQL 字符串
   * @param obj 数据权限上下文
   */
  @Override
  protected void processInsert(Insert insert, int index, String sql, Object obj) {
    DataPermissionContext context = (DataPermissionContext) obj;
    Table table = insert.getTable();
    if (!shouldApply(table)) {
      return;
    }
    List<Column> columns = insert.getColumns();
    if (CollectionUtils.isEmpty(columns)) {
      return;
    }
    Set<String> allowed = getEditableColumns(table, context);
    if (allowed.isEmpty()) {
      return;
    }
    List<Integer> removeIndexes = findColumnsToRemove(columns, allowed);
    if (removeIndexes.isEmpty()) {
      return;
    }
    removeColumnsAndValues(insert, columns, removeIndexes);
    if (columns.isEmpty()) {
      // fail-closed：所有列均无写入权限，拒绝执行 INSERT 避免空记录或语法错误
      throw new TenantIsolationException(
          "列权限拦截：表 " + (table != null ? table.getName() : null) + " 没有可写入列，已拒绝 INSERT 操作");
    }
  }

  /**
   * 查找不在允许集合中的列索引，用于 INSERT 语句移除不允许写入的列
   *
   * @param columns 列列表
   * @param allowed 允许的列名集合（已标准化）
   * @return 需要移除的列索引列表
   */
  private List<Integer> findColumnsToRemove(List<Column> columns, Set<String> allowed) {
    List<Integer> removeIndexes = new ArrayList<>(16);
    for (int i = 0; i < columns.size(); i++) {
      String col = normalizeColumnName(columns.get(i).getColumnName());
      if (!allowed.contains(col)) {
        removeIndexes.add(i);
      }
    }
    return removeIndexes;
  }

  /**
   * 从 INSERT 语句中移除指定索引的列及其对应的值，支持 VALUES 和子查询两种插入方式
   *
   * @param insert INSERT 语句对象
   * @param columns 列列表（将被就地修改）
   * @param removeIndexes 需要移除的列索引列表（逆序移除以避免索引偏移）
   */
  private void removeColumnsAndValues(
      Insert insert, List<Column> columns, List<Integer> removeIndexes) {
    for (int i = removeIndexes.size() - 1; i >= 0; i--) {
      columns.remove((int) removeIndexes.get(i));
    }

    Values values = insert.getValues();
    if (values != null) {
      ExpressionList<?> list = values.getExpressions();
      if (list != null) {
        for (int i = removeIndexes.size() - 1; i >= 0; i--) {
          list.remove((int) removeIndexes.get(i));
        }
      }
    } else if (insert.getSelect() != null) {
      Select select = insert.getSelect();
      if (select instanceof PlainSelect) {
        PlainSelect plain = (PlainSelect) select;
        List<SelectItem<?>> selectItems = plain.getSelectItems();
        if (CollectionUtils.isNotEmpty(selectItems)) {
          for (int i = removeIndexes.size() - 1; i >= 0; i--) {
            int removeIndex = removeIndexes.get(i);
            if (removeIndex >= 0 && removeIndex < selectItems.size()) {
              selectItems.remove(removeIndex);
            }
          }
        }
      }
    }
  }

  /**
   * 递归处理 SELECT 语句体，对 PlainSelect 应用可见列规则，对 SetOperationList 递归处理每个子查询
   *
   * @param select SELECT 语句对象
   * @param context 数据权限上下文
   */
  private void processSelectBody(Select select, DataPermissionContext context) {
    if (select == null) {
      return;
    }
    if (select instanceof PlainSelect) {
      PlainSelect plain = (PlainSelect) select;
      applyVisibleColumns(plain, context);
      return;
    }
    if (select instanceof SetOperationList) {
      SetOperationList setOperationList = (SetOperationList) select;
      if (CollectionUtils.isNotEmpty(setOperationList.getSelects())) {
        setOperationList.getSelects().forEach(it -> processSelectBody(it, context));
      }
    }
  }

  /**
   * 对 PlainSelect 应用可见列规则，根据上下文中的可见列配置过滤 SELECT 项， 支持 AllColumns、AllTableColumns 和普通 Column 三种类型的处理
   *
   * @param plain PlainSelect 语句
   * @param context 数据权限上下文
   */
  private void applyVisibleColumns(PlainSelect plain, DataPermissionContext context) {
    if (context == null
        || context.getVisibleColumnsByTable() == null
        || context.getVisibleColumnsByTable().isEmpty()) {
      return;
    }
    List<Table> tables = extractTables(plain);
    if (tables.isEmpty()) {
      return;
    }

    List<SelectItem<?>> items = plain.getSelectItems();
    if (CollectionUtils.isEmpty(items)) {
      return;
    }

    List<SelectItem<?>> out = new ArrayList<>(16);
    for (SelectItem<?> item : items) {
      Expression exp = item.getExpression();
      if (exp instanceof AllColumns) {
        List<SelectItem<?>> expanded = expandAllColumns(tables, context);
        if (expanded.isEmpty()) {
          out.add(item);
        } else {
          out.addAll(expanded);
        }
        continue;
      }
      if (exp instanceof AllTableColumns) {
        Table ref = ((AllTableColumns) exp).getTable();
        Table matched = findMatchingTable(tables, ref);
        if (matched == null) {
          out.add(item);
          continue;
        }
        if (!hasVisibleRule(matched, context)) {
          out.add(item);
          continue;
        }
        Set<String> allowed = getVisibleColumns(matched, context);
        if (allowed.isEmpty()) {
          continue;
        }
        out.addAll(buildAllowedSelectItems(matched, allowed));
        continue;
      }
      if (!(exp instanceof Column)) {
        out.add(item);
        continue;
      }
      Column col = (Column) exp;
      Table owningTable = resolveOwningTable(tables, plain, col);
      if (owningTable == null) {
        out.add(item);
        continue;
      }
      if (!hasVisibleRule(owningTable, context)) {
        out.add(item);
        continue;
      }
      Set<String> allowed = getVisibleColumns(owningTable, context);
      if (allowed.isEmpty()) {
        continue;
      }
      String colName = normalizeColumnName(col.getColumnName());
      if (allowed.contains(colName)) {
        out.add(item);
      }
    }

    if (out.isEmpty()) {
      List<SelectItem<?>> expanded = expandAllColumns(tables, context);
      if (!expanded.isEmpty()) {
        plain.setSelectItems(expanded);
      } else {
        // fail-closed：所有表均有可见列规则但允许列集合为空，替换为 NULL 占位避免泄露全部列
        log.warn("所有表均无可见列，已将 SELECT 列表替换为 NULL 占位避免数据泄露");
        SelectItem<NullValue> nullItem = new SelectItem<>(new NullValue());
        plain.setSelectItems(Collections.singletonList(nullItem));
      }
      return;
    }
    plain.setSelectItems(out);
  }

  /**
   * 将 AllColumns（SELECT *）展开为各表的可见列 SelectItem 列表， 无可见列规则的表保留 AllTableColumns
   *
   * @param tables 涉及的表列表
   * @param context 数据权限上下文
   * @return 展开后的 SelectItem 列表
   */
  private List<SelectItem<?>> expandAllColumns(List<Table> tables, DataPermissionContext context) {
    List<SelectItem<?>> expanded = new ArrayList<>(16);
    for (Table table : tables) {
      if (!hasVisibleRule(table, context)) {
        SelectItem<AllTableColumns> item = new SelectItem<>(new AllTableColumns(table));
        expanded.add(item);
        continue;
      }
      Set<String> allowed = getVisibleColumns(table, context);
      if (allowed.isEmpty()) {
        continue;
      }
      expanded.addAll(buildAllowedSelectItems(table, allowed));
    }
    return expanded;
  }

  /**
   * 根据允许的列名集合构建 SelectItem 列表
   *
   * @param table 目标表
   * @param allowed 允许的列名集合
   * @return 由允许列构成的 SelectItem 列表
   */
  private List<SelectItem<?>> buildAllowedSelectItems(Table table, Set<String> allowed) {
    List<SelectItem<?>> out = new ArrayList<>(16);
    for (String column : allowed) {
      SelectItem<Column> item = new SelectItem<>(new Column(table, column));
      out.add(item);
    }
    return out;
  }

  /**
   * 从 PlainSelect 中提取所有需要拦截的表（包括主表和 JOIN 表）
   *
   * @param plain PlainSelect 语句
   * @return 需要拦截的表列表
   */
  private List<Table> extractTables(PlainSelect plain) {
    List<Table> tables = new ArrayList<>(16);
    if (plain == null) {
      return tables;
    }
    if (plain.getFromItem() instanceof Table) {
      Table table = (Table) plain.getFromItem();
      if (shouldApply(table)) {
        tables.add(table);
      }
    }
    if (CollectionUtils.isNotEmpty(plain.getJoins())) {
      for (Join join : plain.getJoins()) {
        if (join.getRightItem() instanceof Table) {
          Table table = (Table) join.getRightItem();
          if (shouldApply(table)) {
            tables.add(table);
          }
        }
      }
    }
    return tables;
  }

  /**
   * 解析列所属的表，优先通过列自带的表限定符匹配，其次回退到主表
   *
   * @param tables 涉及的表列表
   * @param plain PlainSelect 语句
   * @param col 待解析的列
   * @return 列所属的表，无法确定时返回 null
   */
  private Table resolveOwningTable(List<Table> tables, PlainSelect plain, Column col) {
    if (col == null) {
      return null;
    }
    if (col.getTable() != null && StringUtils.isNotBlank(col.getTable().getName())) {
      Table matched = findMatchingTable(tables, col.getTable());
      if (matched != null) {
        return matched;
      }
    }
    if (plain != null && plain.getFromItem() instanceof Table) {
      Table fromTable = (Table) plain.getFromItem();
      if (!shouldApply(fromTable)) {
        return null;
      }
      for (Table candidate : tables) {
        if (candidate == fromTable) {
          return candidate;
        }
      }
      return fromTable;
    }
    return null;
  }

  /**
   * 在表列表中查找与引用表匹配的表，优先按别名匹配，其次按表名匹配
   *
   * @param tables 候选表列表
   * @param ref 引用表（可能带别名）
   * @return 匹配到的表，未找到时返回 null
   */
  private Table findMatchingTable(List<Table> tables, Table ref) {
    if (ref == null || CollectionUtils.isEmpty(tables)) {
      return null;
    }
    String refName = normalizeTableName(ref);
    String refAlias = ref.getAlias() == null ? "" : normalizeIdentifier(ref.getAlias().getName());
    for (Table candidate : tables) {
      String candName = normalizeTableName(candidate);
      String candAlias =
          candidate.getAlias() == null ? "" : normalizeIdentifier(candidate.getAlias().getName());
      if (StringUtils.isNotBlank(refAlias) && refAlias.equals(candAlias)) {
        return candidate;
      }
      if (StringUtils.isNotBlank(refName)
          && (refName.equals(candAlias) || refName.equals(candName))) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * 标准化标识符，去除前后空格并转为小写
   *
   * @param name 标识符名称
   * @return 标准化后的标识符，为空时返回空字符串
   */
  private String normalizeIdentifier(String name) {
    if (StringUtils.isBlank(name)) {
      return "";
    }
    return name.trim().toLowerCase();
  }

  /**
   * 对 UPDATE 语句应用可编辑列规则，移除不在允许集合中的列及其对应表达式
   *
   * @param table 目标表
   * @param columns SET 列列表（将被就地修改）
   * @param expressions SET 值表达式列表（将被就地修改）
   * @param context 数据权限上下文
   */
  private void applyEditableColumns(
      Table table,
      List<Column> columns,
      List<Expression> expressions,
      DataPermissionContext context) {
    if (CollectionUtils.isEmpty(columns) || CollectionUtils.isEmpty(expressions)) {
      return;
    }
    Set<String> allowed = getEditableColumns(table, context);
    if (allowed.isEmpty()) {
      return;
    }
    for (int i = columns.size() - 1; i >= 0; i--) {
      String col = normalizeColumnName(columns.get(i).getColumnName());
      if (!allowed.contains(col)) {
        columns.remove(i);
        expressions.remove(i);
      }
    }
    if (columns.isEmpty()) {
      log.warn("列权限拦截：表 {} 没有可编辑列，跳过UPDATE列过滤", table.getName());
    }
  }

  /**
   * 获取指定表的可见列集合，从上下文中按标准化表名查找并转为小写
   *
   * @param table 目标表
   * @param context 数据权限上下文
   * @return 可见列名集合（小写），无规则时返回空集合
   */
  private Set<String> getVisibleColumns(Table table, DataPermissionContext context) {
    if (context == null || context.getVisibleColumnsByTable() == null) {
      return Collections.emptySet();
    }
    return context
        .getVisibleColumnsByTable()
        .getOrDefault(normalizeTableName(table), Collections.emptySet())
        .stream()
        .map(String::toLowerCase)
        .collect(Collectors.toCollection(HashSet::new));
  }

  /**
   * 判断指定表是否存在可见列规则
   *
   * @param table 目标表
   * @param context 数据权限上下文
   * @return 存在可见列规则时返回 true，否则返回 false
   */
  private boolean hasVisibleRule(Table table, DataPermissionContext context) {
    if (context == null || context.getVisibleColumnsByTable() == null) {
      return false;
    }
    return context.getVisibleColumnsByTable().containsKey(normalizeTableName(table));
  }

  /**
   * 获取指定表的可编辑列集合，从上下文中按标准化表名查找并转为小写
   *
   * @param table 目标表
   * @param context 数据权限上下文
   * @return 可编辑列名集合（小写），无规则时返回空集合
   */
  private Set<String> getEditableColumns(Table table, DataPermissionContext context) {
    if (context == null || context.getEditableColumnsByTable() == null) {
      return Collections.emptySet();
    }
    return context
        .getEditableColumnsByTable()
        .getOrDefault(normalizeTableName(table), Collections.emptySet())
        .stream()
        .map(String::toLowerCase)
        .collect(Collectors.toCollection(HashSet::new));
  }

  /**
   * 标准化表名，去除模式前缀（如 schema.tableName 中的 schema.）并转为小写
   *
   * @param table 目标表
   * @return 标准化后的表名，表名为空时返回空字符串
   */
  private String normalizeTableName(Table table) {
    return DataPermissionHelper.normalizeTableName(table);
  }

  /**
   * 标准化列名，去除反引号/双引号包裹和表限定前缀，并转为小写
   *
   * @param col 列名
   * @return 标准化后的列名，为空时返回空字符串
   */
  private String normalizeColumnName(String col) {
    if (StringUtils.isBlank(col)) {
      return "";
    }
    String out = col;
    // 去除反引号（MySQL）和双引号（PostgreSQL/ANSI）包裹
    if (out.startsWith("`") && out.endsWith("`")) {
      out = out.substring(1, out.length() - 1);
    } else if (out.startsWith("\"") && out.endsWith("\"")) {
      out = out.substring(1, out.length() - 1);
    }
    // 去除表限定前缀（如 table.column 中的 table.）
    if (out.contains(".")) {
      out = out.substring(out.lastIndexOf('.') + 1);
    }
    // 再次去除可能残留的反引号/双引号
    out = out.replace("`", "").replace("\"", "");
    return out.toLowerCase();
  }
}
