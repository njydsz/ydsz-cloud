package com.njydsz.common.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.auth.security.CsrfTokenValidator;
import com.njydsz.common.auth.apikey.ApiKeyAuthService;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 安全配置。
 *
 * <p>负责装配安全纵深相关的 Bean：
 *
 * <ul>
 *   <li>{@link CsrfTokenValidator}（CSRF 防护）
 * </ul>
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

  /**
   * 创建 API Key 认证服务 Bean。
   *
   * <p>仅当显式启用 {@code ydsz.auth.api-key-enabled=true} 且 RedisStringOps 可用时装配。 为 IoT、第三方集成、批量脚本等 machine-to-machine
   * 场景提供 {@code X-Api-Key} 认证通道。
   *
   * @param redisStringOps Redis String 操作
   * @param authProperties 认证配置属性
   * @return ApiKeyAuthService 实例
   */
  @Bean
  @ConditionalOnMissingBean(ApiKeyAuthService.class)
  @ConditionalOnBean(RedisStringOps.class)
  @ConditionalOnProperty(
      prefix = "ydsz.auth.api-key",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = false)
  public ApiKeyAuthService apiKeyAuthService(
      RedisStringOps redisStringOps, AuthProperties authProperties) {
    return new ApiKeyAuthService(redisStringOps, authProperties);
  }
}
