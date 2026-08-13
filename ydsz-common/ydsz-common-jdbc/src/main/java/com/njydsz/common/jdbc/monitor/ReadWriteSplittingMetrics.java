package com.njydsz.common.jdbc.monitor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
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
 *   <li>{@code ydsz.jdbc.sql.execution.time} — SQL 执行耗时 Timer（Summary 分位，tag: datasource, sql_type）</li>
 *   <li>{@code ydsz.jdbc.sql.execution.summary} — SQL 执行耗时分布 Histogram（可聚合，tag: datasource, sql_type）</li>
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

    /**
     * Counter 缓存，避免每次路由都重新注册 Micrometer Counter。
     *
     * <p>Micrometer 的 {@code Counter.builder().register()} 本身是幂等操作（相同 name + tags 返回同一实例），
     * 但每次都构建 Tags 对象和调用链仍有开销。缓存后可将每次路由降低到一次 HashMap lookup。
     */
    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    /**
     * DistributionSummary 缓存（Histogram），用于记录 SQL 执行耗时分布。
     *
     * <p>启用 {@code publishPercentileHistogram()} 后，Grafana 可通过
     * {@code histogram_quantile()} 查询任意分位数（如 P50/P95/P99），
     * 弥补 Prometheus Summary 分位数无法跨实例聚合的缺陷。
     */
    private final ConcurrentMap<String, DistributionSummary> summaryCache = new ConcurrentHashMap<>();

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
            String cacheKey = "route.total:" + sanitizeTag(datasource) + ":" + sanitizeTag(sqlType) + ":" + sanitizeTag(routeReason);
            Counter counter = counterCache.computeIfAbsent(cacheKey, k ->
                    Counter.builder(PREFIX + ".route.total")
                            .description("读写分离路由总次数")
                            .tags(Tags.of(
                                    "datasource", sanitizeTag(datasource),
                                    "sql_type", sanitizeTag(sqlType),
                                    "route_reason", sanitizeTag(routeReason)))
                            .register(meterRegistry)
            );
            counter.increment();
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
            String cacheKey = "route.transaction_forced:" + sanitizeTag(sqlType);
            Counter counter = counterCache.computeIfAbsent(cacheKey, k ->
                    Counter.builder(PREFIX + ".route.transaction_forced")
                            .description("事务强制路由到主库次数（@Transactional 中 SELECT 强制走主库）")
                            .tags("sql_type", sanitizeTag(sqlType))
                            .register(meterRegistry)
            );
            counter.increment();
        } catch (Exception e) {
            log.debug("Failed to record transaction forced metric", e);
        }
    }

    /**
     * 记录 SQL 执行耗时
     *
     * <p>同时写入 Timer（Summary 分位数）和 DistributionSummary（Histogram 分布），
     * 使得 Grafana 可通过 Histogram 计算任意聚合分位数。
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

        // 同步写入 DistributionSummary（Histogram），便于 Grafana 聚合计算
        recordSqlExecutionSummary(datasource, sqlType,
                TimeUnit.NANOSECONDS.toMillis(duration));
    }

    /**
     * 记录 SQL 执行耗时分布（Histogram）
     *
     * <p>与 {@link #recordSqlExecutionTime} 同步调用，额外写入 Histogram 类型指标。
     * 启用 {@code publishPercentileHistogram()} 后可计算 P50/P95/P99 等任意分位数。
     *
     * <p>Prometheus 查询示例：
     * <pre>
     * histogram_quantile(0.95, sum(rate(ydsz.jdbc.sql.execution.summary_bucket[5m])) by (le, datasource))
     * histogram_quantile(0.99, sum(rate(ydsz.jdbc.sql.execution.summary_bucket[5m])) by (le, datasource))
     * </pre>
     *
     * @param datasource  数据源名称
     * @param sqlType     SQL 类型（SELECT/INSERT/UPDATE/DELETE）
     * @param durationMs  执行耗时（毫秒）
     */
    public void recordSqlExecutionSummary(String datasource, String sqlType, long durationMs) {
        try {
            String cacheKey = "sql.execution.summary:" + sanitizeTag(datasource) + ":" + sanitizeTag(sqlType);
            DistributionSummary summary = summaryCache.computeIfAbsent(cacheKey, k ->
                    DistributionSummary.builder(PREFIX + ".sql.execution.summary")
                            .description("SQL 执行耗时分布（Histogram）")
                            .tags(Tags.of(
                                    "datasource", sanitizeTag(datasource),
                                    "sql_type", sanitizeTag(sqlType)))
                            .publishPercentileHistogram()
                            .baseUnit("milliseconds")
                            .register(meterRegistry)
            );
            summary.record(durationMs);
        } catch (Exception e) {
            log.debug("Failed to record SQL execution summary metric", e);
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
