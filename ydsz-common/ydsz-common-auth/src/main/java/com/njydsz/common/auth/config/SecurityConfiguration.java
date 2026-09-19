package com.njydsz.common.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.auth.security.CsrfTokenValidator;

/**
 * 安全配置。
 *
 * <p>负责装配安全纵深相关的 Bean（CSRF 防护等）。
 *
 * <p>CSRF 验证器默认启用，仅当应用显式配置 {@code ydsz.auth.csrf-enabled=false} 时关闭。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Configuration
public class SecurityConfiguration {

  /**
   * 创建 CSRF 验证器 Bean。
   *
   * @return CsrfTokenValidator 实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.auth",
      name = "csrf-enabled",
      havingValue = "true",
      matchIfMissing = false)
  public CsrfTokenValidator csrfTokenValidator() {
    return new CsrfTokenValidator(true);
  }
}
