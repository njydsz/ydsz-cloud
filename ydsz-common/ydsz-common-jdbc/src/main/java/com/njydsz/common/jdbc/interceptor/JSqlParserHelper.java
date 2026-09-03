package com.njydsz.common.jdbc.interceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

/**
 * JSqlParser 辅助工具类
 *
 * <p>提供 JSqlParser AST 节点的操作方法封装，简化常见的字段访问和修改逻辑。 所有方法直接委托给 JSqlParser 原生 API，确保与最新版本兼容。
 *
 * <h2>提供的功能</h2>
 *
 * <ul>
 *   <li>INSERT 语句列操作
 *   <li>UPDATE 语句列和值操作（使用 getUpdateSets）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class JSqlParserHelper {

  /** 私有构造方法，工具类禁止实例化。 */
  private JSqlParserHelper() {}

  /**
   * 获取 INSERT 语句的列列表
   *
   * @param insert INSERT 语句对象
   * @return 列列表
   */
  public static List<Column> getInsertColumns(Insert insert) {
    return insert.getColumns();
  }

  /**
   * 获取 UPDATE 语句的列列表（使用 UpdateSets API）
   *
   * <p>这是 JSqlParser 4.7+ 推荐的方式，通过 getUpdateSets() 获取列和值。
   *
   * @param update UPDATE 语句对象
   * @return 列列表
   */
  public static List<Column> getUpdateSetsColumns(Update update) {
    List<UpdateSet> updateSets = update.getUpdateSets();
    if (updateSets == null || updateSets.isEmpty()) {
      return new ArrayList<>(0);
    }
    List<Column> columns = new ArrayList<>(updateSets.size());
    for (UpdateSet updateSet : updateSets) {
      columns.addAll(updateSet.getColumns());
    }
    return columns;
  }

  /**
   * 获取 UPDATE 语句的 SET 值表达式列表（使用 UpdateSets API）
   *
   * <p>遍历所有 UpdateSet，提取每个 SET 子句右侧的表达式值。
   *
   * @param update UPDATE 语句对象
   * @return SET 值表达式列表（与列列表一一对应）
   */
  public static List<Expression> getUpdateSetsExpressions(Update update) {
    List<UpdateSet> updateSets = update.getUpdateSets();
    if (updateSets == null || updateSets.isEmpty()) {
      return new ArrayList<>(0);
    }
    List<Expression> expressions = new ArrayList<>(updateSets.size());
    for (UpdateSet updateSet : updateSets) {
      if (updateSet.getValues() != null) {
        expressions.addAll(updateSet.getValues());
      }
    }
    return expressions;
  }

  /**
   * 获取 JOIN 的 ON 条件表达式
   *
   * <p>兼容 JSqlParser 4.9+ API，通过 getOnExpressions() 获取 ON 列表中的第一个表达式。
   *
   * @param join JOIN 对象
   * @return ON 条件表达式，无 ON 条件时返回 null
   */
  public static Expression getJoinOnExpression(Join join) {
    Collection<Expression> onExpressions = join.getOnExpressions();
    if (onExpressions == null || onExpressions.isEmpty()) {
      return null;
    }
    return onExpressions.iterator().next();
  }

  /**
   * 设置 JOIN 的 ON 条件表达式
   *
   * <p>替换全部 ON 条件为单个表达式，兼容 JSqlParser 4.9+ API。
   *
   * @param join JOIN 对象
   * @param expression 要设置的 ON 条件表达式
   */
  public static void setJoinOnExpression(Join join, Expression expression) {
    if (expression == null) {
      join.setOnExpressions(Collections.emptyList());
    } else {
      join.setOnExpressions(Collections.singletonList(expression));
    }
  }
}
