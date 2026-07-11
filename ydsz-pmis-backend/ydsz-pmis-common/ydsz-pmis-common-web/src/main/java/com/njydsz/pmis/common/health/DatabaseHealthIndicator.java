package com.njydsz.pmis.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
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

    /** 数据源，用于获取数据库连接执行健康检查 SQL */
    private final DataSource dataSource;

    /**
     * 构造器注入数据源
     *
     * @param dataSource Spring 管理的数据源
     */
    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行数据库健康检查：通过 {@code SELECT 1} 验证连接可用性
     *
     * <p>检查成功时返回 UP 状态，携带数据库产品名与版本信息；
     * 检查失败时返回 DOWN 状态，携带异常信息。
     *
     * @return 健康状态对象
     */
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