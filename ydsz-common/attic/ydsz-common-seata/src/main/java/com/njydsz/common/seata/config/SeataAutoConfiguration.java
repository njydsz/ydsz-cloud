package com.njydsz.common.seata.config;

import com.njydsz.common.seata.aspect.SeataReflector;
import com.njydsz.common.seata.aspect.YdszGlobalTransactionalAspect;
import com.njydsz.common.seata.filter.XidServletFilter;
import com.njydsz.common.seata.health.SeataHealthIndicator;
import com.njydsz.common.seata.interceptor.FeignXidRequestInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seata 能力自动配置。
 *
 * @deprecated 自 26.09.19 起归档至 {@code attic/} 目录。
 *     <p><b>退役原因：</b>经全项目代码扫描确认零外部引用，Seata 客户端（seata-spring-boot-starter 等）
 *     由业务模块按需自行引入，不再需要 common 层统一封装。
 *     <p><b>替代方案：</b>业务模块直接引入 Seata 官方 starter 并配置 {@code seata.*} 属性即可；
 *     如需 XID 透传，可自行实现 ServletFilter / Feign RequestInterceptor（参考本模块源码）。
 *     <p><b>移除时间：</b>下一大版本（27.01.01）起彻底删除本模块及 {@code attic/ydsz-common-seata} 目录。
 *     <p>在 27.01.01 之前本模块仍可被编译和引用，但 IDE 会显示删除线警告，引导消费方迁移。
 *
 * <p>本配置类在满足以下任一条件时激活相应 bean：
 * <ul>
 *   <li>{@code ydsz.seata.enabled=true}（总开关，见 {@link SeataProperties}）；</li>
 *   <li>classpath 中存在 Seata 客户端（{@code SeataReflector#seataPresent()}）。</li>
 * </ul>
 * 任一条件不满足则所有 bean 均不会被注册（平台未引入 Seata 客户端时业务代码本模块无感知）。
 *
 * <p>注册清单：
 * <table border="1">
 *   <tr><th>Bean</th><th>职责</th><th>启用条件</th></tr>
 *   <tr><td>YdszGlobalTransactionalAspect</td><td>全局事务切面</td>
 *       <td>enabled=true（Seata 客户端可选；缺失时 warn 放行）</td></tr>
 *   <tr><td>FeignXidRequestInterceptor</td><td>Feign 调用端 XID 头注入</td>
 *       <td>enabled=true + feign 客户端路径条件（IOperator 条件）</td></tr>
 *   <tr><td>XidServletFilter</td><td>Web 入口 XID 绑定/解绑</td>
 *       <td>enabled=true + web 环境条件（WebPathExtensionStrategy）</td></tr>
 *   <tr><td>SeataHealthIndicator</td><td>Actuator /health 状态</td>
 *       <td>enabled=true + actuator 环境条件（MetricsAutoConfig）</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since ACC-1
 */
@Slf4j
@Deprecated(since = "26.09.19", forRemoval = true)
@AutoConfiguration
@EnableConfigurationProperties(SeataProperties.class)
@ConditionalOnProperty(prefix = SeataProperties.PREFIX, name = "enabled", havingValue = "true")
public class SeataAutoConfiguration {

  @PostConstruct
  void logStartup() {
    if (!SeataReflector.seataPresent()) {
      log.warn(
          "ydsz.seata.enabled=true 但 classpath 中未检测到 Seata 客户端；"
              + "已注册全局事务切面但未引入 Seata jar 时不会真正发起全局事务。");
    } else {
      log.info("ydsz-common-seata 已启用（Seata 客户端 present）");
    }
  }

  // ---- 全局事务切面 ----

  /**
   * 全局事务切面（默认注册）。
   */
  @Bean
  @ConditionalOnMissingBean
  public YdszGlobalTransactionalAspect ydszGlobalTransactionalAspect(
      final SeataProperties properties) {
    return new YdszGlobalTransactionalAspect(properties);
  }

  // ---- XID Feign 透传拦截器 ----

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "feign.RequestInterceptor")
  static class FeignXidConfig {

    @Bean
    @ConditionalOnMissingBean
    public FeignXidRequestInterceptor feignXidRequestInterceptor(
        final SeataProperties properties) {
      return new FeignXidRequestInterceptor(properties);
    }
  }

  // ---- XID Web 入口过滤器 ----

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  static class ServletFilterConfig {

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<XidServletFilter> xidServletFilter(
        final SeataProperties properties) {
      final FilterRegistrationBean<XidServletFilter> registration =
          new FilterRegistrationBean<>();
      registration.setFilter(new XidServletFilter(properties));
      registration.addUrlPatterns("/*");
      registration.setName("xidServletFilter");
      registration.setOrder(1000);
      return registration;
    }
  }

  // ---- Seata 组件健康指示器 ----

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  static class SeataHealthConfig {

    @Bean
    @ConditionalOnMissingBean(name = "seataHealthIndicator")
    public org.springframework.boot.health.contributor.HealthContributor seataHealthIndicator(
        final SeataProperties properties) {
      return new SeataHealthIndicator(properties);
    }
  }
}
