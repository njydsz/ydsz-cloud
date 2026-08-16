package com.njydsz.common.core.config;

import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.response.BaseResponse;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

/**
 * Core 模块自动配置类。
 *
 * <p>激活 {@link CoreProperties} 配置属性绑定， 使 {@code ydsz.core.*} 配置项在 IDE 中获得自动补全和类型校验支持。
 *
 * <p>当 Spring {@link MessageSource} 可用时，自动注册 {@link SpringMessageResolver} 并绑定到 {@link
 * BaseResponse}，使响应消息支持国际化。 若容器中无 MessageSource Bean（纯 core 使用场景），自动回退到 JDK {@link ResourceBundle}
 * 加载 {@code i18n/core/messages*} 资源束，保障最低限度的国际化能力。
 *
 * <p><b>启用条件：</b>当 {@code ydsz.core.enabled=true} 时生效（默认启用）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "ydsz.core",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(CoreProperties.class)
public class CoreAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(CoreAutoConfiguration.class);

  /**
   * 注册 SpringMessageResolver 并注入到 BaseResponse。
   *
   * <p>通过静态持有方式使统一的国际化解析能力在任意位置可用 （包括非 Spring Bean 中的静态工厂方法）。 仅当容器中存在 MessageSource Bean 时生效（如
   * starter 模块配置了 MessageSource）。
   *
   * @param messageSource Spring 消息源
   * @return SpringMessageResolver 实例
   */
  @Bean
  @ConditionalOnBean(MessageSource.class)
  public SpringMessageResolver springMessageResolver(MessageSource messageSource) {
    SpringMessageResolver resolver = new SpringMessageResolver(messageSource);
    BaseResponse.setResolverIfAbsent(resolver);
    return resolver;
  }

  /**
   * 注册 JDK ResourceBundle 回退解析器到 BaseResponse。
   *
   * <p>当 Spring MessageSource 不可用时（纯 core 使用场景、CLI 环境等）， 通过 JDK 原生 {@link ResourceBundle} 加载 {@code
   * i18n/core/messages*} 资源束， 提供最低限度的国际化能力。此 Bean 仅在 SpringMessageResolver 未注册时生效。
   *
   * @return ResourceBundleMessageResolver 实例
   * @since 1.11.0
   */
  @Bean
  @ConditionalOnMissingBean(BaseResponse.MessageResolver.class)
  public BaseResponse.MessageResolver resourceBundleMessageResolver() {
    ResourceBundleMessageResolver resolver = new ResourceBundleMessageResolver();
    BaseResponse.setResolverIfAbsent(resolver);
    if (log.isDebugEnabled()) {
      log.debug("JDK ResourceBundle message resolver registered as fallback for i18n.");
    }
    return resolver;
  }

  /** 将 CoreProperties 中的分页配置传播到 PageConstants 运行时覆盖值。 */
  @Bean
  PageConstantsInitializer pageConstantsInitializer(CoreProperties properties) {
    return new PageConstantsInitializer(properties);
  }

  static class PageConstantsInitializer implements SmartInitializingSingleton {

    private final CoreProperties properties;

    PageConstantsInitializer(CoreProperties properties) {
      this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
      PageConstants.init(properties);
    }
  }

  /**
   * 基于 JDK ResourceBundle 的国际化解析器（Fallback）。
   *
   * <p>加载 classpath 下的 {@code i18n/core/messages} 资源束， 按当前线程的 {@link Locale} 选择对应语言版本。
   * 资源不存在时回退到默认值。
   */
  static class ResourceBundleMessageResolver implements BaseResponse.MessageResolver {

    /** i18n 资源束的 base name（相对于 classpath 根）。 */
    private static final String BASENAME = "i18n/core/messages";

    @Override
    public String resolve(String key, String defaultValue) {
      if (key == null || key.isEmpty()) {
        return defaultValue;
      }
      try {
        Locale locale = Locale.getDefault();
        ResourceBundle bundle = ResourceBundle.getBundle(BASENAME, locale);
        String value = bundle.getString(key);
        return value != null ? value : defaultValue;
      } catch (MissingResourceException e) {
        return defaultValue;
      }
    }
  }
}
