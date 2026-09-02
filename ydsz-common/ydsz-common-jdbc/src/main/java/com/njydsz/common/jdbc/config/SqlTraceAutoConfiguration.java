package com.njydsz.common.jdbc.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.njydsz.common.jdbc.interceptor.SqlTraceInnerInterceptor;

/**
 * SQL 链路追踪自动配置（慢 SQL + 审计一体化）
 *
 * <p>向 {@link MybatisPlusInterceptor} 注入 {@link SqlTraceInnerInterceptor}， 统一接管慢 SQL 检测与 SQL
 * 审计功能。相较于分别注册两个拦截器，该方案：
 *
 * <ul>
 *   <li>减少一次 SQL 解析与 {@code getBoundSql} 调用
 *   <li>避免 ThreadLocal 在多个拦截器之间的重复清理/设置
 *   <li>通过 {@link org.springframework.core.Ordered} 控制拦截器在链中的位置
 * </ul>
 *
 * <p>触发条件（满足任一即生效）：
 *
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     enabled: true
 *     slow-sql:
 *       enabled: true
 *     sql-audit:
 *       enabled: true
 * }</pre>
 *
 * <p>配置项使用 {@link SlowSqlProperties} 与 {@link SqlAuditProperties}， 对已有 {@code ydsz.jdbc.slow-sql.*}
 * 和 {@code ydsz.jdbc.sql-audit.*} 配置完全兼容。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see SqlTraceInnerInterceptor
 * @see MybatisPlusConfiguration
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnExpression(
    "${ydsz.jdbc.enabled:true} and (${ydsz.jdbc.slow-sql.enabled:false} or ${ydsz.jdbc.sql-audit.enabled:false})")
@EnableConfigurationProperties({
  JdbcProperties.class,
  SlowSqlProperties.class,
  SqlAuditProperties.class
})
public class SqlTraceAutoConfiguration {

  /**
   * 构造 SQL 链路追踪自动配置
   *
   * @param mybatisPlusInterceptor MyBatis-Plus 拦截器链
   * @param jdbcProperties JDBC 基础配置属性
   * @param slowSqlProperties 慢 SQL 配置属性
   * @param sqlAuditProperties SQL 审计配置属性
   * @param meterRegistryProvider Micrometer 指标注册表（可选）
   */
  public SqlTraceAutoConfiguration(
      MybatisPlusInterceptor mybatisPlusInterceptor,
      JdbcProperties jdbcProperties,
      SlowSqlProperties slowSqlProperties,
      SqlAuditProperties sqlAuditProperties,
      ObjectProvider<MeterRegistry> meterRegistryProvider) {
    SqlTraceInnerInterceptor interceptor = findOrCreateTraceInterceptor(mybatisPlusInterceptor);

    // 慢 SQL 配置
    boolean slowSqlActive = Boolean.TRUE.equals(slowSqlProperties.isEnabled());
    interceptor.setSlowSqlEnabled(slowSqlActive || interceptor.isSlowSqlEnabled());
    interceptor.setSlowSqlThresholdMillis(slowSqlProperties.getThresholdMillis());
    interceptor.setAlertThresholdMillis(slowSqlProperties.getAlertThresholdMillis());

    // SQL 审计配置
    boolean auditActive = Boolean.TRUE.equals(sqlAuditProperties.isEnabled());
    interceptor.setAuditEnabled(auditActive || interceptor.isAuditEnabled());
    interceptor.setAuditSelect(sqlAuditProperties.isAuditSelect());
    interceptor.setAuditInsert(sqlAuditProperties.isAuditInsert());
    interceptor.setAuditUpdate(sqlAuditProperties.isAuditUpdate());
    interceptor.setAuditDelete(sqlAuditProperties.isAuditDelete());
    interceptor.setLogParameters(sqlAuditProperties.isLogParameters());
    interceptor.setMaxParameterLength(sqlAuditProperties.getMaxParameterLength());
    interceptor.setExcludeTables(sqlAuditProperties.getExcludeTables());
    interceptor.setExcludeMethods(sqlAuditProperties.getExcludeMethods());

    // Micrometer 指标注册表
    MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
    if (meterRegistry != null) {
      interceptor.setMeterRegistry(meterRegistry);
    }

    log.info(
        "SQL链路追踪已启用 (慢SQL={}, 审计={}), 慢SQL阈值: {}ms, 告警阈值: {}ms",
        interceptor.isSlowSqlEnabled(),
        interceptor.isAuditEnabled(),
        slowSqlProperties.getThresholdMillis(),
        slowSqlProperties.getAlertThresholdMillis());
  }

  /**
   * 查找或创建并注册 SqlTraceInnerInterceptor
   *
   * <p>如果拦截器链中已存在该拦截器（例如其他自动配置提前注册），则直接复用， 避免重复添加导致同一条 SQL 被多次追踪。
   *
   * @param mybatisPlusInterceptor MyBatis-Plus 拦截器链
   * @return SqlTraceInnerInterceptor 实例
   */
  private SqlTraceInnerInterceptor findOrCreateTraceInterceptor(
      MybatisPlusInterceptor mybatisPlusInterceptor) {
    for (Object inner : mybatisPlusInterceptor.getInterceptors()) {
      if (inner instanceof SqlTraceInnerInterceptor traceInterceptor) {
        return traceInterceptor;
      }
    }
    SqlTraceInnerInterceptor interceptor = new SqlTraceInnerInterceptor();
    // 置于拦截器链最前端，确保最早记录开始时间
    mybatisPlusInterceptor.getInterceptors().add(0, interceptor);
    return interceptor;
  }
}
