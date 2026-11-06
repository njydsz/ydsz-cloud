package com.njydsz.common.safe.internal;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.safe.config.ApiSignatureProperties;

/**
 * 内部接口调用校验自动配置（P0-2 下沉）。
 *
 * <p>注册 {@link InternalCallProperties} 与 {@link RequireInternalAspect}，
 * 使各业务模块只需在内部 Controller 上标注 {@code @RequireInternal}
 * 并配置 {@code ydsz.internal-call.enabled=true} 即可获得服务端二次校验。
 *
 * <p>独立自动配置类（而非并入 SafeConfiguration）的原因：SafeConfiguration 体量庞大
 * （34KB+、90+ Bean），新增 Bean 走独立类可降低对存量配置的回归风险，
 * 也便于后续按需拆分。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@AutoConfiguration
@EnableConfigurationProperties({InternalCallProperties.class, ApiSignatureProperties.class})
public class InternalCallAutoConfiguration {

  /**
   * 注册内部调用校验切面。
   *
   * @param properties 内部调用配置
   * @param signatureProperties 签名配置（用于签名优先判定）
   * @return 切面实例
   */
  @Bean
  @ConditionalOnMissingBean(RequireInternalAspect.class)
  public RequireInternalAspect requireInternalAspect(
      InternalCallProperties properties, ApiSignatureProperties signatureProperties) {
    return new RequireInternalAspect(properties, signatureProperties);
  }
}
