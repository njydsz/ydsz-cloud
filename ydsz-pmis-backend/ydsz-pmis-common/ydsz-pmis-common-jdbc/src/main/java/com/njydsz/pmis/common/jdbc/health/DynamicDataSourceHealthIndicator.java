package com.njydsz.pmis.common.jdbc.health;

import javax.sql.DataSource;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 动态多数据源健康检查指示器
 *
 * <p>当使用 baomidou dynamic-datasource 时，检查主数据源的连接池状态。
 * 仅读取 HikariPoolMXBean 指标，不获取连接。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class DynamicDataSourceHealthIndicator implements HealthIndicator {

    private final DynamicRoutingDataSource dynamicDataSource;

    public DynamicDataSourceHealthIndicator(DynamicRoutingDataSource dynamicDataSource) {
        this.dynamicDataSource = dynamicDataSource;
    }

    @Override
    public Health health() {
        DataSource primaryDs = dynamicDataSource.getDataSource("master");
        if (primaryDs instanceof HikariDataSource hikariDs) {
            try {
                var mxBean = hikariDs.getHikariPoolMXBean();
                int active = mxBean.getActiveConnections();
                int idle = mxBean.getIdleConnections();
                int max = hikariDs.getMaximumPoolSize();
                double utilization = max > 0 ? (double) active / max : 0.0;

                Health.Builder builder = Health.up()
                        .withDetail("master.active", active)
                        .withDetail("master.idle", idle)
                        .withDetail("master.max", max)
                        .withDetail("master.utilization", String.format("%.2f%%", utilization * 100));

                if (utilization > 0.9) {
                    builder.down()
                            .withDetail("master.status", "DEGRADED")
                            .withDetail("reason", "Connection pool near exhaustion");
                }

                return builder.build();
            } catch (Exception e) {
                return Health.down(e).withDetail("datasource", "master").build();
            }
        }
        return Health.unknown().withDetail("message", "Primary data source is not HikariDataSource").build();
    }
}
