package com.njydsz.pmis.common.jdbc.interceptor;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * SQL 链路追踪拦截器（慢 SQL + 审计一体化）
 *
 * <p>将慢 SQL 检测与 SQL 审计合并为单个拦截器，避免对同一条 SQL 进行多次解析和多次
 * {@link MappedStatement#getBoundSql(Object)} 调用，降低 MyBatis 拦截器链开销。
 *
 * <p>执行阶段说明：
 * <ul>
 *   <li>{@code willDoQuery/willDoUpdate}：记录 SQL 开始时间，初始化 ThreadLocal 上下文</li>
 *   <li>{@code beforePrepare}：此时所有前置拦截器已完成 SQL 改写，提取最终 SQL 并一次性完成
 *       慢 SQL 判定、指标采集和审计日志输出</li>
 * </ul>
 *
 * <p>通过实现 {@link Ordered} 接口，建议将该拦截器置于拦截器链最前端，确保最早记录开始时间。
 * 默认优先级 {@link Ordered#HIGHEST_PRECEDENCE} + 100。
 *
 * <p>替代关系：
 * <ul>
 *   <li>替代 {@link SlowSqlInnerInterceptor}（慢 SQL 检测）</li>
 *   <li>替代 {@link SqlAuditInterceptor}（SQL 审计）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see SlowSqlInnerInterceptor
 * @see SqlAuditInterceptor
 */
@Slf4j
public class SqlTraceInnerInterceptor implements InnerInterceptor, Ordered, MeterBinder {

    /**
     * ThreadLocal 存储当前查询/更新操作的开始时间与 SQL 标识
     */
    private static final ThreadLocal<TimingContext> TIMING_CONTEXT = new ThreadLocal<>();

    /**
     * SQL 审计专用日志，便于独立配置 appender
     */
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("sql.audit");

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    public SqlTraceInnerInterceptor() {
    }

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

    @SuppressWarnings("rawtypes")
    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter,
                               RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        startTiming(ms, parameter);
        return true;
    }

    @Override
    public boolean willDoUpdate(Executor executor, MappedStatement ms, Object parameter) throws SQLException {
        startTiming(ms, parameter);
        return true;
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        TimingContext timing = TIMING_CONTEXT.get();
        if (timing == null) {
            return;
        }
        try {
            long elapsed = System.currentTimeMillis() - timing.startTime;
            SqlCommandType commandType = timing.commandType;
            String sqlId = timing.sqlId;

            BoundSql boundSql = sh.getBoundSql();
            String rawSql = extractRawSql(sh, boundSql);
            String cleanedSql = rawSql != null ? rawSql.replaceAll("\\s+", " ").trim() : null;

            // 慢 SQL 检测与审计共用同一次 SQL 提取结果
            if (slowSqlEnabled && elapsed > slowSqlThresholdMillis) {
                handleSlowSql(sqlId, elapsed, rawSql);
            }
            if (auditEnabled && shouldAudit(commandType) && !shouldExclude(sqlId, cleanedSql)) {
                logAudit(sqlId, cleanedSql, boundSql, timing.parameter, commandType, elapsed);
            }
        } finally {
            TIMING_CONTEXT.remove();
        }
    }

    /**
     * 启动计时上下文
     */
    private void startTiming(MappedStatement ms, Object parameter) {
        // 清理上一个请求可能遗留的 ThreadLocal，防止线程池复用时泄漏
        TIMING_CONTEXT.remove();
        if (ms == null) {
            return;
        }
        SqlCommandType commandType = ms.getSqlCommandType();
        if (commandType == null || commandType == SqlCommandType.FLUSH) {
            return;
        }
        TIMING_CONTEXT.set(new TimingContext(System.currentTimeMillis(), ms.getId(), commandType, parameter));
    }

    /**
     * 处理慢 SQL 检测、告警和指标采集
     */
    private void handleSlowSql(String sqlId, long elapsed, String rawSql) {
        String displaySql = truncateSql(rawSql, 200);
        if (displaySql != null) {
            log.warn("慢SQL检测 | SQL_ID: {} | SQL: {} | 耗时: {}ms | 阈值: {}ms",
                    sqlId, displaySql, elapsed, slowSqlThresholdMillis);
        } else {
            log.warn("慢SQL检测 | SQL_ID: {} | 耗时: {}ms | 阈值: {}ms",
                    sqlId, elapsed, slowSqlThresholdMillis);
        }

        if (elapsed > alertThresholdMillis) {
            log.error("慢SQL告警 | SQL_ID: {} | 耗时: {}ms | 告警阈值: {}ms | 请及时优化SQL",
                    sqlId, elapsed, alertThresholdMillis);
            logCallStack();
        }

        recordSlowSqlMetric(displaySql, elapsed);
    }

    /**
     * 打印当前调用堆栈，辅助定位慢 SQL 调用位置
     */
    private void logCallStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder("慢SQL调用堆栈:");
        for (int i = 3; i < Math.min(stack.length, 15); i++) {
            sb.append("\n\tat ").append(stack[i].toString());
        }
        log.warn(sb.toString());
    }

    /**
     * 从 StatementHandler 提取原始 SQL 语句
     */
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

    /**
     * 截断 SQL 用于日志和指标标签
     */
    private String truncateSql(String sql, int maxLength) {
        if (sql == null || sql.isEmpty()) {
            return null;
        }
        return sql.length() > maxLength ? sql.substring(0, maxLength) + "..." : sql;
    }

    /**
     * 记录慢 SQL 指标到 Micrometer
     */
    private void recordSlowSqlMetric(String sql, long elapsed) {
        if (meterRegistry == null) {
            return;
        }
        try {
            String sqlTag = sql != null ? sql : "unknown";
            Timer.builder("jdbc.slow.sql")
                    .description("Slow SQL execution duration")
                    .tag("sql", sqlTag)
                    .register(meterRegistry)
                    .record(Duration.ofMillis(elapsed));

            Counter.builder("jdbc.slow.sql.count")
                    .description("Slow SQL execution count")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.debug("慢SQL指标采集失败: {}", e.getMessage());
        }
    }

    /**
     * 判断是否应该审计该 SQL 类型
     */
    private boolean shouldAudit(SqlCommandType commandType) {
        return switch (commandType) {
            case SELECT -> auditSelect;
            case INSERT -> auditInsert;
            case UPDATE -> auditUpdate;
            case DELETE -> auditDelete;
            default -> false;
        };
    }

    /**
     * 判断是否应该排除该 SQL
     */
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

    /**
     * 记录 SQL 审计日志
     */
    private void logAudit(String methodId, String sql, BoundSql boundSql, Object parameter,
                          SqlCommandType commandType, long elapsed) {
        try {
            String timestamp = LocalDateTime.now().format(FORMATTER);

            String parameters = "";
            if (logParameters && parameter != null) {
                parameters = formatParameters(parameter, boundSql);
            }

            StringBuilder auditLog = new StringBuilder();
            auditLog.append("[SQL审计] ")
                    .append(timestamp).append(" | ")
                    .append(commandType.name()).append(" | ")
                    .append(methodId).append(" | ")
                    .append(elapsed).append("ms | ")
                    .append("0行");

            if (!parameters.isEmpty()) {
                auditLog.append(" | 参数: ").append(parameters);
            }

            auditLog.append(" | SQL: ").append(sql != null ? sql : "N/A");

            AUDIT_LOG.info(auditLog.toString());
        } catch (Exception e) {
            AUDIT_LOG.warn("SQL审计日志记录失败", e);
        }
    }

    /**
     * 格式化 SQL 参数
     */
    private String formatParameters(Object parameter, BoundSql boundSql) {
        try {
            if (parameter == null) {
                return "[]";
            }

            if (parameter instanceof Map) {
                return parameter.toString();
            }

            if (parameter instanceof String || parameter instanceof Number || parameter instanceof Boolean) {
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
     */
    private static class TimingContext {
        final long startTime;
        final String sqlId;
        final SqlCommandType commandType;
        final Object parameter;

        TimingContext(long startTime, String sqlId, SqlCommandType commandType, Object parameter) {
            this.startTime = startTime;
            this.sqlId = sqlId;
            this.commandType = commandType;
            this.parameter = parameter;
        }
    }
}
