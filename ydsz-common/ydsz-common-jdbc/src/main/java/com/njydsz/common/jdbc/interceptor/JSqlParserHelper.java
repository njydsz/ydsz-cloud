package com.njydsz.common.jdbc.interceptor;.interceptor
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
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
 *   <li>JOIN 语句 ON 表达式操作
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