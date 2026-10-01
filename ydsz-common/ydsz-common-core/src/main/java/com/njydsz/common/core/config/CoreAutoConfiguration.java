package com.njydsz.common.core.config;

import java.util.Locale;

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
import org.springframework.context.i18n.LocaleContextHolder;

import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.feature.ConfigDrivenFeatureFlagService;
import com.njydsz.common.core.feature.FeatureFlagContext;
import com.njydsz.common.core.feature.FeatureFlagService;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.locales.util.MessageSourceHolder;

/**
 * Core 模块自动配置类。
 *
 * <p>激活 {@link CoreProperties} 配置属性绑定， 使 {@code ydsz.core.*} 配置项在 IDE 中获得自动补全和类型校验支持。
 *
 * <p>当 Spring {@link MessageSource} 可用时，自动注册基于 {@link MessageSource} 的国际化解析器并绑定到 {@link
 * YdszResponse}，使响应消息支持国际化。 若容器中无 MessageSource Bean（纯 core 使用场景），自动回退到 JDK {@link ResourceBundle}
 * 加载 {@code i18n/core/messages*} 资源束，保障最低限度的国际化能力。
 *
 * <p><b>启用条件：</b>当 {@code ydsz.core.enabled=true} 时生效（默认启用）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "ydsz.core",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(CoreProperties.class)
public class CoreAutoConfiguration {

  /** 日志实例，用于记录配置装配信息。 */
  private static final Logger LOG = LoggerFactory.getLogger(CoreAutoConfiguration.class);

  /**
   * 注册基于 {@link MessageSourceHolder} 的国际化解析器并注入到 YdszResponse。
   *
   * <p>通过 {@link MessageSourceHolder#resolve(String, Object[], Locale)} 走统一解析路径，享有负缓存 + 缺失节流 + 运行时覆盖
   * 全体系能力。仅当 {@link MessageSourceHolder} 已就绪（即 {@code ydsz-common-locales} 的 {@link
   * com.njydsz.common.locales.config.LocalesAutoConfiguration} 完成 MessageSource 桥接）时生效。
   *
   * @return YdszResponse.MessageResolver 实例（对齐 locales 统一路径）
   */
  @Bean
  @ConditionalOnBean(MessageSource.class)
  public YdszResponse.MessageResolver springMessageResolver() {
    YdszResponse.MessageResolver resolver = (key, defaultValue) -> {
      if (key == null || key.isEmpty()) {
        return defaultValue;
      }
      // 走 MessageSourceHolder 统一路径：负缓存 + 缺失节流 + 运行时覆盖
      return MessageSourceHolder.resolve(key, null, LocaleContextHolder.getLocale());
    };
    YdszResponse.setResolverIfAbsent(resolver);
    return resolver;
  }

  /**
   * 注册分页常量初始化器，在所有单例就绪后将 {@link CoreProperties} 中的分页默认值灌入 {@link PageConstants}。
   *
   * <p>使用 {@link SmartInitializingSingleton} 而非 {@code @PostConstruct} 是为了确保分页常量被其他 Bean
   * 在初始化阶段读取前已完成赋值，避免读到未绑定的默认值。
   *
   * @param properties Core 配置属性
   * @return 分页常量初始化器
   */
  @Bean
  PageConstantsInitializer pageConstantsInitializer(CoreProperties properties) {
    return new PageConstantsInitializer(properties);
  }

  /**
   * 注册特性开关服务。
   *
   * <p>基于 {@code ydsz.core.feature-flags} 配置驱动，并注入到 {@link FeatureFlagContext}
   * 静态门面，供非 Spring 注入场景访问。未配置任何开关时回退为全部开启。
   *
   * @param properties Core 配置属性
   * @return 特性开关服务实例
   */
  @Bean
  @ConditionalOnMissingBean
  public FeatureFlagService featureFlagService(CoreProperties properties) {
    ConfigDrivenFeatureFlagService service =
        new ConfigDrivenFeatureFlagService(properties.getFeatureFlags());
    FeatureFlagContext.setService(service);
    return service;
  }

  /**
   * 在所有单例就绪后把分页默认值灌入 {@link PageConstants} 静态持有者。
   *
   * <p>选择 {@link SmartInitializingSingleton} 而非 {@code @PostConstruct}：分页常量可能被其他 Bean
   * 在初始化阶段读取，必须等全部单例（含可能被 {@code BeanPostProcessor} 增强过的 {@code
   * CoreProperties}）创建完成后再统一赋值，避免读到未绑定的默认值。
   *
   * <p>本类为包级可见的启动期一次性组件，无状态、不对外暴露。
   *
   * @author ydsz-team
   * @since 26.10.01
   */
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
}
