package com.njydsz.common.jdbc.interceptor;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;

import com.njydsz.common.jdbc.monitor.SqlFingerprint;

/**
 * SQL 链路追踪拦截器（慢 SQL + 审计一体化）
 *
 * <p>将慢 SQL 检测与 SQL 审计合并为单个拦截器，避免对同一条 SQL 进行多次解析和多次 {@link MappedStatement#getBoundSql(Object)}
 * 调用，降低 MyBatis 拦截器链开销。
 *
 * <p>执行阶段说明：
 *
 * <ul>
 *   <li>{@code willDoQuery/willDoUpdate}：记录 SQL 开始时间，初始化 ThreadLocal 上下文
 *   <li>{@code beforePrepare}：此时所有前置拦截器已完成 SQL 改写，提取最终 SQL 并一次性完成 慢 SQL 判定、指标采集和审计日志输出
 * </ul>
 *
 * <p>通过实现 {@link Ordered} 接口，建议将该拦截器置于拦截器链最前端，确保最早记录开始时间。 默认优先级 {@link Ordered#HIGHEST_PRECEDENCE}
 * + 100。
 *
 * <p>替代关系：
 *
 * <ul>
 *   <li>替代 {@link SlowSqlInnerInterceptor}（慢 SQL 检测）
 *   <li>替代 {@link SqlAuditInterceptor}（SQL 审计）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SlowSqlInnerInterceptor
 * @see SqlAuditInterceptor
 */
@Slf4j
@Intercepts({
  @Signature(
      type = Executor.class,
      method = "query",
      args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
  @Signature(
      type = Executor.class,
      method = "query",
      args = {
        MappedStatement.class,
        Object.class,
        RowBounds.class,
        ResultHandler.class,
        CacheKey.class,
        BoundSql.class
      }),
  @Signature(
      type = Executor.class,
      method = "update",
      args = {MappedStatement.class, Object.class})
})
/**
 * SQL 追踪内部拦截器
 *
 * <p>基于 MyBatis-Plus InnerInterceptor 实现 SQL 执行追踪、审计日志和 Micrometer 指标采集。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SqlTraceInnerInterceptor
    implements InnerInterceptor, Ordered, MeterBinder, Interceptor {

  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在拦截器 finally 块调用 remove() 清理（云顶规范 15.1）
  /** ThreadLocal 存储当前查询/更新操作的开始时间与 SQL 标识 */
  private static final ThreadLocal<TimingContext> TIMING_CONTEXT = new ThreadLocal<>();

  /**
   * ThreadLocal 存储当前 SQL 执行的影响行数。
   *
   * <p>在 {@link #intercept(Invocation)} 中执行完 Executor.query/update 后写入， 供 {@link #logAudit}
   * 读取，替代硬编码 "N/A"。
   */
  private static final ThreadLocal<Integer> AFFECTED_ROWS = new ThreadLocal<>();
  // CHECKSTYLE.ON: RegexpSinglelineJava

  /** SQL 审计专用日志，便于独立配置 appender */
  private static final Logger AUDIT_LOG = LoggerFactory.getLogger("sql.audit");

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /** 慢 SQL 检测开关 */
  private boolean slowSqlEnabled = false;

  /** 慢 SQL 检测阈值（毫秒） */
  private long slowSqlThresholdMillis = 1000;

  /** 慢 SQL 告警阈值（毫秒） */
  private long alertThresholdMillis = 3000;

  /** Micrometer 指标注册表 */
  private MeterRegistry meterRegistry;

  /** SQL 审计开关 */
  private boolean auditEnabled = false;

  /** 是否审计 SELECT 语句 */
  private boolean auditSelect = false;

  /** 是否审计 INSERT 语句 */
  private boolean auditInsert = true;

  /** 是否审计 UPDATE 语句 */
  private boolean auditUpdate = true;

  /** 是否审计 DELETE 语句 */
  private boolean auditDelete = true;

  /** 是否记录 SQL 参数 */
  private boolean logParameters = true;

  /** 参数最大长度（超过则截断） */
  private int maxParameterLength = 500;

  /** 排除的表名列表 */
  private List<String> excludeTables;

  /** 排除的 Mapper 方法名列表 */
  private List<String> excludeMethods;

  /** 拦截器顺序，值越小越靠前 */
  private int order = Ordered.HIGHEST_PRECEDENCE + 100;

  public SqlTraceInnerInterceptor() {}

  public SqlTraceInnerInterceptor(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public int getOrder() {
    return order;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    this.meterRegistry = registry;
  }

  @Override
  public boolean willDoQuery(
      Executor executor,
      MappedStatement ms,
      Object parameter,
      RowBounds rowBounds,
      ResultHandler resultHandler,
      BoundSql boundSql)
      throws SQLException {
    startTiming(ms, parameter);
    return true;
  }

  @Override
  public boolean willDoUpdate(Executor executor, MappedStatement ms, Object parameter)
      throws SQLException {
    startTiming(ms, parameter);
    return true;
  }

  @Override
  public void beforePrepare(
      StatementHandler sh, Connection connection, Integer transactionTimeout) {
    TimingContext timing = TIMING_CONTEXT.get();
    if (timing == null) {
      return;
    }
    // 此处 SQL 已被所有前置拦截器改写为最终形态，提取后供 intercept 在
    // 真实执行完成后统一处理慢 SQL 与审计（耗时以拦截器链最外层计时为准）。
    BoundSql boundSql = sh.getBoundSql();
    String rawSql = extractRawSql(sh, boundSql);
    timing.finalSql = rawSql != null ? rawSql.replaceAll("\\s+", " ").trim() : null;
  }

  /** 启动计时上下文 */
  private void startTiming(MappedStatement ms, Object parameter) {
    // 清理上一个请求可能遗留的 ThreadLocal，防止线程池复用时泄漏
    TIMING_CONTEXT.remove();
    AFFECTED_ROWS.remove();
    if (ms == null) {
      return;
    }
    SqlCommandType commandType = ms.getSqlCommandType();
    if (commandType == null || commandType == SqlCommandType.FLUSH) {
      return;
    }
    TIMING_CONTEXT.set(new TimingContext(System.nanoTime(), ms.getId(), commandType, parameter));
  }

  /** 处理慢 SQL 检测、告警和指标采集 */
  private void handleSlowSql(String sqlId, long elapsed, String rawSql) {
    String displaySql = truncateSql(rawSql, 200);
    if (displaySql != null) {
      log.warn(
          "慢SQL检测 | SQL_ID: {} | SQL: {} | 耗时: {}ms | 阈值: {}ms",
          sqlId,
          displaySql,
          elapsed,
          slowSqlThresholdMillis);
    } else {
      log.warn("慢SQL检测 | SQL_ID: {} | 耗时: {}ms | 阈值: {}ms", sqlId, elapsed, slowSqlThresholdMillis);
    }

    if (elapsed > alertThresholdMillis) {
      log.error(
          "慢SQL告警 | SQL_ID: {} | 耗时: {}ms | 告警阈值: {}ms | 请及时优化SQL",
          sqlId,
          elapsed,
          alertThresholdMillis);
      logCallStack();
    }

    recordSlowSqlMetric(displaySql, elapsed);
  }

  /** 打印当前调用堆栈，辅助定位慢 SQL 调用位置 */
  private void logCallStack() {
    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    StringBuilder sb = new StringBuilder("慢SQL调用堆栈:");
    for (int i = 3; i < Math.min(stack.length, 15); i++) {
      sb.append("\n\tat ").append(stack[i].toString());
    }
    log.warn(sb.toString());
  }

  /** 从 StatementHandler 提取原始 SQL 语句 */
  private String extractRawSql(StatementHandler sh, BoundSql boundSql) {
    try {
      PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
      if (mpSh != null && mpSh.mPBoundSql() != null) {
        String sql = mpSh.mPBoundSql().sql();
        if (sql != null && !sql.isEmpty()) {
          return sql;
        }
      }
      if (boundSql != null) {
        String sql = boundSql.getSql();
        if (sql != null && !sql.isEmpty()) {
          return sql;
        }
      }
    } catch (Exception e) {
      log.debug("提取 SQL 失败: {}", e.getMessage());
    }
    return null;
  }

  /** 截断 SQL 用于日志和指标标签 */
  private String truncateSql(String sql, int maxLength) {
    if (sql == null || sql.isEmpty()) {
      return null;
    }
    return sql.length() > maxLength ? sql.substring(0, maxLength) + "..." : sql;
  }

  /** 记录慢 SQL 指标到 Micrometer */
  private void recordSlowSqlMetric(String sql, long elapsed) {
    if (meterRegistry == null) {
      return;
    }
    try {
      // 使用 SQL 指纹替代原始 SQL 作为 tag，避免高基数标签导致 Prometheus 内存爆炸
      String fingerprint = SqlFingerprint.fingerprint(sql);
      Timer.builder("jdbc.slow.sql")
          .description("Slow SQL execution duration")
          .tag("sql_fingerprint", fingerprint)
          .register(meterRegistry)
          .record(Duration.ofMillis(elapsed));

      Counter.builder("jdbc.slow.sql.count")
          .description("Slow SQL execution count")
          .tag("sql_fingerprint", fingerprint)
          .register(meterRegistry)
          .increment();
    } catch (Exception e) {
      log.debug("慢SQL指标采集失败: {}", e.getMessage());
    }
  }

  /** 判断是否应该审计该 SQL 类型 */
  private boolean shouldAudit(SqlCommandType commandType) {
    return switch (commandType) {
      case SELECT -> auditSelect;
      case INSERT -> auditInsert;
      case UPDATE -> auditUpdate;
      case DELETE -> auditDelete;
      default -> false;
    };
  }

  /** 判断是否应该排除该 SQL */
  private boolean shouldExclude(String methodId, String sql) {
    if (excludeMethods != null && !excludeMethods.isEmpty() && methodId != null) {
      for (String excludeMethod : excludeMethods) {
        if (methodId.contains(excludeMethod)) {
          return true;
        }
      }
    }

    if (excludeTables != null && !excludeTables.isEmpty() && sql != null) {
      String lowerSql = sql.toLowerCase();
      for (String table : excludeTables) {
        if (lowerSql.contains(table.toLowerCase())) {
          return true;
        }
      }
    }

    return false;
  }

  /** 记录 SQL 审计日志 */
  private void logAudit(
      String methodId, String sql, Object parameter, SqlCommandType commandType, long elapsed) {
    try {
      String timestamp = LocalDateTime.now().format(FORMATTER);

      String parameters = "";
      if (logParameters && parameter != null) {
        parameters = formatParameters(parameter);
      }

      // 获取实际影响行数：从 AFFECTED_ROWS ThreadLocal 中读取，无值则显示 "N/A"
      Integer affectedRows = AFFECTED_ROWS.get();
      String rowsInfo = affectedRows != null ? String.valueOf(affectedRows) : "N/A";

      StringBuilder auditLog = new StringBuilder();
      auditLog
          .append("[SQL审计] ")
          .append(timestamp)
          .append(" | ")
          .append(commandType.name())
          .append(" | ")
          .append(methodId)
          .append(" | ")
          .append(elapsed)
          .append("ms | ")
          .append("影响行数: ")
          .append(rowsInfo);

      if (!parameters.isEmpty()) {
        auditLog.append(" | 参数: ").append(parameters);
      }

      auditLog.append(" | SQL: ").append(sql != null ? sql : "N/A");

      AUDIT_LOG.info(auditLog.toString());
    } catch (Exception e) {
      AUDIT_LOG.warn("SQL审计日志记录失败", e);
    }
  }

  /** 格式化 SQL 参数 */
  private String formatParameters(Object parameter) {
    try {
      if (parameter == null) {
        return "[]";
      }

      if (parameter instanceof Map) {
        return parameter.toString();
      }

      if (parameter instanceof String
          || parameter instanceof Number
          || parameter instanceof Boolean) {
        return "[" + parameter + "]";
      }

      String paramStr = parameter.toString();
      if (paramStr.length() > maxParameterLength) {
        paramStr = paramStr.substring(0, maxParameterLength) + "...(已截断)";
      }

      return paramStr;
    } catch (Exception e) {
      return "[参数格式化失败]";
    }
  }

  // ----- MyBatis Interceptor interface (计时 + 影响行数捕获) -----

  /**
   * 拦截 Executor.query / Executor.update，在真实执行前后计时， 并在执行完成后统一处理慢 SQL 检测与 SQL 审计。
   *
   * <p><b>计时语义：</b>耗时 = {@code proceed()} 前后 {@code System.nanoTime()} 差值， 包含完整数据库往返时间，替代原先在 {@code
   * beforePrepare} 阶段计时的错误窗口 （该阶段发生在 SQL 真正执行之前，无法反映真实执行耗时）。
   */
  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    long startNanos = System.nanoTime();
    try {
      Object result = invocation.proceed();
      long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

      // 从执行结果中提取影响行数
      if (result instanceof List<?> list) {
        // query 返回 List，size 即为返回行数
        AFFECTED_ROWS.set(list.size());
      } else if (result instanceof Integer intResult) {
        // update 返回 Integer，即 JDBC 影响行数
        AFFECTED_ROWS.set(intResult);
      } else if (result instanceof int[] intArray) {
        // 批量 update 返回 int[]，取总和
        int sum = 0;
        for (int rows : intArray) {
          sum += rows;
        }
        AFFECTED_ROWS.set(sum);
      }

      // SQL 真实执行完成后统一处理慢 SQL 与审计
      TimingContext timing = TIMING_CONTEXT.get();
      if (timing != null) {
        if (slowSqlEnabled && elapsedMillis > slowSqlThresholdMillis) {
          handleSlowSql(timing.sqlId, elapsedMillis, timing.finalSql);
        }
        if (auditEnabled
            && shouldAudit(timing.commandType)
            && !shouldExclude(timing.sqlId, timing.finalSql)) {
          logAudit(
              timing.sqlId, timing.finalSql, timing.parameter, timing.commandType, elapsedMillis);
        }
      }
      return result;
    } catch (Throwable t) {
      throw t;
    } finally {
      // 执行结束（成功或失败）统一清理 ThreadLocal，避免线程池复用泄漏
      TIMING_CONTEXT.remove();
      AFFECTED_ROWS.remove();
    }
  }

  /** 包装 Executor，实现 SQL 执行后的影响行数捕获。 */
  @Override
  public Object plugin(Object target) {
    if (target instanceof Executor) {
      return Plugin.wrap(target, this);
    }
    return target;
  }

  @Override
  public void setProperties(Properties properties) {
    // 无额外配置项
  }

  // ----- Getters / Setters -----

  public boolean isSlowSqlEnabled() {
    return slowSqlEnabled;
  }

  public void setSlowSqlEnabled(boolean slowSqlEnabled) {
    this.slowSqlEnabled = slowSqlEnabled;
  }

  public long getSlowSqlThresholdMillis() {
    return slowSqlThresholdMillis;
  }

  public void setSlowSqlThresholdMillis(long slowSqlThresholdMillis) {
    this.slowSqlThresholdMillis = slowSqlThresholdMillis;
  }

  public long getAlertThresholdMillis() {
    return alertThresholdMillis;
  }

  public void setAlertThresholdMillis(long alertThresholdMillis) {
    this.alertThresholdMillis = alertThresholdMillis;
  }

  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  public void setMeterRegistry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public boolean isAuditEnabled() {
    return auditEnabled;
  }

  public void setAuditEnabled(boolean auditEnabled) {
    this.auditEnabled = auditEnabled;
  }

  public boolean isAuditSelect() {
    return auditSelect;
  }

  public void setAuditSelect(boolean auditSelect) {
    this.auditSelect = auditSelect;
  }

  public boolean isAuditInsert() {
    return auditInsert;
  }

  public void setAuditInsert(boolean auditInsert) {
    this.auditInsert = auditInsert;
  }

  public boolean isAuditUpdate() {
    return auditUpdate;
  }

  public void setAuditUpdate(boolean auditUpdate) {
    this.auditUpdate = auditUpdate;
  }

  public boolean isAuditDelete() {
    return auditDelete;
  }

  public void setAuditDelete(boolean auditDelete) {
    this.auditDelete = auditDelete;
  }

  public boolean isLogParameters() {
    return logParameters;
  }

  public void setLogParameters(boolean logParameters) {
    this.logParameters = logParameters;
  }

  public int getMaxParameterLength() {
    return maxParameterLength;
  }

  public void setMaxParameterLength(int maxParameterLength) {
    this.maxParameterLength = maxParameterLength;
  }

  public List<String> getExcludeTables() {
    return excludeTables;
  }

  public void setExcludeTables(List<String> excludeTables) {
    this.excludeTables = excludeTables;
  }

  public List<String> getExcludeMethods() {
    return excludeMethods;
  }

  public void setExcludeMethods(List<String> excludeMethods) {
    this.excludeMethods = excludeMethods;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  /**
   * 计时上下文
   *
   * <p>{@code startNanos} 记录 {@code willDoQuery/willDoUpdate} 阶段起始纳秒时间， 供 {@code intercept}
   * 在真实执行完成后计算耗时； {@code finalSql} 由 {@code beforePrepare} 写入 SQL 改写后的最终形态。
   */
  private static class TimingContext {
    final long startNanos;
    final String sqlId;
    final SqlCommandType commandType;
    final Object parameter;
    String finalSql;

    TimingContext(long startNanos, String sqlId, SqlCommandType commandType, Object parameter) {
      this.startNanos = startNanos;
      this.sqlId = sqlId;
      this.commandType = commandType;
      this.parameter = parameter;
    }
  }
}
