package com.njydsz.common.jdbc.actuator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.jdbc.monitor.SlaveLatencyMonitor;
import com.njydsz.common.jdbc.monitor.SqlAstCache;

import lombok.extern.slf4j.Slf4j;

/**
 * JDBC 模块健康指标自动配置
 *
 * <p>暴露以下运行时状态供运维监控：
 * <ul>
 *   <li>从库延迟监控健康状态</li>
 *   <li>SQL 解析缓存统计</li>
 * </ul>
 *
 * <p>端点路径：{@code /actuator/health/jdbc}
 *
 * <p>触发条件：
 * <ul>
 *   <li>classpath 存在 {@code spring-boot-actuator} / {@code spring-boot-health}</li>
 *   <li>（可选）ydsz.jdbc.health.enabled=true（默认启用）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.8.0
 * @see SlaveLatencyMonitor
 * @see SqlAstCache
 */
@Slf4j
@Configuration
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.jdbc.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JdbcHealthIndicator {

    /**
     * 注册 JDBC 健康指标 Bean
     *
     * @param latencyMonitor 从库延迟监控器（可为 null，表示未启用读写分离监控）
     * @return HealthIndicator 实例
     */
    @Bean
    public HealthIndicator jdbcHealthIndicator(ObjectProvider<SlaveLatencyMonitor> latencyMonitor) {
        return new JdbcHealthIndicatorImpl(latencyMonitor.getIfAvailable());
    }

    /**
     * 健康指标实现
     */
    @Slf4j
    @RequiredArgsConstructor
    static class JdbcHealthIndicatorImpl implements HealthIndicator {

        private final SlaveLatencyMonitor latencyMonitor;

        @Override
        public Health health() {
            Health.Builder builder = Health.up();
            try {
                appendLatencyMonitorInfo(builder);
                appendCacheInfo(builder);
            } catch (Exception e) {
                log.warn("JDBC 健康指标采集异常: {}", e.getMessage());
                builder.down().withDetail("error", e.getMessage());
            }
            return builder.build();
        }

        /**
         * 追加从库延迟监控信息
         */
        private void appendLatencyMonitorInfo(Health.Builder builder) {
            if (latencyMonitor == null) {
                return;
            }
            Set<String> healthySlaves = latencyMonitor.getHealthySlaves();
            Map<String, Object> rwInfo = new LinkedHashMap<>();
            rwInfo.put("healthySlaves", healthySlaves);
            rwInfo.put("healthyCount", healthySlaves != null ? healthySlaves.size() : 0);
            builder.withDetail("readWriteSplitting", rwInfo);
        }

        /**
         * 追加 SQL 解析缓存统计信息
         */
        private void appendCacheInfo(Health.Builder builder) {
            Map<String, Object> cacheStats = new LinkedHashMap<>();
            cacheStats.put("size", SqlAstCache.getInstance().size());
            cacheStats.put("maxSize", SqlAstCache.getInstance().maxSize());
            builder.withDetail("sqlAstCache", cacheStats);
        }
    }
}
