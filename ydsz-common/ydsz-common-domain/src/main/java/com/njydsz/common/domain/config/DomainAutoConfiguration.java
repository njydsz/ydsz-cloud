package com.njydsz.common.domain.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Domain 模块自动配置。
 *
 * <p>激活 {@link DomainProperties} 配置属性绑定。 通过 {@code ydsz.domain.enabled=false} 关闭自动装配。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "ydsz.domain",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(DomainProperties.class)
public class DomainAutoConfiguration {
  // 配置绑定由 @EnableConfigurationProperties 激活
}
