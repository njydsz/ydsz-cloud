package com.njydsz.common.locales.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.common.locales.util.MissingTranslationLogger;

/**
 * 国际化基座自动配置（L2 基础设施）
 *
 * <p>从 {@code ydsz-common-exception} 迁出，统一承载 YDSZ 后端的国际化基础设施：
 *
 * <ul>
 *   <li>{@link MessageSource}（ReloadableResourceBundleMessageSource，多模块资源聚合）
 *   <li>{@link LocaleResolver}（AcceptHeaderLocaleResolver，Web 环境按需加载）
 *   <li>{@link LocaleChangeInterceptor}（?lang= 参数切换）
 *   <li>{@link Validator}（关联 i18n 的 JSR-303 校验器）
 *   <li>{@link I18nMessages}（可注入工具 Bean）
 * </ul>
 *
 * <p>此模块为纯 L2 基础设施，不依赖任何 L3+ 模块，可供异常模块（L3）、Web 模块（L6）、业务模块平等引用。
 *
 * <p><b>使用方式：</b>在 starter pom 中引入 {@code ydsz-common-locales}，即可自动装配以上所有 Bean。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(I18nProperties.class)
@ConditionalOnClass(MessageSource.class)
public class LocalesAutoConfiguration {

  /** MessageSource Bean 名称常量（ydsz 统一约定，避免多模块冲突） */
  public static final String MESSAGE_SOURCE_BEAN_NAME = "ydszMessageSource";

  /** 通配符扫描发现阈值日志：扫描到的新增资源数量 >= 此值时打印 INFO 提示 */
  private static final int DISCOVERY_LOG_THRESHOLD = 1;

  private final I18nProperties i18nProperties;
  private final Environment environment;

  public LocalesAutoConfiguration(I18nProperties i18nProperties, Environment environment) {
    this.i18nProperties = i18nProperties;
    this.environment = environment;
  }

  // ==================== 翻译缺失节流器初始化 ====================

  /**
   * 初始化翻译缺失节流器（{@link MissingTranslationLogger}）。
   *
   * <p>在 MessageSource Bean 创建完成后（依赖 {@code ydszMessageSource}）启用节流器，使后续 i18n 解析缺失时输出 WARN
   * 日志。配置由 {@link I18nProperties#getMissingTranslationLogEnabled()} 与 {@link
   * I18nProperties#getMissingTranslationLogBufferCapacity()} 控制。
   */
  @PostConstruct
  public void configureMissingTranslationLogger() {
    boolean enabled = i18nProperties.isMissingTranslationLogEnabled();
    int capacity = i18nProperties.getMissingTranslationLogBufferCapacity();
    MissingTranslationLogger.configure(enabled, capacity);
    if (enabled) {
      log.info(
          "MissingTranslationLogger 已启用 | 缓冲区容量: {} | 节流器将在 i18n key 未解析时输出 WARN 日志",
          capacity);
    }
  }

  /**
   * 跨语言翻译完整性校验（在 MessageSource 创建后、Bean 初始化完成前调用）。
   *
   * <p>确保默认 Locale（zh_CN）下存在的每个 key 在所有其它支持 Locale 中也有对应翻译。调用链：{@link
   * #validateCrossLocaleCompleteness()}。
   */
  @PostConstruct
  public void performCrossLocaleValidation() {
    validateCrossLocaleCompleteness();
  }

  // ==================== 国际化核心 ====================

  /**
   * 创建全局国际化消息源
   *
   * <p>覆盖 ydsz 所有模块的资源文件（通过 ydsz.i18n.basename 逗号分隔配置）。
   *
   * <p>使用 Bean 名称 {@link #MESSAGE_SOURCE_BEAN_NAME} 而非默认 {@code messageSource}，避免与 Spring
   * Boot 的 MessageSourceAutoConfiguration 默认 Bean 互相干扰。 若消费方仍需要传统 {@code messageSource} 名称，可在
   * application.yml 中通过 {@code spring.messages.basename} 显式指向 —— 此时本 Bean 可通过
   * {@code @ConditionalOnMissingBean} 自动跳过。
   *
   * <p>当 {@code ydsz.i18n.wildcard-scan-enabled=true}（默认），启动时通过 {@link
   * org.springframework.core.io.support.PathMatchingResourcePatternResolver} 自动扫描 {@code
   * classpath*:i18n/*-messages*.properties}，将发现的新增资源前缀与手动配置合并去重后传入 {@link
   * ReloadableResourceBundleMessageSource}，实现新模块零配置被发现。
   *
   * @return MessageSource 实例
   */
  @Bean(name = MESSAGE_SOURCE_BEAN_NAME)
  @ConditionalOnMissingBean(name = MESSAGE_SOURCE_BEAN_NAME)
  public MessageSource ydszMessageSource() {
    boolean isProd = isProdEnvironment();
    int cacheSeconds =
        isProd ? i18nProperties.getProdCacheSeconds() : i18nProperties.getDevCacheSeconds();
    return createMessageSource(cacheSeconds);
  }

  /**
   * 注册国际化消息工具 Bean（可注入方式使用 i18n）。
   *
   * <p>为需要通过 Spring DI 获取 i18n 解析能力的业务代码提供可注入组件。 静态场景请直接使用 {@link
   * com.njydsz.common.locales.util.I18n} 工具类。
   *
   * @param messageSource 国际化消息源
   * @return 国际化消息工具 Bean
   */
  @Bean
  @ConditionalOnMissingBean(I18nMessages.class)
  public I18nMessages i18nMessages(MessageSource messageSource) {
    return new I18nMessages(messageSource);
  }

  /**
   * 注册关联国际化消息源的 JSR-303 验证器
   *
   * <p>校验注解（@NotBlank, @Size, @Email 等的错误消息）使用 i18n 资源文件中的 key 解析。 与 Spring 默认
   * {@code LocalValidatorFactoryBean} 相比，额外关联了 YDSZ 的 {@link MessageSource}。
   *
   * @param messageSource 国际化消息源
   * @return JSR-303 校验器
   */
  @Bean
  @ConditionalOnMissingBean(Validator.class)
  public Validator ydszValidator(MessageSource messageSource) {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    log.info("JSR-303 验证器已关联国际化消息源");
    return validator;
  }

  // ==================== Web 国际化（按需条件加载） ====================

  /**
   * 创建区域解析器（默认 AcceptHeaderLocaleResolver）
   *
   * <p>解析顺序：请求参数 {@code ?lang=xxx} &gt; Accept-Language Header &gt; 默认 Locale（zh_CN）。
   *
   * <p>仅当类路径存在 {@link LocaleResolver} 且当前没有自定义 {@code LocaleResolver} Bean 时才注册， 避免与 Spring Boot 自动配置冲突。
   *
   * @return 区域解析器
   */
  @Bean
  @ConditionalOnClass({LocaleResolver.class, AcceptHeaderLocaleResolver.class})
  @ConditionalOnMissingBean(LocaleResolver.class)
  public LocaleResolver ydszLocaleResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();

    Locale defaultLocale = parseDefaultLocale();
    resolver.setDefaultLocale(defaultLocale);

    List<Locale> localeList = new ArrayList<>(8);
    for (String localeStr : i18nProperties.getSupportedLocales()) {
      localeList.add(parseLocale(localeStr));
    }
    resolver.setSupportedLocales(localeList);

    log.info(
        "LocaleResolver 已注册 | 默认 Locale: {} | 支持语言: {}",
        defaultLocale,
        Arrays.toString(i18nProperties.getSupportedLocales()));

    return resolver;
  }

  /**
   * 创建语言切换拦截器
   *
   * <p>通过 URL 参数（默认 ?lang=en_US）覆盖 Accept-Language 与默认 Locale。
   *
   * @return 语言切换拦截器
   */
  @Bean
  @ConditionalOnClass({LocaleChangeInterceptor.class})
  @ConditionalOnMissingBean(LocaleChangeInterceptor.class)
  public LocaleChangeInterceptor localeChangeInterceptor() {
    LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
    interceptor.setParamName(i18nProperties.getLangParamName());
    log.info("LocaleChangeInterceptor 已注册 | 参数名: {}", i18nProperties.getLangParamName());
    return interceptor;
  }

  // ==================== 辅助方法 ====================

  private boolean isProdEnvironment() {
    String[] activeProfiles = environment != null ? environment.getActiveProfiles() : new String[] {};
    for (String profile : activeProfiles) {
      if ("prod".equalsIgnoreCase(profile)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 校验跨语言翻译完整性：默认 Locale（zh_CN）下的每个 i18n key 是否在所有支持的 Locale 中也有对应翻译。
   *
   * <p>校验在启用了 {@code validateOnStartup=true} 且支持的 Locale 数量 ≥ 2 时生效。若某 key 在 zh_CN 中存在但在
   * en_US / zh_TW 等其它 Locale 中缺失（useCodeAsDefaultMessage=true 导致返回 key 本身），则以
   * MissingTranslationLogger 节流器输出 WARN 日志。
   *
   * <p><b>自包含约束：</b>本方法不依赖 L3+ 模块（如 ydsz-common-exception），仅在 L2 locales 模块内完成资源加载与校验。
   */
  private void validateCrossLocaleCompleteness() {
    if (!i18nProperties.isValidateOnStartup()) {
      return;
    }
    String[] supportedLocales = i18nProperties.getSupportedLocales();
    if (supportedLocales == null || supportedLocales.length < 2) {
      return;
    }
    Locale defaultLocale = parseDefaultLocale();
    // 加载默认 Locale 的 key 集合
    Set<String> defaultLocaleKeys = loadKeysForLocale(defaultLocale);
    if (defaultLocaleKeys.isEmpty()) {
      log.debug("跨语言校验跳过：默认 Locale {} 下未找到任何 i18n key", defaultLocale);
      return;
    }
    int missingCount = 0;
    for (String localeTag : supportedLocales) {
      Locale locale = parseLocale(localeTag);
      if (locale.equals(defaultLocale)) {
        continue;
      }
      Set<String> localeKeys = loadKeysForLocale(locale);
      for (String key : defaultLocaleKeys) {
        if (!localeKeys.contains(key)) {
          missingCount++;
          log.warn(
              "i18n 跨语言翻译缺失：key='{}' 在默认 Locale ({}) 存在，但在 Locale ({}) 缺失对应翻译",
              key,
              defaultLocale,
              localeTag);
        }
      }
    }
    if (missingCount > 0) {
      log.warn(
          "跨语言翻译完整性校验完成：发现 {} 个 key 在默认 Locale ({}) 存在但部分支持 Locale 中缺失",
          missingCount,
          defaultLocale);
    } else {
      log.info(
          "跨语言翻译完整性校验通过：默认 Locale ({}) 中 {} 个 key 在所有 {} 个支持 Locale 中均有翻译",
          defaultLocale,
          defaultLocaleKeys.size(),
          supportedLocales.length);
    }
  }

  /**
   * 加载指定 Locale 下所有资源前缀中的 key 集合。
   *
   * <p>实现方式：通过 {@link PathMatchingResourcePatternResolver} 定位 `{prefix}_{locale}.properties` 文件，
   * 逐文件读取 Properties 并提取所有 key。失败（文件不存在/IO 异常）时回退跳过，不阻断启动。
   *
   * @param locale 目标 Locale
   * @return 该 Locale 下所有 basename 的 key 集合（可能为空但不为 null）
   */
  private Set<String> loadKeysForLocale(Locale locale) {
    Set<String> keys = new LinkedHashSet<>();
    String localeSuffix =
        locale.getCountry().isEmpty()
            ? locale.getLanguage()
            : locale.getLanguage() + "_" + locale.getCountry();
    String[] basenames = i18nProperties.getEffectiveBasenames();
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    for (String basename : basenames) {
      String basenameTrimmed = normalizeBasename(basename);
      // 构造资源路径：classpath:i18n/exception-messages_zh_CN.properties
      String resourcePath = basenameTrimmed + "_" + localeSuffix + ".properties";
      try {
        Resource[] resources = resolver.getResources(resourcePath);
        for (Resource resource : resources) {
          if (resource.exists()) {
            try (InputStream is = resource.getInputStream()) {
              Properties props = new Properties();
              props.load(new InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
              keys.addAll(props.stringPropertyNames());
            }
          }
        }
      } catch (Exception e) {
        // 资源不存在或读取失败时跳过，不阻断
      }
    }
    return keys;
  }

  private MessageSource createMessageSource(int cacheSeconds) {
    ReloadableResourceBundleMessageSource messageSource =
        new ReloadableResourceBundleMessageSource();

    // 获取有效的 basename 列表：手动配置 + 通配符扫描发现（如果启用）
    String[] effectiveBasenames = i18nProperties.getEffectiveBasenames();

    // 记录通配符扫描发现的新增资源
    if (i18nProperties.isWildcardScanEnabled()) {
      int discoveredCount = effectiveBasenames.length - i18nProperties.getBasename().split(",").length;
      if (discoveredCount >= DISCOVERY_LOG_THRESHOLD) {
        log.info(
            "通配符扫描已发现 {} 个 i18n 资源前缀 | 总计: {} 个",
            discoveredCount,
            effectiveBasenames.length);
      } else {
        log.info("通配符扫描已执行 | 总计: {} 个 i18n 资源前缀", effectiveBasenames.length);
      }
    }

    // 清理 basename（去除空白，标准化 classpath 前缀）
    String[] cleanedBasenames = new String[effectiveBasenames.length];
    for (int i = 0; i < effectiveBasenames.length; i++) {
      cleanedBasenames[i] = normalizeBasename(effectiveBasenames[i]);
    }
    messageSource.setBasenames(cleanedBasenames);

    messageSource.setDefaultEncoding(i18nProperties.getEncoding());
    messageSource.setCacheSeconds(cacheSeconds);
    messageSource.setFallbackToSystemLocale(i18nProperties.isFallbackToSystemLocale());
    messageSource.setUseCodeAsDefaultMessage(true);

    log.info(
        "国际化配置已加载 | 资源前缀数: {} | 缓存时间: {}秒 | 支持语言: {} | profiles: {} | wildcardScan: {}",
        cleanedBasenames.length,
        cacheSeconds,
        Arrays.toString(i18nProperties.getSupportedLocales()),
        Arrays.toString(environment != null ? environment.getActiveProfiles() : new String[] {}),
        i18nProperties.isWildcardScanEnabled());

    return messageSource;
  }

  /**
   * 标准化 basename 条目：去除首尾空格、去掉尾部斜杠。
   *
   * @param basename 原始 basename 字符串（如 " classpath:i18n/userinfo-messages "）
   * @return 标准化后的 basename（如 "classpath:i18n/userinfo-messages"）
   */
  private String normalizeBasename(String basename) {
    if (basename == null) {
      return "";
    }
    String trimmed = basename.trim();
    // 去掉尾部斜杠（防止路径拼接异常）
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private Locale parseDefaultLocale() {
    String defaultLocaleTag = i18nProperties.getDefaultLocale();
    if (defaultLocaleTag == null || defaultLocaleTag.isEmpty()) {
      return Locale.CHINA;
    }
    return parseLocale(defaultLocaleTag);
  }

  private Locale parseLocale(String localeStr) {
    if (localeStr == null || localeStr.isEmpty()) {
      return Locale.CHINA;
    }
    String[] parts = localeStr.split("_");
    if (parts.length == 2) {
      return new Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build();
    }
    return Locale.CHINA;
  }
}
