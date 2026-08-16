package com.njydsz.common.safe.encrypt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import com.njydsz.common.safe.config.ApiSignatureProperties;
import com.njydsz.common.safe.crypto.NonceCache;
import com.njydsz.common.safe.filter.ApiSignatureFilter;

/**
 * API 签名自动配置
 *
 * <p>基于 {@code timestamp + nonce + signature} 三要素实现 API 请求防篡改和防重放。 使用 HMAC-SHA256
 * 算法计算签名，确保请求在传输过程中未被篡改。
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   safe:
 *     api-signature:
 *       enabled: true
 *       app-id: my-app
 *       app-secret: base64-encoded-secret
 *       timestamp-tolerance-seconds: 300
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.safe.api-signature", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ApiSignatureProperties.class)
public class ApiSignatureAutoConfiguration {

  /**
   * 注册防重放 Nonce 缓存
   *
   * <p>用于 API 签名验证的 nonce 防重放存储，基于 ydsz-common-cache 实现 TTL 自动过期。
   *
   * @return Nonce 缓存实例
   */
  @Bean
  @ConditionalOnMissingBean(NonceCache.class)
  public NonceCache nonceCache() {
    log.info("注册防重放 Nonce 缓存");
    return new NonceCache();
  }

  /**
   * 注册 API 签名验证过滤器
   *
   * @param properties 签名配置属性
   * @param nonceCache 防重放 Nonce 缓存
   * @return API 签名验证过滤器注册 bean
   */
  @Bean
  @ConditionalOnMissingBean(name = "apiSignatureFilterRegistration")
  public FilterRegistrationBean<ApiSignatureFilter> apiSignatureFilterRegistration(
      ApiSignatureProperties properties, NonceCache nonceCache) {
    log.info("注册 API 签名验证过滤器");
    FilterRegistrationBean<ApiSignatureFilter> registrationBean =
        new FilterRegistrationBean<>(new ApiSignatureFilter(properties, nonceCache, null));
    registrationBean.setName("apiSignatureFilter");
    registrationBean.addUrlPatterns("/*");
    registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
    return registrationBean;
  }
}
