package com.njydsz.common.exception.config;

import java.util.Locale;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.registry.ExceptionCodeScanner;
import com.njydsz.common.locales.config.I18nProperties;
import com.njydsz.common.locales.config.LocalesAutoConfiguration;
import com.njydsz.common.locales.util.MessageSourceHolder;

/**
 * 异常模块核心自动配置
 *
 * <p>职责范围：
 *
 * <ul>
 *   <li>错误码注册中心：{@link ErrorCodeTable} 与 {@link ExceptionCodeScanner}
 *   <li>异常指标统计：{@link ExceptionMetrics}
 *   <li>将 Spring MessageSource 注入 {@link MessageSourceHolder} 静态桥（由 {@link LocalesAutoConfiguration}
 *       提供的 ydszMessageSource Bean）
 * </ul>
 *
 * <p>国际化相关的 Bean（MessageSource / LocaleResolver / Validator 等）已迁移至 {@link
 * LocalesAutoConfiguration}（ydsz-common-locales）。 本配置通过 {@code @AutoConfigureAfter(LocalesAutoConfiguration.class)}
 * 确保国际化的 MessageSource 已在之后再执行静态注入，保证启动顺序正确。
 *
 * <p>所有 Web/Actuator 相关能力均通过 {@code @ConditionalOnClass} 条件加载， 保证在纯后端（无 Web 容器）场景下也能使用异常模块的核心功能。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(LocalesAutoConfiguration.class)
@EnableConfigurationProperties(ExceptionProperties.class)
@ConditionalOnClass(MessageSource.class)
public class YdszExceptionCoreAutoConfiguration {

  private final ExceptionProperties exceptionProperties;
  private final Environment environment;
  private final ObjectProvider<MessageSource> messageSourceProvider;

  public YdszExceptionCoreAutoConfiguration(
      ExceptionProperties exceptionProperties,
      ObjectProvider<Environment> environmentProvider,
      ObjectProvider<MessageSource> messageSourceProvider) {
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
   * 校验基于完整注册表，而非空表空转。 {@code i18nProperties} 用于执行资源文件 basename 存在性校验（26.09.18 增强）。
   *
   * @param errorCodeTable 错误码注册表，扫描到的 {@code @YdszExceptionCode} 全部注册到此表；
   *     由 {@link #errorCodeTable()} 提供，容器缺失时由该方法兜底创建空表
   * @param messageSource 国际化消息源
   * @param env Spring 环境对象
   * @param i18nPropertiesProvider i18n 配置属性提供器（通过 {@link LocalesAutoConfiguration} 注册），可为 null
   * @return 处理结果
   */
  @Bean
  @ConditionalOnMissingBean(ExceptionCodeScanner.class)
  public ExceptionCodeScanner exceptionCodeScanner(
      ErrorCodeTable errorCodeTable,
      MessageSource messageSource,
      Environment env,
      ObjectProvider<I18nProperties> i18nPropertiesProvider) {
    boolean validateOnStartup =
        env == null || env.getProperty("ydsz.i18n.validate-on-startup", Boolean.class, true);
    I18nProperties i18nProperties = i18nPropertiesProvider.getIfAvailable();
    return new ExceptionCodeScanner(errorCodeTable, messageSource, validateOnStartup, i18nProperties);
  }

  // ==================== MessageSource 静态注入 ====================

  /**
   * 在 Bean 初始化完成后，将 Spring MessageSource 注入 {@link com.njydsz.common.locales.util.MessageSourceHolder}，
   * 使 {@link com.njydsz.common.exception.custom.AbstractYdszException#getMessage()} 能自动解析 i18n 消息。
   *
   * <p>注入方式：通过 {@link com.njydsz.common.locales.util.MessageSourceHolder.MessageResolver}
   * 函数式接口桥接，避免异常模块对 Spring 的硬依赖。
   *
   * <p>i18n 解析策略：
   *
   * <ul>
   *   <li>MessageSource 可用时：{@code getMessage()} 按当前请求 Locale 自动解析 i18n 文案
   *   <li>MessageSource 不可用时：{@code getMessage()} 返回原始 key（兜底）
   * </ul>
   *
   * <p>注入来源：由 {@link LocalesAutoConfiguration} 提供的 {@code ydzsMessageSource} Bean， 通过 {@code
   * @AutoConfigureAfter} 保证加载顺序。
   */
  @PostConstruct
  public void injectMessageResolver() {
    MessageSource messageSource = messageSourceProvider.getIfAvailable();
    if (messageSource == null) {
      log.warn("MessageSource 未找到，AbstractYdszException.getMessage() 将降级为返回 i18n key");
      return;
    }
    // 将 Spring MessageSource 桥接注入静态 Holder（位于 L2 locales 模块），实现无侵入的 i18n 解析
    MessageSourceHolder.setResolver(
        (key, params, defaultMsg, locale) -> {
          try {
            Locale resolvedLocale = locale != null ? locale : MessageSourceHolder.currentLocale();
            return messageSource.getMessage(key, params, defaultMsg, resolvedLocale);
          } catch (Exception e) {
            return defaultMsg;
          }
        });
    log.info("异常模块已就绪 | MessageSource 已注入 MessageSourceHolder，getMessage() 启用 i18n 解析");
  }

  // ==================== 异常指标 ====================

  /**
   * 注册异常指标统计器。
   *
   * @param meterRegistry Micrometer 指标注册表，由 Spring Boot Actuator 提供；
   *     仅在类路径与实际 Bean 均存在时才注册本指标统计器
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

}
