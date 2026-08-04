package com.remisoft.common.jdbc.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.remisoft.common.jdbc.interceptor.SqlTraceInnerInterceptor;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * SQL 链路追踪自动配置（慢 SQL + 审计一体化）
 *
 * <p>向 {@link MybatisPlusInterceptor} 注入 {@link SqlTraceInnerInterceptor}，
 * 统一接管慢 SQL 检测与 SQL 审计功能。相较于分别注册两个拦截器，该方案：
 * <ul>
 *   <li>减少一次 SQL 解析与 {@code getBoundSql} 调用</li>
 *   <li>避免 ThreadLocal 在多个拦截器之间的重复清理/设置</li>
 *   <li>通过 {@link org.springframework.core.Ordered} 控制拦截器在链中的位置</li>
 * </ul>
 *
 * <p>触发条件（满足任一即生效）：
 * <pre>{@code
 * remi:
 *   jdbc:
 *     enabled: true
 *     slow-sql:
 *       enabled: true
 *     sql-audit:
 *       enabled: true
 * }</pre>
 *
 * <p>配置项沿用 {@link JdbcProperties.SlowSql} 与 {@link JdbcProperties.SqlAudit}，
 * 对已有 {@code remi.jdbc.slow-sql.*} 和 {@code remi.jdbc.sql-audit.*} 配置完全兼容。
 *
 * @author remi-team
 * @since 1.0.0
 * @see SqlTraceInnerInterceptor
 * @see MybatisPlusConfiguration
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnExpression("${remi.jdbc.enabled:true} and (${remi.jdbc.slow-sql.enabled:true} or ${remi.jdbc.sql-audit.enabled:true})")
@EnableConfigurationProperties(JdbcProperties.class)
public class SqlTraceAutoConfiguration {

    /**
     * 构造 SQL 链路追踪自动配置
     *
     * @param mybatisPlusInterceptor MyBatis-Plus 拦截器链
     * @param jdbcProperties         JDBC 配置属性
     * @param meterRegistryProvider  Micrometer 指标注册表（可选）
     */
    public SqlTraceAutoConfiguration(MybatisPlusInterceptor mybatisPlusInterceptor,
                                      JdbcProperties jdbcProperties,
                                      ObjectProvider<MeterRegistry> meterRegistryProvider) {
        JdbcProperties.SlowSql slowSql = jdbcProperties.getSlowSql();
        JdbcProperties.SqlAudit sqlAudit = jdbcProperties.getSqlAudit();

        SqlTraceInnerInterceptor interceptor = findOrCreateTraceInterceptor(mybatisPlusInterceptor);

        // 慢 SQL 配置
        boolean slowSqlActive = Boolean.TRUE.equals(slowSql.isEnabled());
        interceptor.setSlowSqlEnabled(slowSqlActive || interceptor.isSlowSqlEnabled());
        interceptor.setSlowSqlThresholdMillis(slowSql.getThresholdMillis());
        interceptor.setAlertThresholdMillis(slowSql.getAlertThresholdMillis());

        // SQL 审计配置
        boolean auditActive = Boolean.TRUE.equals(sqlAudit.isEnabled());
        interceptor.setAuditEnabled(auditActive || interceptor.isAuditEnabled());
        interceptor.setAuditSelect(sqlAudit.isAuditSelect());
        interceptor.setAuditInsert(sqlAudit.isAuditInsert());
        interceptor.setAuditUpdate(sqlAudit.isAuditUpdate());
        interceptor.setAuditDelete(sqlAudit.isAuditDelete());
        interceptor.setLogParameters(sqlAudit.isLogParameters());
        interceptor.setMaxParameterLength(sqlAudit.getMaxParameterLength());
        interceptor.setExcludeTables(sqlAudit.getExcludeTables());
        interceptor.setExcludeMethods(sqlAudit.getExcludeMethods());

        // Micrometer 指标注册表
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            interceptor.setMeterRegistry(meterRegistry);
        }

        log.info("SQL链路追踪已启用 (慢SQL={}, 审计={}), 慢SQL阈值: {}ms, 告警阈值: {}ms",
                interceptor.isSlowSqlEnabled(), interceptor.isAuditEnabled(),
                slowSql.getThresholdMillis(), slowSql.getAlertThresholdMillis());
    }

    /**
     * 查找或创建并注册 SqlTraceInnerInterceptor
     *
     * <p>如果拦截器链中已存在该拦截器（例如其他自动配置提前注册），则直接复用，
     * 避免重复添加导致同一条 SQL 被多次追踪。
     *
     * @param mybatisPlusInterceptor MyBatis-Plus 拦截器链
     * @return SqlTraceInnerInterceptor 实例
     */
    private SqlTraceInnerInterceptor findOrCreateTraceInterceptor(MybatisPlusInterceptor mybatisPlusInterceptor) {
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
