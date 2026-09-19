package com.njydsz.common.locales.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * i18n Web 元数据 API 自动配置（按需条件加载）。
 *
 * <p>当满足以下全部条件时启用：
 *
 * <ul>
 *   <li>类路径存在 spring-webmvc（{@code @RestController} 可用）
 *   <li>当前为 Web 应用上下文（{@link ConditionalOnWebApplication}）
 *   <li>配置 {@code ydsz.i18n.metadata-api-enabled=true}（默认 false）
 * </ul>
 *
 * <p>启用后扫描 {@code com.njydsz.common.locales.web} 包下的 {@link
 * com.njydsz.common.locales.web.I18nMetadataController}，暴露 i18n 元数据 REST API。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
@ConditionalOnWebApplication
@ConditionalOnProperty(
    prefix = "ydsz.i18n",
    name = "metadata-api-enabled",
    havingValue = "true",
    matchIfMissing = false)
@ComponentScan(basePackages = "com.njydsz.common.locales.web")
public class LocalesWebMetadataAutoConfiguration {
  // 空类：通过 @ComponentScan 加载 web 包下的 Controller
}
