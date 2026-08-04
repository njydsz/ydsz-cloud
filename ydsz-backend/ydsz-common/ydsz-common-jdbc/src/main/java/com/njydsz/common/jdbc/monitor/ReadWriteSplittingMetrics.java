package com.njydsz.common.jdbc.monitor;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import lombok.extern.slf4j.Slf4j;

/**
 * 读写分离监控指标（Micrometer）
 *
 * <p>暴露的指标：
 * <ul>
 *   <li>{@code ydsz.jdbc.route.total} — 路由总次数（tag: datasource, sql_type, route_reason）</li>
 *   <li>{@code ydsz.jdbc.route.master} — 路由到主库次数（tag: sql_type）</li>
 *   <li>{@code ydsz.jdbc.route.slave} — 路由到从库次数（tag: datasource, sql_type）</li>
 *   <li>{@code ydsz.jdbc.route.transaction_forced} — 事务强制走主库次数</li>
 *   <li>{@code ydsz.jdbc.sql.execution.time} — SQL 执行耗时（tag: datasource, sql_type）</li>
 * </ul>
 *
 * <p>典型 Prometheus 查询示例：
 * <pre>
 * # 读写比例
 * sum(rate(ydsz.jdbc.route.total{route_reason="slave"}[5m]))
 * /
 * sum(rate(ydsz.jdbc.route.total[5m]))
 *
 * # 事务强制走主库比例
 * sum(rate(ydsz.jdbc.route.transaction_forced[5m]))
 * /
 * sum(rate(ydsz.jdbc.route.total[5m]))
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ReadWriteSplittingMetrics {

    private static final String PREFIX = "ydsz.jdbc";

    private final MeterRegistry meterRegistry;

    public ReadWriteSplittingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.debug("ReadWriteSplittingMetrics initialized");
    }

    /**
     * 记录一次路由决策
     *
     * @param datasource   目标数据源名称
     * @param sqlType      SQL 类型（SELECT/INSERT/UPDATE/DELETE）
     * @param routeReason  路由原因（slave / master / transaction_forced）
     */
    public void recordRoute(String datasource, String sqlType, String routeReason) {
        try {
            Counter.builder(PREFIX + ".route.total")
                    .description("读写分离路由总次数")
                    .tags(Tags.of(
                            "datasource", sanitizeTag(datasource),
                            "sql_type", sanitizeTag(sqlType),
                            "route_reason", sanitizeTag(routeReason)))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.debug("Failed to record route metric", e);
        }
    }

    /**
     * 记录事务强制路由到主库
     *
     * @param sqlType SQL 类型
     */
    public void recordTransactionForced(String sqlType) {
        try {
            Counter.builder(PREFIX + ".route.transaction_forced")
                    .description("事务强制路由到主库次数（@Transactional 中 SELECT 强制走主库）")
                    .tags("sql_type", sanitizeTag(sqlType))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.debug("Failed to record transaction forced metric", e);
        }
    }

    /**
     * 记录 SQL 执行耗时
     *
     * @param datasource 数据源名称
     * @param sqlType    SQL 类型
     * @param duration   执行耗时（纳秒）
     */
    public void recordSqlExecutionTime(String datasource, String sqlType, long duration) {
        try {
            Timer.builder(PREFIX + ".sql.execution.time")
                    .description("SQL 执行耗时（含网络往返）")
                    .tags(Tags.of(
                            "datasource", sanitizeTag(datasource),
                            "sql_type", sanitizeTag(sqlType)))
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry)
                    .record(duration, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.debug("Failed to record SQL execution time metric", e);
        }
    }

    /**
     * 清理 tag 值中的特殊字符（Prometheus tag 值不能包含空格、冒号、引号等）
     */
    private String sanitizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[\\s:\"']", "_").toLowerCase();
    }
}
