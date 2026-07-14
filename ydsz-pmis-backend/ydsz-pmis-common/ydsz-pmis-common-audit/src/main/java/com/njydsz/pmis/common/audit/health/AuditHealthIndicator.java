package com.njydsz.pmis.common.audit.health;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.pmis.common.audit.config.AuditProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 审计模块健康检查指示器
 * <p>
 * 通过 Spring Boot Actuator 暴露 {@code /actuator/health/audit} 端点，
 * 用于监控审计存储（JDBC）的可用性。
 * </p>
 *
 * <p><b>检测逻辑：</b></p>
 * <ul>
 *   <li>从 DataSource 获取连接并验证（{@code connection.isValid(2)}）</li>
 *   <li>返回审计存储类型与连接耗时（毫秒）</li>
 *   <li>异常时返回 DOWN 状态并附带错误信息</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class AuditHealthIndicator implements HealthIndicator {

    /** 数据源 */
    private final DataSource dataSource;

    /** 审计配置属性 */
    private final AuditProperties auditProperties;

    /**
     * 构造审计健康检查指示器
     *
     * @param dataSource      数据源
     * @param auditProperties 审计配置属性
     */
    public AuditHealthIndicator(DataSource dataSource, AuditProperties auditProperties) {
        this.dataSource = dataSource;
        this.auditProperties = auditProperties;
    }

    /**
     * 执行健康检查
     *
     * @return Health 状态（UP / DOWN）及明细
     */
    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();
            try (Connection connection = dataSource.getConnection()) {
                long responseTime = System.currentTimeMillis() - startTime;

                if (connection.isValid(2)) {
                    return Health.up()
                            .withDetail("module", "audit")
                            .withDetail("storageType", auditProperties.getStorageType())
                            .withDetail("jdbc", "connected")
                            .withDetail("responseTimeMs", responseTime)
                            .build();
                }

                return Health.down()
                        .withDetail("module", "audit")
                        .withDetail("storageType", auditProperties.getStorageType())
                        .withDetail("jdbc", "connection not valid")
                        .build();
            }
        } catch (Exception e) {
            log.error("【审计模块】健康检查失败 | error={}", e.getMessage());
            return Health.down()
                    .withDetail("module", "audit")
                    .withDetail("storageType", auditProperties.getStorageType())
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
