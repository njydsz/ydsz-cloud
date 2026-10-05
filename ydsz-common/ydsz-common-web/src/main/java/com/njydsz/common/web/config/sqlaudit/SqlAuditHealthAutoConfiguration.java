package com.njydsz.common.web.config.sqlaudit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.njydsz.common.jdbc.config.DataPermissionConfiguration;
import com.njydsz.common.jdbc.config.MybatisPlusConfiguration;
import com.njydsz.common.web.health.SqlAuditHealthIndicator;

/**
 * SQL 数据权限拦截器自检自动配置
 *
 * <p>当满足以下条件时启用：
 *
 * <ul>
 *   <li>classpath 存在 {@link MybatisPlusInterceptor}（即 {@code ydsz-common-jdbc} 已引入）
 *   <li>{@code ydsz-common.jdbc.sql-audit.enabled=true}（显式开启自检）
 *   <li>Spring 容器中存在 {@link MybatisPlusInterceptor} Bean（即 JDBC 模块实际参与装配）
 * </ul>
 *
 * <p>未引入 {@code ydsz-common-jdbc} 的模块（gateway / cronjob 等）不受影响：该配置类不会激活。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see SqlAuditHealthIndicator
 * @see SqlAuditProperties
 */
@AutoConfiguration
@AutoConfigureAfter(MybatisPlusConfiguration.class)
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnBean(MybatisPlusInterceptor.class)
@ConditionalOnProperty(
    prefix = "ydsz-common.jdbc.sql-audit",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
@EnableConfigurationProperties(SqlAuditProperties.class)
public class SqlAuditHealthAutoConfiguration {

  /**
   * 注册 SQL 数据权限拦截器自检 HealthIndicator Bean
   *
   * @param mybatisPlusInterceptor MyBatis-Plus 拦截器链
   * @param dataPermissionConfigurationProvider 数据权限配置 provider（弱依赖）
   * @return SqlAuditHealthIndicator 实例
   */
  @Bean
  public SqlAuditHealthIndicator sqlAuditHealthIndicator(
      MybatisPlusInterceptor mybatisPlusInterceptor,
      ObjectProvider<DataPermissionConfiguration> dataPermissionConfigurationProvider) {
    return new SqlAuditHealthIndicator(
        mybatisPlusInterceptor, dataPermissionConfigurationProvider);
  }
}
