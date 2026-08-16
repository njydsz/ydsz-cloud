package com.njydsz.common.exception.config;

import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.custom.MessageSourceAccessor;
import com.njydsz.common.exception.custom.MessageSourceHolder;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.registry.ExceptionCodeScanner;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * 异常模块核心自动配置
 *
 * <p>合并了原有的 3 个基础配置类：
 *
 * <ul>
 *   <li>国际化核心：{@link MessageSource}、{@link Validator}、消息解析器注入 {@link MessageSourceHolder}
 *   <li>Web 国际化：{@link LocaleResolver}、{@link LocaleChangeInterceptor}
 *   <li>异常指标：{@link ExceptionMetrics}
 * </ul>
 *
 * <p>同时负责错误码注册中心的显式装配：{@link ErrorCodeTable} 与 {@link ExceptionCodeScanner} 均在核心装配中声明（不依赖 Actuator
 * 或组件扫描），保证无 Actuator 依赖的消费方 也能完成错误码注册与 i18n key fail-fast 校验。
 *
 * <p>所有 Web/Actuator 相关能力均通过 {@code @ConditionalOnClass} 条件加载， 保证在纯后端（无 Web 容器）场景下也能使用异常模块的核心功能。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({I18nProperties.class, ExceptionProperties.class})
@ConditionalOnClass(name = "org.springframework.context.MessageSource")
public class YdszExceptionCoreAutoConfiguration {

  /** MessageSource Bean 名称常量 */
  public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";

  private final I18nProperties i18nProperties;
  private final ExceptionProperties exceptionProperties;
  private final Environment environment;
  private final ObjectProvider<MessageSource> messageSourceProvider;

  public YdszExceptionCoreAutoConfiguration(
      I18nProperties i18nProperties,
      ExceptionProperties exceptionProperties,
      ObjectProvider<Environment> environmentProvider,
      ObjectProvider<MessageSource> messageSourceProvider) {
    this.i18nProperties = i18nProperties;
    this.exceptionProperties = exceptionProperties;
    this.environment = environmentProvider.getIfAvailable();
    this.messageSourceProvider = messageSourceProvider;
  }

  // ==================== 错误码注册中心 ====================

  /**
   * 创建统一错误码注册表 Bean。
   *
   * <p>显式声明以消除对消费方组件扫描的隐式依赖，保证任何消费方均可用。
   *
   * @return 处理结果
   */
  @Bean
  @ConditionalOnMissingBean(ErrorCodeTable.class)
  public ErrorCodeTable errorCodeTable() {
    return new ErrorCodeTable();
  }

  /**
   * 创建错误码自动扫描注册器 Bean。
   *
   * <p>扫描与 i18n key 校验在全部单例 Bean 实例化完成后执行（{@code SmartInitializingSingleton}）， 确保 fail-fast
   * 校验基于完整注册表，而非空表空转。
   *
   * @param errorCodeTable errorCodeTable 参数说明
   * @param messageSource 国际化消息源
   * @param env Spring 环境对象
   * @return 处理结果
   */
  @Bean
  @ConditionalOnMissingBean(ExceptionCodeScanner.class)
  public ExceptionCodeScanner exceptionCodeScanner(
      ErrorCodeTable errorCodeTable, MessageSource messageSource, Environment env) {
    boolean validateOnStartup =
        env == null || env.getProperty("ydsz.i18n.validate-on-startup", Boolean.class, true);
    return new ExceptionCodeScanner(errorCodeTable, messageSource, validateOnStartup);
  }

  // ==================== 国际化核心 ====================

  /**
   * 在 Bean 初始化完成后，将 Spring MessageSource 注入 {@link MessageSourceHolder}， 使 {@link
   * AbstractYdszException#getMessage()} 能自动解析 i18n 消息。
   *
   * <p>注入方式：通过 {@link MessageSourceHolder.MessageResolver} 函数式接口桥接， 避免异常模块对 Spring 的硬依赖。
   *
   * <p>i18n 解析策略：
   *
   * <ul>
   *   <li>MessageSource 可用时：{@code getMessage()} 按当前请求 Locale 自动解析 i18n 文案
   *   <li>MessageSource 不可用时：{@code getMessage()} 返回原始 key（兜底）
   * </ul>
   */
  @PostConstruct
  public void injectMessageResolver() {
    MessageSource messageSource = messageSourceProvider.getIfAvailable();
    if (messageSource == null) {
      log.warn("MessageSource 未找到，AbstractYdszException.getMessage() 将降级为返回 i18n key");
      return;
    }
    // 将 Spring MessageSource 桥接注入静态 Holder，实现无侵入的 i18n 解析
    MessageSourceHolder.setResolver(
        (key, params, defaultMsg, locale) -> {
          try {
            Locale resolvedLocale = locale != null ? locale : Locale.ROOT;
            return messageSource.getMessage(key, params, defaultMsg, resolvedLocale);
          } catch (Exception e) {
            // MessageSource 解析失败时返回 defaultMsg（即 messageKey 本身）
            return defaultMsg;
          }
        });
    log.info(
        "异常模块已就绪 | MessageSource 已注入 MessageSourceHolder，getMessage() 启用 i18n 解析 | 实现: {}",
        messageSource.getClass().getSimpleName());
  }

  /**
   * 注册 {@link MessageSourceAccessor} Bean（可注入的消息源访问器）。
   *
   * <p>为需要通过 Spring DI 获取 i18n 解析能力的业务代码提供可注入组件。 静态 {@link MessageSourceHolder} 仍然可用但不利于单元测试，
   * 新代码建议优先使用本 Bean。
   *
   * @param messageSource Spring MessageSource
   * @return MessageSourceAccessor 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public MessageSourceAccessor messageSourceAccessor(MessageSource messageSource) {
    return new MessageSourceAccessor(messageSource);
  }

  /**
   * 创建全局国际化消息源。
   *
   * @return 处理结果
   */
  @Bean(name = MESSAGE_SOURCE_BEAN_NAME)
  @ConditionalOnMissingBean(name = MESSAGE_SOURCE_BEAN_NAME)
  public MessageSource messageSource() {
    boolean isProd = isProdEnvironment();
    int cacheSeconds =
        isProd ? i18nProperties.getProdCacheSeconds() : i18nProperties.getDevCacheSeconds();
    return createMessageSource(cacheSeconds);
  }

  /**
   * 注册关联国际化消息源的 JSR-303 验证器。
   *
   * @param messageSource 国际化消息源
   * @return 处理结果
   */
  @Bean
  @ConditionalOnMissingBean(Validator.class)
  public Validator getValidator(MessageSource messageSource) {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    log.info("验证器已关联国际化消息源");
    return validator;
  }

  // ==================== Web 国际化 ====================

  /**
   * 创建区域解析器 Bean。
   *
   * @return 处理结果
   */
  @Bean
  @ConditionalOnClass({LocaleResolver.class, AcceptHeaderLocaleResolver.class})
  @ConditionalOnMissingBean(LocaleResolver.class)
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(Locale.CHINA);

    List<Locale> localeList = new ArrayList<>(4);
    for (String localeStr : i18nProperties.getSupportedLocales()) {
      String[] parts = localeStr.split("_");
      if (parts.length == 2) {
        localeList.add(new Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build());
      } else {
        localeList.add(Locale.CHINA);
      }
    }
    resolver.setSupportedLocales(localeList);
    return resolver;
  }

  /**
   * 创建语言切换拦截器 Bean。
   *
   * @return 处理结果
   */
  @Bean
  @ConditionalOnClass({LocaleResolver.class, AcceptHeaderLocaleResolver.class})
  @ConditionalOnMissingBean(LocaleChangeInterceptor.class)
  public LocaleChangeInterceptor localeChangeInterceptor() {
    LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
    interceptor.setParamName(i18nProperties.getLangParamName());
    return interceptor;
  }

  // ==================== 异常指标 ====================

  /**
   * 注册异常指标统计器。
   *
   * @param meterRegistry meterRegistry 参数说明
   * @return 处理结果
   */
  @Bean
  @ConditionalOnClass(MeterRegistry.class)
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnProperty(
      prefix = "ydsz.exception",
      name = "metrics-enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean(ExceptionMetrics.class)
  public ExceptionMetrics exceptionMetrics(MeterRegistry meterRegistry) {
    ExceptionMetrics metrics = new ExceptionMetrics(meterRegistry);
    boolean includeCodeTag = exceptionProperties.isMetricsIncludeCodeTag();
    metrics.setIncludeCodeTag(includeCodeTag);
    metrics.setPercentiles(exceptionProperties.getMetricsPercentiles());
    if (includeCodeTag) {
      log.error(
          "[ExceptionMetrics] 已开启高基数 code tag（ydsz.exception.metrics-include-code-tag=true），"
              + "可能导致 Prometheus 存储空间激增与查询性能下降。建议仅在开发/测试环境开启，"
              + "生产环境请通过 ydsz.exception.metrics-include-code-tag=false 关闭，"
              + "或使用 Prometheus 的 Recording Rule 在存储层降采样。");
    }
    if (exceptionProperties.getMetricsPercentiles() != null
        && !exceptionProperties.getMetricsPercentiles().isEmpty()) {
      log.info(
          "[ExceptionMetrics] 异常处理耗时预计算分位数已启用: {}", exceptionProperties.getMetricsPercentiles());
    }
    return metrics;
  }

  // ==================== 辅助方法 ====================

  private boolean isProdEnvironment() {
    String[] activeProfiles =
        environment != null ? environment.getActiveProfiles() : new String[] {};
    for (String profile : activeProfiles) {
      if ("prod".equalsIgnoreCase(profile)) {
        return true;
      }
    }
    return false;
  }

  private MessageSource createMessageSource(int cacheSeconds) {
    ReloadableResourceBundleMessageSource messageSource =
        new ReloadableResourceBundleMessageSource();

    String basename = i18nProperties.getBasename();
    String[] basenameArray = basename.split(",");
    String[] basenames = new String[basenameArray.length];
    for (int i = 0; i < basenameArray.length; i++) {
      basenames[i] = basenameArray[i].trim();
    }
    messageSource.setBasenames(basenames);

    messageSource.setDefaultEncoding(i18nProperties.getEncoding());
    messageSource.setCacheSeconds(cacheSeconds);
    messageSource.setFallbackToSystemLocale(i18nProperties.isFallbackToSystemLocale());
    messageSource.setUseCodeAsDefaultMessage(true);

    log.info(
        "国际化配置加载成功 | 基础路径: {} | 缓存时间: {}秒 | 支持语言: {}",
        i18nProperties.getBasename(),
        cacheSeconds,
        Arrays.toString(i18nProperties.getSupportedLocales()));

    return messageSource;
  }
}
