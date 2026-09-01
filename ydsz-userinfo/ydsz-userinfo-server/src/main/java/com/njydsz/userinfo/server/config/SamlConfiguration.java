package com.njydsz.userinfo.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SAML 2.0 自动配置类
 *
 * <p>注册 SAML 相关的 Bean，当 ydsz.userinfo.saml.enabled=true 时激活。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
@EnableConfigurationProperties(SamlProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.userinfo.saml",
    name = "enabled",
    havingValue = "true")
public class SamlConfiguration {
  // SamlProperties 已通过 @EnableConfigurationProperties 注册为 Bean
  // SamlService 通过 @Service 自动注册
}
