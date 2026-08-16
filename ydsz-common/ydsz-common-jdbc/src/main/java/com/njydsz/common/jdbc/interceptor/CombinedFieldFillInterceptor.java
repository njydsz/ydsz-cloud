package com.njydsz.common.jdbc.interceptor;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.jdbc.handler.FieldFillHandler;
import com.njydsz.common.jdbc.monitor.SqlAstCache;

/**
 * 合并的字段填充拦截器
 *
 * <p>支持传入多个 {@link FieldFillHandler}，单次 SQL 解析完成所有字段填充， 避免为每个 Handler 注册独立拦截器导致的 SQL 重复解析开销。
 *
 * <p><b>优化点：</b>
 *
 * <ul>
 *   <li>单次 SQL 解析，所有 handler 共享一次解析结果
 *   <li>只对 INSERT/UPDATE 语句处理，跳过 SELECT/DELETE
 *   <li>每个 handler 独立判断 createIgnore / updateIgnore
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FieldFillInterceptor
 */
public class CombinedFieldFillInterceptor extends CachingJsqlParserSupport
    implements InnerInterceptor {

  private static final Logger LOG = LoggerFactory.getLogger(CombinedFieldFillInterceptor.class);

  /** 字段填充处理器列表（不可变） */
  private final List<FieldFillHandler> handlers;

  public CombinedFieldFillInterceptor(SqlAstCache sqlAstCache, List<FieldFillHandler> handlers) {
    super(sqlAstCache);
    if (handlers == null || handlers.isEmpty()) {
      this.handlers = Collections.emptyList();
    } else {
      this.handlers = Collections.unmodifiableList(new ArrayList<>(handlers));
    }
  }

  @Override
  public void beforePrepare(
      StatementHandler sh, Connection connection, Integer transactionTimeout) {
    if (handlers.isEmpty()) {
      return;
    }
    PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
    MappedStatement ms = mpSh.mappedStatement();
    SqlCommandType sct = ms.getSqlCommandType();

    // 仅处理 INSERT/UPDATE；SELECT/DELETE 无字段填充语义
    if (sct != SqlCommandType.INSERT && sct != SqlCommandType.UPDATE) {
      return;
    }

    PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
    mpBs.sql(parserMulti(mpBs.sql(), null));
  }

  @Override
  protected void processSelect(Select select, int index, String sql, Object obj) {
    // SELECT 不做字段填充
  }

  @Override
  protected void processDelete(Delete delete, int index, String sql, Object obj) {
    // DELETE 不做字段填充
  }

  @Override
  protected void processInsert(Insert insert, int index, String sql, Object obj) {
    String tableName = insert.getTable().getName();

    List<Column> columns = JSqlParserHelper.getInsertColumns(insert);
    if (CollectionUtils.isEmpty(columns)) {
      return;
    }

    Values values = insert.getValues();
    Select select = insert.getSelect();
    List<SelectItem<?>> selectItems = null;
    if (select instanceof PlainSelect) {
      selectItems = ((PlainSelect) select).getSelectItems();
    }

    boolean changed = false;
    for (FieldFillHandler handler : handlers) {
      if (handler.createIgnore(tableName)) {
        continue;
      }
      String column = handler.getFieldFillColumn();
      Expression fillValue = handler.getFieldFillValue();
      if (StringUtils.isBlank(column) || fillValue == null) {
        LOG.warn(
            "字段填充配置异常：填充字段名或值为空，跳过处理。handler={}, 字段：{}",
            handler.getClass().getSimpleName(),
            column);
        continue;
      }
      boolean hasFillColumn =
          columns.stream()
              .map(Column::getColumnName)
              .anyMatch(colName -> colName.equalsIgnoreCase(column));
      if (hasFillColumn) {
        continue;
      }

      boolean valueAdded = false;
      if (selectItems != null) {
        appendSelectItem(selectItems, fillValue);
        valueAdded = true;
      } else if (values != null && values.getExpressions() != null) {
        ExpressionList<Expression> typedList = new ExpressionList<>();
        for (Object item : values.getExpressions()) {
          typedList.add(Expression.class.cast(item));
        }
        typedList.add(fillValue);
        values.setExpressions(typedList);
        valueAdded = true;
      }

      if (valueAdded) {
        columns.add(new Column(column));
        changed = true;
        LOG.debug("INSERT 字段填充成功: table={}, column={}", tableName, column);
      }
    }

    if (!changed) {
      LOG.debug("INSERT 字段填充未生效: table={}", tableName);
    }
  }

  @Override
  protected void processUpdate(Update update, int index, String sql, Object obj) {
    Table table = update.getTable();
    if (table == null) {
      return;
    }
    String tableName = table.getName();

    List<Column> columns = JSqlParserHelper.getUpdateSetsColumns(update);
    if (CollectionUtils.isEmpty(columns)) {
      return;
    }

    for (FieldFillHandler handler : handlers) {
      if (handler.updateIgnore(tableName)) {
        continue;
      }
      String column = handler.getFieldFillColumn();
      Expression fillValue = handler.getFieldFillValue();
      if (StringUtils.isBlank(column) || fillValue == null) {
        LOG.warn(
            "字段填充配置异常：填充字段名或值为空，跳过处理。handler={}, 字段：{}",
            handler.getClass().getSimpleName(),
            column);
        continue;
      }
      if (columns.stream().map(Column::getColumnName).anyMatch(c -> c.equals(column))) {
        continue;
      }
      update.addUpdateSet(new Column(column), fillValue);
      LOG.debug("UPDATE 字段填充成功: table={}, column={}", tableName, column);
    }
  }

  private void appendSelectItem(List<SelectItem<?>> selectItems, Expression fillValue) {
    if (CollectionUtils.isEmpty(selectItems) || fillValue == null) {
      return;
    }
    if (selectItems.size() == 1) {
      SelectItem<?> item = selectItems.get(0);
      Expression exp = item.getExpression();
      if (exp instanceof AllColumns || exp instanceof AllTableColumns) {
        return;
      }
    }
    selectItems.add(new SelectItem<>(fillValue));
  }

  @Override
  public void setProperties(Properties properties) {
    // 由 Spring 容器注入 handlers，无需通过 properties 反射创建
  }
}
