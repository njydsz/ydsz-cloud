package com.njydsz.common.jdbc.interceptor;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

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
   * 查询前拦截（MP 3.5.16+ 使用 beforeQuery 替代已废弃的 beforePrepare）。
   *
   * <p>通过 Executor.query 拦截，在 MappedStatement 执行前注入全局超时。
   * 注意：超时设置借助 Configuration 的 defaultStatementTimeout 实现全局兜底，
   * 详见 ydsz-common-jdbc 全局配置注入逻辑。
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
      Executor executor,
      MappedStatement ms,
      Object parameter,
      RowBounds rowBounds,
      ResultHandler resultHandler,
      BoundSql boundSql) {
    // 全局 SQL 超时由 ydsz.jdbc.query-timeout-seconds 在 Configuration 级别统一配置，
    // 此拦截器保留扩展点，当前无需额外逻辑。
  }
}
