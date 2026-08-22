package com.njydsz.common.jdbc.actuator;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.jdbc.monitor.SqlAstCache;

/**
 * JDBC 模块健康指标自动配置
 *
 * <p>暴露以下运行时状态供运维监控：
 *
 * <ul>
 *   <li>SQL 解析缓存统计（当前容量、最大容量）
 * </ul>
 *
 * <p>端点路径：{@code /actuator/health/jdbc}
 *
 * <p>触发条件：
 *
 * <ul>
 *   <li>classpath 存在 {@code spring-boot-health} 模块
 *   <li>{@code ydsz.jdbc.health.enabled=true}（默认启用）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SqlAstCache
 */
@Slf4j
@Configuration
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(
    prefix = "ydsz.jdbc.health",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JdbcHealthIndicator {

  /**
   * 注册 JDBC 健康指标 Bean
   *
   * @param sqlAstCache SQL 解析缓存实例
   * @return HealthIndicator 实例
   */
  @Bean
  public HealthIndicator jdbcHealthIndicator(SqlAstCache sqlAstCache) {
    return new JdbcHealthIndicatorImpl(sqlAstCache);
  }

  /** 健康指标实现 */
  @Slf4j
  @RequiredArgsConstructor
  static class JdbcHealthIndicatorImpl implements HealthIndicator {

    private final SqlAstCache sqlAstCache;

    @Override
    public Health health() {
      Health.Builder builder = Health.up();
      try {
        appendCacheInfo(builder);
      } catch (Exception e) {
        log.warn("JDBC 健康指标采集异常: {}", e.getMessage());
        builder.down().withDetail("error", e.getMessage());
      }
      return builder.build();
    }

    /** 追加 SQL 解析缓存统计信息 */
    private void appendCacheInfo(Health.Builder builder) {
      Map<String, Object> cacheStats = new LinkedHashMap<>();
      cacheStats.put("size", sqlAstCache.size());
      cacheStats.put("maxSize", sqlAstCache.maxSize());
      builder.withDetail("sqlAstCache", cacheStats);
    }
  }
}
