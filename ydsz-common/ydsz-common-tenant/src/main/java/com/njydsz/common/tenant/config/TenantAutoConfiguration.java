package com.njydsz.common.tenant.config;

import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;
import com.njydsz.common.redis.service.RedisRateLimiter;
import com.njydsz.common.redis.tenant.TenantRedisKeyPrefixer;
import com.njydsz.common.tenant.SystemTenantContextRunner;
import com.njydsz.common.tenant.annotation.TenantColumnScanner;
import com.njydsz.common.tenant.async.TenantContextTaskDecorator;
import com.njydsz.common.tenant.datasource.DatasourceKeyResolver;
import com.njydsz.common.tenant.datasource.TenantDataSourceFilter;
import com.njydsz.common.tenant.datasource.TenantDataSourceRouter;
import com.njydsz.common.tenant.feign.TenantContextFeignInterceptor;
import com.njydsz.common.tenant.health.TenantHealthIndicator;
import com.njydsz.common.tenant.interceptor.TenantInterceptorProvider;
import com.njydsz.common.tenant.metrics.TenantMetrics;
import com.njydsz.common.tenant.ratelimit.TenantRateLimiter;
import com.njydsz.common.tenant.validation.TenantIndexValidator;
import com.njydsz.common.tenant.web.TenantContextWebFilter;

/**
 * 多租户自动装配。
 *
 * <p>条件：{@code ydsz.tenant.enabled=true}（默认 false，不启用）。
 *
 * <p>装配内容：
 *
 * <ul>
 *   <li>{@link TenantInterceptorProvider} — SPI 注册 SQL 拦截器到 MybatisPlusInterceptor 链
 *   <li>{@link TenantContextWebFilter} — Web 入口上下文设置 + MDC 日志注入
 *   <li>{@link TenantContextFeignInterceptor} — Feign 跨服务透传
 *   <li>{@link TenantContextTaskDecorator} — 异步传播
 *   <li>{@link TenantDataSourceRouter} — ISOLATE_DB 数据源路由
 *   <li>{@link TenantDataSourceFilter} — ISOLATE_DB Web 过滤器
 * </ul>
 *
 * <p><b>注意：</b>租户生命周期管理已迁移至独立模块 {@code ydsz-tenant-admin}， 不应在此声明。运行时隔离与运营域职责分离。
 *
 * <p>不引入 {@code common-tenant} 依赖或设为 false 时， 无任何租户逻辑，{@code MpBaseEntity.tenantId} 字段被忽略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.tenant", name = "enabled", matchIfMissing = false)
@EnableConfigurationProperties(TenantProperties.class)
public class TenantAutoConfiguration {

  /**
   * SPI 拦截器提供者：注册 TenantIsolationInterceptor 到 MybatisPlusInterceptor 链。
   *
   * @param properties 租户配置
   * @return 拦截器提供者
   */
  @Bean
  @ConditionalOnMissingBean
  public TenantInterceptorProvider tenantInterceptorProvider(
      TenantProperties properties, ObjectProvider<TenantMetrics> metricsProvider) {
    log.info(
        "多租户隔离已启用: mode={}, tenantColumn={}, superTenantId={}, systemTenantId={}",
        properties.getMode(),
        properties.getTenantColumn(),
        properties.getSuperTenantId(),
        properties.getSystemTenantId());
    SystemTenantContextRunner.init(properties.getSystemTenantId());
    return new TenantInterceptorProvider(properties, metricsProvider.getIfAvailable());
  }

  /**
   * {@code @TenantColumn} 注解扫描器。
   *
   * <p>扫描 classpath 中实体类上的 {@code @TenantColumn} + {@code @TableName} 注解， 解析 per-table 租户列名映射。
   *
   * <p>仅当 MyBatis-Plus 在 classpath 时注册。
   *
   * @return 注解扫描器
   */
  @Bean
  @ConditionalOnClass(name = "com.baomidou.mybatisplus.annotation.TableName")
  @ConditionalOnMissingBean
  public TenantColumnScanner tenantColumnScanner() {
    return new TenantColumnScanner();
  }

  /**
   * 注解扫描结果回填处理器。
   *
   * <p>在 {@link TenantProperties} 初始化后，将注解扫描到的 per-table 列名映射 回填到其 tableColumnMapping Map 中（YAML
   * 显式配置优先）。
   *
   * @param scanner 注解扫描器
   * @return BeanPostProcessor
   */
  @Bean
  @ConditionalOnClass(name = "com.baomidou.mybatisplus.annotation.TableName")
  @ConditionalOnMissingBean
  public TenantPropertiesAnnotationPopulator tenantPropertiesAnnotationPopulator(
      TenantColumnScanner scanner) {
    return new TenantPropertiesAnnotationPopulator(scanner);
  }

  // -----------------------------------------------------------------------
  // 租户数据源 Key 解析器（简化：单一实现支持配置映射 + 命名约定回退）
  // -----------------------------------------------------------------------

  /**
   * 数据源 Key 解析器。
   *
   * <p>使用内置默认实现，同时支持：
   *
   * <ul>
   *   <li>配置映射：从 {@code ydsz.tenant.datasource.mapping} 读取
   *   <li>命名约定回退：未配置时 {@code "tenant_" + tenantId}
   * </ul>
   *
   * <p>业务模块可通过注册 {@code @Primary} Bean 覆盖此默认行为。
   *
   * @param properties 租户配置
   * @return 数据源 Key 解析器
   */
  @Bean
  @ConditionalOnMissingBean
  public DatasourceKeyResolver datasourceKeyResolver(TenantProperties properties) {
    return DatasourceKeyResolver.createDefault(properties);
  }

  // -----------------------------------------------------------------------
  // 租户诊断 / 校验
  // -----------------------------------------------------------------------

  /**
   * 租户表索引校验器（异步，DataSource 存在时）。
   *
   * @param dataSource 数据源
   * @param properties 租户配置
   * @return 索引校验器
   */
  @Bean
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnProperty(
      prefix = "ydsz.tenant.validation.index-check",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean
  public TenantIndexValidator tenantIndexValidator(
      DataSource dataSource,
      TenantProperties properties,
      ObjectProvider<ThreadPoolTaskExecutor> taskExecutorProvider) {
    return new TenantIndexValidator(dataSource, properties, taskExecutorProvider);
  }

  /**
   * Web 入口过滤器：从 JWT/Header 解析租户上下文 + MDC 日志注入。
   *
   * <p>使用 {@link FilterRegistrationBean} 包装，显式指定 order 为 {@code Ordered.HIGHEST_PRECEDENCE +
   * 90}，确保在 ISOLATE_DB 数据源 路由过滤器（+100）之前执行。
   *
   * <p><b>Filter 链执行顺序：</b>
   *
   * <ol>
   *   <li>TenantContextWebFilter（+90）：解析租户上下文写入 TenantContextHolder
   *   <li>TenantDataSourceFilter（+100）：读取上下文切换数据源
   * </ol>
   *
   * @param properties 租户配置
   * @return Filter 注册 Bean
   */
  @Bean
  @ConditionalOnClass(name = "jakarta.servlet.Filter")
  @ConditionalOnWebApplication
  @ConditionalOnMissingBean
  public FilterRegistrationBean<TenantContextWebFilter> tenantContextWebFilterRegistration(
      TenantProperties properties, ObjectProvider<TenantMetrics> metricsProvider) {
    FilterRegistrationBean<TenantContextWebFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(
        new TenantContextWebFilter(properties, metricsProvider.getIfAvailable()));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 90);
    registration.addUrlPatterns("/*");
    registration.setName("tenantContextWebFilter");
    return registration;
  }

  /**
   * Feign 跨服务透传拦截器（可选，common-feign 在 classpath 时）。
   *
   * <p>注入 {@link TenantProperties#getActiveTenantFields()}，使拦截器 通过 {@link
   * com.njydsz.common.tenant.feign.TenantHeaderContract} 计算与 WebFilter 端一致的 header 名称。
   *
   * @param properties 租户配置
   * @return Feign 拦截器
   */
  @Bean
  @ConditionalOnClass(name = "feign.RequestInterceptor")
  @ConditionalOnMissingBean
  public TenantContextFeignInterceptor tenantContextFeignInterceptor(TenantProperties properties) {
    log.info("多租户 Feign 跨服务透传已启用");
    return new TenantContextFeignInterceptor(properties.getActiveTenantFields());
  }

  /**
   * 异步传播任务装饰器（可选，common-thread 在 classpath 时）。
   *
   * @param properties 租户配置
   * @return 任务装饰器
   */
  @Bean
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.core.task.TaskDecorator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  @ConditionalOnMissingBean
  public TenantContextTaskDecorator tenantContextTaskDecorator(TenantProperties properties) {
    log.info("多租户异步上下文传播已启用");
    return new TenantContextTaskDecorator(properties);
  }

  /**
   * 租户级 Redis Key 前缀器（可选，common-redis 在 classpath 时）。
   *
   * <p>为所有 Redis key 自动添加租户前缀，实现租户级数据隔离。
   *
   * @return Redis Key 前缀器
   */
  @Bean
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.data.redis.serializer.RedisSerializer")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  @ConditionalOnMissingBean
  public TenantRedisKeyPrefixer tenantRedisKeyPrefixer() {
    log.info("多租户 Redis Key 隔离已启用");
    return new TenantRedisKeyPrefixer(RequestContext::getTenantId, true);
  }

  /**
   * 租户级限流门面（可选，common-redis 在 classpath 时）。
   *
   * <p>按租户维度限流，自动在限流 Key 前添加租户前缀。
   *
   * @param rateLimiterProvider Redis 限流器提供者（可选）
   * @return 租户限流门面
   */
  @Bean
  @ConditionalOnClass(name = "com.njydsz.common.redis.service.RedisRateLimiter")
  @ConditionalOnMissingBean
  public TenantRateLimiter tenantRateLimiter(ObjectProvider<RedisRateLimiter> rateLimiterProvider) {
    log.info("多租户限流已启用");
    return new TenantRateLimiter(rateLimiterProvider.getIfAvailable());
  }

  /**
   * 租户级配置隔离提供者。
   *
   * <p>允许不同租户有差异化的配置覆盖。
   *
   * @return 配置提供者
   */
  @Bean
  @ConditionalOnMissingBean
  public TenantConfigProvider tenantConfigProvider() {
    log.info("多租户配置隔离已启用");
    return new TenantConfigProvider();
  }

  /**
   * 多租户 Micrometer 指标（可选，Micrometer 在 classpath 时）。
   *
   * @param meterRegistryProvider Micrometer 注册中心提供者（可选）
   * @return 指标实例
   */
  @Bean
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  @ConditionalOnMissingBean
  public TenantMetrics tenantMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    return new TenantMetrics(meterRegistryProvider.getIfAvailable());
  }

  /**
   * 多租户健康检查指标。
   *
   * @param properties 租户配置
   * @param metricsProvider 指标（可选）
   * @param dataSourceRouterProvider 数据源路由器（可选）
   * @return 健康检查
   */
  @Bean
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  @ConditionalOnMissingBean
  public TenantHealthIndicator tenantHealthIndicator(
      TenantProperties properties,
      ObjectProvider<TenantMetrics> metricsProvider,
      ObjectProvider<TenantDataSourceRouter> dataSourceRouterProvider) {
    return new TenantHealthIndicator(properties, metricsProvider, dataSourceRouterProvider);
  }

  /**
   * 自动将 TenantContextTaskDecorator 注入到所有 ThreadPoolTaskExecutor。
   *
   * <p>通过 BeanPostProcessor 在 Bean 初始化后自动设置 TaskDecorator， 无需业务模块手动配置。
   *
   * @param taskDecoratorProvider TaskDecorator 提供者
   * @return BeanPostProcessor
   */
  @Bean
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  @ConditionalOnMissingBean(name = "tenantTaskDecoratorPostProcessor")
  public BeanPostProcessor tenantTaskDecoratorPostProcessor(
      ObjectProvider<TenantContextTaskDecorator> taskDecoratorProvider) {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ThreadPoolTaskExecutor executor) {
          TenantContextTaskDecorator decorator = taskDecoratorProvider.getIfAvailable();
          if (decorator != null) {
            executor.setTaskDecorator(decorator);
            log.info("多租户 TaskDecorator 自动注入到线程池: {}", beanName);
          }
        }
        return bean;
      }
    };
  }

  /**
   * ISOLATE_DB 模式数据源路由器（可选，mode=ISOLATE_DB 时）。
   *
   * @param routingDataSource 动态数据源
   * @param properties 租户配置
   * @param keyResolverProvider 数据源 Key 解析器 SPI 提供者（可选）
   * @param metricsProvider 指标提供者（可选）
   * @return 数据源路由器
   */
  @Bean
  @ConditionalOnProperty(prefix = "ydsz.tenant", name = "mode", havingValue = "ISOLATE_DB")
  @ConditionalOnClass(name = "com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource")
  @ConditionalOnMissingBean
  public TenantDataSourceRouter tenantDataSourceRouter(
      DynamicRoutingDataSource routingDataSource,
      TenantProperties properties,
      ObjectProvider<DatasourceKeyResolver> keyResolverProvider,
      ObjectProvider<TenantMetrics> metricsProvider) {
    DatasourceKeyResolver resolver = keyResolverProvider.getIfAvailable();
    log.info(
        "多租户 ISOLATE_DB 模式已启用，数据源路由器已注册，解析器={}",
        resolver != null ? resolver.getClass().getSimpleName() : "default");
    return new TenantDataSourceRouter(
        routingDataSource, properties, resolver, metricsProvider.getIfAvailable());
  }

  /**
   * ISOLATE_DB 模式 Web 过滤器（可选，mode=ISOLATE_DB + web 应用时）。
   *
   * <p>order = {@code HIGHEST_PRECEDENCE + 100}，在 TenantContextWebFilter（+90）
   * 之后执行，确保租户上下文已解析后再进行数据源路由。
   *
   * @param router 数据源路由器
   * @param properties 租户配置
   * @return Filter 注册 Bean
   */
  @Bean
  @ConditionalOnProperty(prefix = "ydsz.tenant", name = "mode", havingValue = "ISOLATE_DB")
  @ConditionalOnClass(name = "jakarta.servlet.Filter")
  @ConditionalOnWebApplication
  @ConditionalOnMissingBean
  public FilterRegistrationBean<TenantDataSourceFilter> tenantDataSourceFilterRegistration(
      TenantDataSourceRouter router, TenantProperties properties) {
    FilterRegistrationBean<TenantDataSourceFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new TenantDataSourceFilter(router));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
    registration.addUrlPatterns("/*");
    registration.setName("tenantDataSourceFilter");
    return registration;
  }
}
