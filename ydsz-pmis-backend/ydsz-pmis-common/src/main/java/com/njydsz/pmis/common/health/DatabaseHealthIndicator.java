package com.njydsz.pmis.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 数据库健康检查指示器（P1-5）
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            return Health.up()
                    .withDetail("database", conn.getMetaData().getDatabaseProductName())
                    .withDetail("version", conn.getMetaData().getDatabaseProductVersion())
                    .build();
        } catch (Exception e) {
            log.warn("[HealthCheck] 数据库健康检查失败: {}", e.getMessage());
            return Health.down(e).build();
        }
    }
}