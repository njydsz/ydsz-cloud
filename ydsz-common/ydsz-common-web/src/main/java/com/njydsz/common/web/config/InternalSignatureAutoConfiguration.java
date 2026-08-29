package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.web.constant.WebFilterOrder;
import com.njydsz.common.web.filter.InternalSignatureFilter;

/**
 * 内部签名验签自动配置（P0-3 补建）。
 *
 * <p>为下游 Servlet 服务注册 {@link InternalSignatureFilter}，校验网关注入的
 * {@code X-Internal-Sig} HMAC 签名，实现"网关只签、下游必验"的内部调用防伪闭环。
 *
 * <h3>启用方式</h3>
 *
 * <p>默认关闭（{@code enabled=false}），由配置中心按服务粒度灰度开启：
 *
 * <pre>
 * ydsz:
 *   security:
 *     internal-sign:
 *       enabled: true
 *       secret: ${ENC(...)}          # 与网关 ydsz.gateway.internal-sign-secret 一致
 *       enforce-paths:               # 默认 /api/internal/** 与 /feign/**
 *         - /api/internal/**
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass(jakarta.servlet.Filter.class)
@ConditionalOnProperty(
    prefix = "ydsz.security.internal-sign",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(InternalSignatureProperties.class)
public class InternalSignatureAutoConfiguration {

  /**
   * 注册内部签名验签过滤器。
   *
   * <p>顺序 {@link WebFilterOrder#INTERNAL_SIGNATURE_FILTER}（先于鉴权过滤器，
   * 在伪造内部请求触达鉴权链路前直接拒绝）。
   *
   * @param properties 内部签名配置
   * @return FilterRegistrationBean
   */
  @Bean
  @ConditionalOnMissingBean(name = "internalSignatureFilter")
  public FilterRegistrationBean<InternalSignatureFilter> internalSignatureFilter(
      InternalSignatureProperties properties) {
    FilterRegistrationBean<InternalSignatureFilter> registration =
        new FilterRegistrationBean<>(new InternalSignatureFilter(properties));
    registration.setOrder(WebFilterOrder.INTERNAL_SIGNATURE_FILTER);
    registration.setName("internalSignatureFilter");
    return registration;
  }
}
