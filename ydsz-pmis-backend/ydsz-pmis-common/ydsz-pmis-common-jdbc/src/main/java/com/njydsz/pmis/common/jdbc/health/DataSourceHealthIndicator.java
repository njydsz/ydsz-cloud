package com.njydsz.pmis.common.jdbc.health;

import javax.sql.DataSource;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据源健康检查指示器
 *
 * <p>暴露 HikariCP 连接池的健康指标，包括：
 * <ul>
 *   <li>活跃连接数（active）</li>
 *   <li>空闲连接数（idle）</li>
 *   <li>等待连接线程数（waiting）</li>
 *   <li>最大连接池大小（max）</li>
 *   <li>最小空闲连接数（min）</li>
 * </ul>
 *
 * <p><b>设计说明：</b>健康检查仅读取 HikariPoolMXBean 的指标数据，不实际获取数据库连接，
 * 避免在连接池高负载时加剧连接竞争。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class DataSourceHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DataSourceHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
            return Health.unknown().build();
        }
        try {
            HikariDataSource pool = hikariDataSource;
            var mxBean = pool.getHikariPoolMXBean();
            int active = mxBean.getActiveConnections();
            int idle = mxBean.getIdleConnections();
            int threadsAwaitingConnection = mxBean.getThreadsAwaitingConnection();
            int max = pool.getMaximumPoolSize();
            int min = pool.getMinimumIdle();

            // 计算连接池利用率
            double utilization = max > 0 ? (double) active / max : 0.0;

            Health.Builder builder = Health.up()
                    .withDetail("active", active)
                    .withDetail("idle", idle)
                    .withDetail("waiting", threadsAwaitingConnection)
                    .withDetail("max", max)
                    .withDetail("min", min)
                    .withDetail("utilization", String.format("%.2f%%", utilization * 100));

            // 如果等待线程数过多，标记为降级
            if (threadsAwaitingConnection > max / 2) {
                builder.down()
                        .withDetail("status", "DEGRADED")
                        .withDetail("reason", "High connection wait queue");
                return builder.build();
            }

            // 如果利用率超过 90%，标记为降级
            if (utilization > 0.9) {
                builder.down()
                        .withDetail("status", "DEGRADED")
                        .withDetail("reason", "Connection pool near exhaustion");
                return builder.build();
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
