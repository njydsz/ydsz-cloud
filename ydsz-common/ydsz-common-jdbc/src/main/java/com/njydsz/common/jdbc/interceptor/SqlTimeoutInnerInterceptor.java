package com.njydsz.common.jdbc.interceptor;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.transaction.Transaction;

/**
 * SQL 执行超时统一控制拦截器
 *
 * <p>在 MyBatis 准备 {@link java.sql.Statement} 阶段，通过 {@link MappedStatement#setStatementTimeout(Integer)}
 * 注入全局超时时间（秒）。超时到达后 JDBC 驱动抛出 {@code SQLTimeoutException}，
 * MyBatis 将其包装为 {@code PersistenceException} 向上传播。
 *
 * <p><b>配置：</b>
 *
 * <pre>
 * ydsz:
 *   jdbc:
 *     query-timeout-seconds: 30   # 全局 SQL 超时（秒），0=不限时
 * </pre>
 *
 * <p><b>生效时机：</b>在 {@link InnerInterceptor#beforePrepare(StatementHandler, Transaction)} 阶段，
 * StatementHandler 已创建但 Statement 尚未创建，此时设置 MappedStatement 的属性
 * 会传递到最终 Statement 对象。
 *
 * <p><b>异常行为：</b>超时后 JDBC 驱动抛 {@code java.sql.SQLTimeoutException}，
 * 由 MyBatis 包装后传播，业务层应配合 ydsz-common-safe 熔断器使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class SqlTimeoutInnerInterceptor implements InnerInterceptor {

  /** 全局超时秒数，0 表示不限制 */
  private final int queryTimeoutSeconds;

  /**
   * 构造超时拦截器
   *
   * @param queryTimeoutSeconds 超时时间（秒），0 或负数表示不限制
   */
  public SqlTimeoutInnerInterceptor(int queryTimeoutSeconds) {
    this.queryTimeoutSeconds = Math.max(queryTimeoutSeconds, 0);
  }

  /**
   * StatementHandler 创建后、JDBC Statement 创建前调用。
   *
   * <p>将全局查询超时设置到 {@link MappedStatement#getStatementTimeout()}，
   * MP 后续创建 Statement 时会据此调用 {@link java.sql.Statement#setQueryTimeout(int)}。
   *
   * <p><b>注意：</b>仅当 MappedStatement 自身未配置超时时才注入全局默认值，
   * 以保留用户通过注解或 XML 级别的个性化配置。
   *
   * @param sh MyBatis StatementHandler
   * @param transaction 当前事务对象
   */
  @Override
  public void beforePrepare(StatementHandler sh, Transaction transaction) {
    if (queryTimeoutSeconds <= 0) {
      // 不限制时跳过，使用驱动默认值
      return;
    }
    try {
      MappedStatement ms = sh.getMappedStatement();
      if (ms != null && (ms.getStatementTimeout() == null || ms.getStatementTimeout() <= 0)) {
        ms.setStatementTimeout(queryTimeoutSeconds);
        log.debug("全局 SQL 超时已应用: {} = {} 秒", ms.getId(), queryTimeoutSeconds);
      }
    } catch (Exception e) {
      // 设置失败不应阻塞 SQL 执行（降级为驱动默认超时）
      log.warn("全局 SQL 超时设置失败: {}", e.getMessage());
    }
  }

  /**
   * 查询前拦截（预留扩展点，当前无需额外逻辑）。
   *
   * <p>超时设置已在 {@link #beforePrepare} 完成，此处仅保留接口契约。
   *
   * @param executor 执行器
   * @param ms MappedStatement
   * @param parameter 参数对象
   * @param rowBounds 分页参数
   * @param resultHandler 结果处理器
   * @param boundSql 绑定后的 SQL
   */
  @Override
  public void beforeQuery(
      org.apache.ibatis.executor.Executor executor,
      MappedStatement ms,
      Object parameter,
      org.apache.ibatis.session.RowBounds rowBounds,
      org.apache.ibatis.session.ResultHandler resultHandler,
      org.apache.ibatis.mapping.BoundSql boundSql) {
    // 不需要额外处理，超时控制由 beforePrepare 完成
  }
}
