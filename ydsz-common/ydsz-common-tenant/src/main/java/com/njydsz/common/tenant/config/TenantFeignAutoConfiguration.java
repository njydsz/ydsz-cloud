package com.njydsz.common.tenant.config;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.tenant.feign.TenantContextFeignInterceptor;
import com.njydsz.common.tenant.feign.TenantContextPropagationStrategy;

/**
 * 多租户 Feign 跨服务透传自动装配（仅在 classpath 存在 Feign 时加载）。
 *
 * <p>从 {@link TenantAutoConfiguration} 中拆分出来，避免 Spring Boot 4 在类方法内省时， 因方法签名引用了 {@code
 * feign.RequestInterceptor} 导致非 Feign 模块（如 generator）启动失败。
 *
 * <p>条件：Feign 在 classpath 且租户功能已启用（{@code ydsz.tenant.enabled=true}）。
 *
 * @author ydsz-team
 * @since 26.10.09
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "feign.RequestInterceptor")
@EnableConfigurationProperties(TenantProperties.class)
public class TenantFeignAutoConfiguration {

  /**
   * Feign 跨服务透传拦截器。
   *
   * <p>注入 {@link TenantProperties#getActiveTenantFields()}，使拦截器通过 {@link
   * com.njydsz.common.tenant.feign.TenantHeaderContract} 计算与 WebFilter 端一致的 header 名称。
   *
   * @param properties 租户配置
   * @return Feign 拦截器
   */
  @Bean
  @ConditionalOnMissingBean
  public TenantContextFeignInterceptor tenantContextFeignInterceptor(TenantProperties properties) {
    log.info("[tenant-feign] 多租户 Feign 跨服务透传已启用");
    return new TenantContextFeignInterceptor(properties.getActiveTenantFields());
  }

  /**
   * 注册一个默认的 Feign 传播策略 Bean（实现 {@link TenantContextPropagationStrategy}）。
   *
   * <p>基于 Feign Header 的传播策略实现，作为内置默认实现；业务模块可通过 {@code @Primary} 覆盖以支持自定义协议。
   *
   * @param feignInterceptor Feign 拦截器
   * @return Feign 传播策略 Bean
   */
  @Bean
  @ConditionalOnBean(TenantContextFeignInterceptor.class)
  @ConditionalOnMissingBean
  public TenantContextPropagationStrategy feignTenantContextPropagationStrategy(
      TenantContextFeignInterceptor feignInterceptor) {
    return new TenantContextPropagationStrategy() {
      @Override
      public void propagate(Map<String, String> transportCarrier) {
        var ctx = com.njydsz.common.core.context.TenantContextHolder.get();
        if (ctx == null) {
          return;
        }
        var fields = ctx.getFields();
        if (fields == null) {
          return;
        }
        for (var entry : fields.entrySet()) {
          if (entry.getValue() instanceof String value) {
            transportCarrier.put("x-" + entry.getKey().toLowerCase(), value);
          }
        }
      }

      @Override
      public int order() {
        return 100;
      }

      @Override
      public boolean supports(String transportType) {
        return "feign".equalsIgnoreCase(transportType) || "http".equalsIgnoreCase(transportType);
      }
    };
  }
}
