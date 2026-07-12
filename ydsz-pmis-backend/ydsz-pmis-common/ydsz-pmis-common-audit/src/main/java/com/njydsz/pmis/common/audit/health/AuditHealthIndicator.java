package com.njydsz.pmis.common.audit.health;

import com.njydsz.pmis.common.audit.config.AuditProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * 审计模块健康检查指示器
 *
 * <p>提供审计模块的健康状态检查，包括：
 * <ul>
 *   <li>数据源连通性</li>
 *   <li>审计功能启用状态</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class AuditHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final AuditProperties properties;

    public AuditHealthIndicator(DataSource dataSource, AuditProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        builder.withDetail("enabled", properties.isEnabled());
        builder.withDetail("async", properties.isAsync());
        builder.withDetail("storageType", properties.getStorageType());

        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                builder.withDetail("database", metaData.getDatabaseProductName());
                builder.withDetail("databaseVersion", metaData.getDatabaseProductVersion());
            } catch (Exception e) {
                builder.down().withDetail("error", "数据库连接失败: " + e.getMessage());
            }
        }

        return builder.build();
    }
}
