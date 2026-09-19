package com.njydsz.common.base.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.base.actuator.I18nMetadataActuatorEndpoint;
import com.njydsz.common.locales.config.I18nProperties;

/**
 * i18n 元数据 Actuator 端点自动配置（base 模块集中管理 Web/Actuator 端点注册）。
 *
 * <p>当满足以下全部条件时启用：
 *
 * <ul>
 *   <li>Actuator 端点机制可用（类路径存在 {@code @Endpoint}）
 *   <li>Web 应用上下文（{@link ConditionalOnWebApplication}）
 *   <li>{@link I18nProperties} Bean 可用（locales 模块已引入）
 *   <li>配置 {@code ydsz.i18n.metadata-api-enabled=true}（默认 false）
 * </ul>
 *
 * <p>启用后注册 {@link I18nMetadataActuatorEndpoint}，暴露 i18n 配置与语言信息查询能力。
 *
 * <p><b>架构说明：</b>端点注册放在 base 模块而非 locales 模块，避免 L2 基础设施引入 Web/Actuator 依赖方向问题。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@ConditionalOnWebApplication
@ConditionalOnBean(I18nProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.i18n",
    name = "metadata-api-enabled",
    havingValue = "true",
    matchIfMissing = false)
public class I18nMetadataAutoConfiguration {

  /**
   * 注册 i18n 元数据 Actuator 端点 Bean。
   *
   * @param i18nProperties i18n 配置属性（由 locales 模块的 LocalesAutoConfiguration 提供）
   * @return i18n 元数据端点
   */
  @Bean
  public I18nMetadataActuatorEndpoint i18nMetadataActuatorEndpoint(I18nProperties i18nProperties) {
    return new I18nMetadataActuatorEndpoint(i18nProperties);
  }
}
