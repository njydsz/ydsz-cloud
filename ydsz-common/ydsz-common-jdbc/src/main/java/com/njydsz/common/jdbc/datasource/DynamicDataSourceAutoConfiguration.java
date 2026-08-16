package com.njydsz.common.jdbc.datasource;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.jdbc.annotation.DS;
import com.njydsz.common.jdbc.config.MultiDataSourcePoolCustomizer;
import com.njydsz.common.jdbc.constant.DataSourceConstants;
import com.njydsz.common.jdbc.health.DynamicDataSourceHealthIndicator;

/**
 * 动态数据源自动配置
 *
 * <p>启用后，支持通过 {@link DS} 注解动态切换数据源。
 *
 * <p><b>装配说明：</b>本配置在 {@link HikariCPConfiguration} 之后执行，
 * 将容器中已创建的默认数据源（HikariDataSource）作为路由数据源的默认目标 与 {@code master} 目标注册，保证 {@link
 * DynamicRoutingDataSource} 可直接使用； 同时注册 {@code @DS} 注解切面与动态多数据源健康检查。
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   jdbc:
 *     dynamic-datasource:
 *       enabled: true
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(MultiDataSourcePoolCustomizer.class)
@ConditionalOnProperty(
    prefix = "ydsz.jdbc.dynamic-datasource",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DynamicDataSourceAutoConfiguration {

  /**
   * 注册动态路由数据源
   *
   * <p>将容器中唯一的默认数据源作为路由默认目标与 master 目标， 使 {@link DynamicRoutingDataSource} 在单数据源场景下即可正常路由。
   *
   * @param defaultDataSourceProvider 默认数据源提供者
   * @return DynamicRoutingDataSource 实例
   */
  @Bean
  @ConditionalOnMissingBean(DynamicRoutingDataSource.class)
  public DynamicRoutingDataSource dynamicRoutingDataSource(
      ObjectProvider<DataSource> defaultDataSourceProvider) {
    DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource();

    DataSource defaultDataSource = defaultDataSourceProvider.getIfUnique();
    if (defaultDataSource != null && !(defaultDataSource instanceof DynamicRoutingDataSource)) {
      routingDataSource.setDefaultTargetDataSource(defaultDataSource);
      Map<Object, Object> targetDataSources = new HashMap<>(4);
      targetDataSources.put(DataSourceConstants.MASTER, defaultDataSource);
      routingDataSource.setTargetDataSources(targetDataSources);
      routingDataSource.afterPropertiesSet();
      log.info("动态路由数据源初始化完成，master={}", defaultDataSource.getClass().getSimpleName());
    } else {
      log.warn("未找到默认数据源，动态路由数据源将延迟到运行时注册（可通过 addDataSource 注入）");
    }
    return routingDataSource;
  }

  /**
   * 注册 @DS 注解拦截器
   *
   * @return DsAnnotationInterceptor 实例
   */
  @Bean
  @ConditionalOnMissingBean(DsAnnotationInterceptor.class)
  public DsAnnotationInterceptor dsAnnotationInterceptor() {
    return new DsAnnotationInterceptor();
  }

  /**
   * 注册 @DS 注解切面
   *
   * @param interceptor 拦截器
   * @return Advisor 实例
   */
  @Bean
  @ConditionalOnMissingBean(name = "dsAnnotationAdvisor")
  public Advisor dsAnnotationAdvisor(DsAnnotationInterceptor interceptor) {
    AnnotationMatchingPointcut pointcut = new AnnotationMatchingPointcut(DS.class, DS.class, true);
    return new DefaultPointcutAdvisor(pointcut, interceptor);
  }

  /**
   * 注册动态多数据源健康检查
   *
   * @param routingDataSource 动态路由数据源
   * @return DynamicDataSourceHealthIndicator 实例
   */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(DynamicDataSourceHealthIndicator.class)
  public DynamicDataSourceHealthIndicator dynamicDataSourceHealthIndicator(
      DynamicRoutingDataSource routingDataSource) {
    return new DynamicDataSourceHealthIndicator(routingDataSource);
  }

  // 注意：租户数据源路由（TenantDataSourceRouter）已迁移到 common-tenant 模块
  // 由 TenantAutoConfiguration 在 ydsz.tenant.mode=ISOLATE_DB 时自动注册
}
