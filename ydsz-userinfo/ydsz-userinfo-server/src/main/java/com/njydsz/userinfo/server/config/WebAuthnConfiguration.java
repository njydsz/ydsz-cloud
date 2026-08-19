package com.njydsz.userinfo.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * WebAuthn/Passkey 自动配置类
 *
 * <p>注册 WebAuthn 相关的 Bean，当 ydsz.userinfo.webauthn.enabled=true 时激活。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(WebAuthnProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.userinfo.webauthn",
    name = "enabled",
    havingValue = "true")
public class WebAuthnConfiguration {
  // WebAuthnProperties 已通过 @EnableConfigurationProperties 注册为 Bean
  // WebAuthnService 通过 @Service 自动注册
}
