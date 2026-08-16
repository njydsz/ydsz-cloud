package com.njydsz.common.jdbc.interceptor;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.common.jdbc.config.DataPermissionConfiguration;
import com.njydsz.common.jdbc.monitor.SqlAstCache;
import com.njydsz.common.jdbc.permission.DataPermissionContext;
import com.njydsz.common.jdbc.permission.DataPermissionContextResolver;
import java.sql.Connection;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * 数据权限拦截器抽象基类（行级 + 列级公共逻辑）
 *
 * <p>抽取 {@link RowPermissionInnerInterceptor} 和 {@link ColPermissionInnerInterceptor}
 * 的公共逻辑，消除以下重复代码：
 *
 * <ul>
 *   <li>{@code apply()} — 空上下文处理 + parserSingle 调用
 *   <li>{@code beforePrepare()} — 配置检查 + SQL 类型过滤 + 注解忽略 + 解析权限上下文
 *   <li>{@code shouldApply(Table)} — 表名标准化 + 拦截策略判断
 *   <li>{@code isDataPermissionIgnored()} — @DataPermissionIgnore 注解检查
 *   <li>{@code normalizeTableSet()} — 表名集合标准化
 * </ul>
 *
 * <p>模板方法 {@link #beforePrepare} 定义了通用拦截流程，子类可通过覆盖 {@link #shouldCheckBypass()} 和 {@link
 * #isSupportedSqlType} 自定义行为差异。
 *
 * <p>行级拦截器处理 SELECT/UPDATE/DELETE；列级拦截器处理 SELECT/UPDATE/INSERT。 两者在 INSERT vs DELETE 上的差异由 {@link
 * #isSupportedSqlType} 抽象方法承载。
 *
 * @author ydsz-team
 * @since 1.8.0
 */
@Slf4j
public abstract class DataPermissionInnerInterceptor extends CachingJsqlParserSupport
    implements InnerInterceptor {

  /** 数据权限配置 */
  protected final DataPermissionConfiguration config;

  /** 数据权限上下文解析器 */
  protected final DataPermissionContextResolver contextResolver;

  /** 标准化后的表名集合（小写），与拦截策略配合使用 */
  protected final Set<String> normalizedTables;

  /**
   * 构造数据权限拦截器基类
   *
   * @param sqlAstCache SQL 解析缓存
   * @param config 数据权限配置
   * @param contextResolver 数据权限上下文解析器
   */
  protected DataPermissionInnerInterceptor(
      SqlAstCache sqlAstCache,
      DataPermissionConfiguration config,
      DataPermissionContextResolver contextResolver) {
    super(sqlAstCache);
    this.config = config;
    this.contextResolver = contextResolver;
    this.normalizedTables = DataPermissionHelper.normalizeTableSet(config);
  }

  // ====================================================================
  // 公共模板方法
  // ====================================================================

  /**
   * 应用数据权限到 SQL（供外部复合拦截器调用）
   *
   * <p>空配置时直接返回原 SQL；空上下文时降级为空上下文（不拦截）。
   *
   * @param sql 原始 SQL 语句
   * @param context 数据权限上下文，为 null 时使用空上下文
   * @return 应用数据权限后的 SQL 语句
   */
  public String apply(String sql, DataPermissionContext context) {
    if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
      return sql;
    }
    if (context == null) {
      context = DataPermissionContext.empty();
    }
    return parserSingle(sql, context);
  }

  /**
   * SQL 执行前回调，解析当前 SQL 类型并应用数据权限控制
   *
   * <p>模板方法，定义通用拦截流程：
   *
   * <ol>
   *   <li>配置未启用 → 跳过
   *   <li>（可选）系统级绕过检查 → 跳过
   *   <li>SQL 类型不支持 → 跳过
   *   <li>@DataPermissionIgnore 注解 → 跳过
   *   <li>解析权限上下文并改写 SQL
   * </ol>
   *
   * @param sh StatementHandler
   * @param connection 数据库连接
   * @param transactionTimeout 事务超时时间
   */
  @Override
  public void beforePrepare(
      StatementHandler sh, Connection connection, Integer transactionTimeout) {
    if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
      return;
    }
    if (shouldCheckBypass() && DataPermissionHelper.isBypassActive()) {
      return;
    }
    PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
    MappedStatement ms = mpSh.mappedStatement();
    if (!isSupportedSqlType(ms.getSqlCommandType())) {
      return;
    }
    if (DataPermissionHelper.isDataPermissionIgnored(ms)) {
      return;
    }
    DataPermissionContext context = contextResolver.resolve();
    PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
    mpBs.sql(parserSingle(mpBs.sql(), context));
  }

  // ====================================================================
  // 子类可覆盖的行为钩子
  // ====================================================================

  /**
   * 是否需要检查系统级绕过（如定时任务、MQ 消费者）
   *
   * <p>行级权限需要检查（后台任务无用户上下文应跳过），列级权限不需要。
   *
   * @return true 时激活绕过检查
   */
  protected boolean shouldCheckBypass() {
    return false;
  }

  /**
   * 判断当前 SQL 命令类型是否支持权限拦截
   *
   * <p>行级拦截器支持 SELECT/UPDATE/DELETE；列级拦截器支持 SELECT/UPDATE/INSERT。
   *
   * @param sqlCommandType SQL 命令类型
   * @return 支持时返回 true
   */
  protected abstract boolean isSupportedSqlType(
      org.apache.ibatis.mapping.SqlCommandType sqlCommandType);

  // ====================================================================
  // 公共辅助方法（子类可直接使用）
  // ====================================================================

  /**
   * 检查 MappedStatement 对应的方法是否标注了 @DataPermissionIgnore 注解
   *
   * @param ms MyBatis MappedStatement
   * @return 标注了忽略注解时返回 true
   */
  protected boolean isDataPermissionIgnored(MappedStatement ms) {
    return DataPermissionHelper.isDataPermissionIgnored(ms);
  }

  /**
   * 判断是否应对指定表应用数据权限拦截
   *
   * @param table 目标表
   * @return 需要拦截时返回 true
   */
  public boolean shouldApply(net.sf.jsqlparser.schema.Table table) {
    return DataPermissionHelper.shouldApply(table, config, normalizedTables);
  }

  // ====================================================================
  // 默认空实现（子类按需覆盖）
  // ====================================================================

  /**
   * 处理 SELECT 语句。
   *
   * <p>默认空实现，子类可按需覆盖。
   */
  @Override
  protected void processSelect(Select select, int index, String sql, Object obj) {
    // 默认不处理
  }

  /**
   * 处理 INSERT 语句。
   *
   * <p>默认空实现，子类可按需覆盖。
   */
  @Override
  protected void processInsert(Insert insert, int index, String sql, Object obj) {
    // 默认不处理
  }

  /**
   * 处理 UPDATE 语句。
   *
   * <p>默认空实现，子类可按需覆盖。
   */
  @Override
  protected void processUpdate(Update update, int index, String sql, Object obj) {
    // 默认不处理
  }

  /**
   * 处理 DELETE 语句。
   *
   * <p>默认空实现，子类可按需覆盖。
   */
  @Override
  protected void processDelete(Delete delete, int index, String sql, Object obj) {
    // 默认不处理
  }
}
