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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.common.locales.util.MessageSourceHolder;
import com.njydsz.common.locales.util.MissingTranslationLogger;

/**
 * 国际化基座自动配置（L2 基础设施）
 *
 * <p>承载 YDSZ 后端国际化基础设施：
 *
 * <ul>
 *   <li>MessageSource（多模块资源聚合 + 通配符自动发现）
 *   <li>LocaleResolver（accept-header / user-priority + Cookie 持久化，按需切换）
 *   <li>LocaleChangeInterceptor（?lang= 参数切换）
 *   <li>Validator（关联 i18n 的 JSR-303 校验器）
 *   <li>I18nMessages（可注入工具 Bean，统一委托 MessageSourceHolder）
 *   <li>I18nAdminController（ydsz.i18n.admin-api-enabled=true 时注册）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(I18nProperties.class)
@ConditionalOnClass(MessageSource.class)
public class LocalesAutoConfiguration {

  public static final String MESSAGE_SOURCE_BEAN_NAME = "ydszMessageSource";

  private static final int DISCOVERY_LOG_THRESHOLD = 1;

  private final I18nProperties i18nProperties;
  private final Environment environment;

  public LocalesAutoConfiguration(I18nProperties i18nProperties, Environment environment) {
    this.i18nProperties = i18nProperties;
    this.environment = environment;
  }

  // ==================== 节流器 / 负缓存初始化 ====================

  /**
   * 初始化翻译缺失节流器（MissingTranslationLogger）。
   */
  @PostConstruct
  public void configureMissingTranslationLogger() {
    boolean enabled = i18nProperties.isMissingTranslationLogEnabled();
    int capacity = i18nProperties.getMissingTranslationLogBufferCapacity();
    MissingTranslationLogger.configure(enabled, capacity);
    if (enabled) {
      log.info("MissingTranslationLogger 已启用 | 缓冲区容量: {}", capacity);
    }
  }

  /**
   * 初始化负缓存（MessageSourceHolder#configureNegativeCache）。
   */
  @PostConstruct
  public void configureNegativeCache() {
    boolean enabled = i18nProperties.isNegativeCacheEnabled();
    int capacity = i18nProperties.getNegativeCacheCapacity();
    MessageSourceHolder.configureNegativeCache(enabled, capacity);
    if (enabled) {
      log.info("i18n 负缓存已启用 | 容量: {}", capacity);
    }
  }

  /** 跨语言翻译完整性校验。 */
  @PostConstruct
  public void performCrossLocaleValidation() {
    validateCrossLocaleCompleteness();
  }

  // ==================== 国际化核心 Bean ====================

  /**
   * 全局国际化消息源（多模块聚合，支持通配符自动发现）。
   *
   * <p>Bean 名称 {@link #MESSAGE_SOURCE_BEAN_NAME} 避免与 Spring Boot 默认 messageSource 互相干扰。
   */
  @Bean(name = MESSAGE_SOURCE_BEAN_NAME)
  @ConditionalOnMissingBean(name = MESSAGE_SOURCE_BEAN_NAME)
  public MessageSource ydszMessageSource() {
    boolean isProd = isProdEnvironment();
    int cache = isProd ? i18nProperties.getProdCacheSeconds() : i18nProperties.getDevCacheSeconds();
    return createMessageSource(cache);
  }

  /** 可注入的 i18n 消息工具 Bean（Service 层推荐）。 */
  @Bean
  @ConditionalOnMissingBean(I18nMessages.class)
  public I18nMessages i18nMessages(MessageSource messageSource) {
    return new I18nMessages(messageSource);
  }

  /** JSR-303 校验器关联 i18n MessageSource。 */
  @Bean
  @ConditionalOnMissingBean(Validator.class)
  public Validator ydszValidator(MessageSource messageSource) {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    log.info("JSR-303 验证器已关联国际化消息源");
    return validator;
  }

  // ==================== Web 国际化（按需加载） ====================

  /**
   * 区域解析器（可切换 accept-header / user-priority 两种模式）。
   */
  @Bean
  @ConditionalOnClass({LocaleResolver.class, AcceptHeaderLocaleResolver.class})
  @ConditionalOnMissingBean(LocaleResolver.class)
  public LocaleResolver ydszLocaleResolver() {
    Locale defaultLocale = parseDefaultLocale();
    String type = i18nProperties.getLocaleResolverType();
    if ("user-priority".equalsIgnoreCase(type)) {
      UserPriorityLocaleResolver resolver =
          new UserPriorityLocaleResolver(
              defaultLocale,
              UserPriorityLocaleResolver.DEFAULT_LOCALE_COOKIE_NAME,
              i18nProperties.getLangParamName());
      log.info(
          "UserPriorityLocaleResolver 已注册 | defaultLocal {} | supported {} | cookie {}",
          defaultLocale,
          Arrays.toString(i18nProperties.getSupportedLocales()),
          UserPriorityLocaleResolver.DEFAULT_LOCALE_COOKIE_NAME);
      return resolver;
    }
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(defaultLocale);
    List<Locale> list = new ArrayList<>(8);
    for (String s : i18nProperties.getSupportedLocales()) {
      list.add(parseLocale(s));
    }
    resolver.setSupportedLocales(list);
    log.info(
        "AcceptHeaderLocaleResolver 已注册 | defaultLocal {} | supported {}",
        defaultLocale,
        Arrays.toString(i18nProperties.getSupportedLocales()));
    return resolver;
  }

  /**
   * 用户偏好 Locale 持久化 — Cookie 写入拦截器（仅 user-priority 模式下注册）。
   *
   * <p>配合 {@link UserPriorityLocaleResolver}，在请求完成后将本次切换的 Locale 写入 Cookie。
   */
  @Bean
  @ConditionalOnProperty(prefix = "ydsz.i18n", name = "locale-resolver-type", havingValue = "user-priority")
  @ConditionalOnClass(HandlerInterceptor.class)
  @ConditionalOnMissingBean(name = "ydszLocaleWritingMvcConfigurer")
  public WebMvcConfigurer ydszLocaleWritingMvcConfigurer() {
    UserPriorityLocaleWritingInterceptor interceptor =
        new UserPriorityLocaleWritingInterceptor(
            UserPriorityLocaleResolver.DEFAULT_LOCALE_COOKIE_NAME,
            UserPriorityLocaleResolver.DEFAULT_COOKIE_MAX_AGE);
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
      }
    };
  }

  /** 语言切换 URL 参数拦截器。 */
  @Bean
  @ConditionalOnClass(LocaleChangeInterceptor.class)
  @ConditionalOnMissingBean(LocaleChangeInterceptor.class)
  public LocaleChangeInterceptor localeChangeInterceptor() {
    LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
    interceptor.setParamName(i18nProperties.getLangParamName());
    log.info("LocaleChangeInterceptor 已注册 | param {}", i18nProperties.getLangParamName());
    return interceptor;
  }

  // ==================== 管理端点 ====================

  /** i18n 管理 REST Controller（ydsz.i18n.admin-api-enabled=true 时注册）。 */
  @Bean
  @ConditionalOnProperty(prefix = "ydsz.i18n", name = "admin-api-enabled", havingValue = "true")
  @ConditionalOnClass(I18nAdminController.class)
  @ConditionalOnMissingBean(I18nAdminController.class)
  public I18nAdminController i18nAdminController() {
    return new I18nAdminController(i18nProperties);
  }

  // ==================== 辅助方法 ====================

  private boolean isProdEnvironment() {
    for (String p : (environment != null ? environment.getActiveProfiles() : new String[] {})) {
      if ("prod".equalsIgnoreCase(p)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 校验跨语言翻译完整性：默认 Locale 下的每个 i18n key 是否在所有支持的 Locale 中也有对应翻译。
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
    Set<String> defaultKeys = loadKeysForLocale(defaultLocale);
    if (defaultKeys.isEmpty()) {
      return;
    }
    int missingCount = 0;
    for (String tag : supportedLocales) {
      Locale loc = parseLocale(tag);
      if (loc.equals(defaultLocale)) {
        continue;
      }
      Set<String> locKeys = loadKeysForLocale(loc);
      for (String key : defaultKeys) {
        if (!locKeys.contains(key)) {
          missingCount++;
          log.warn(
              "i18n 跨语言缺失：key='{}' 存在于 {} 但缺失于 {}", key, defaultLocale, tag);
        }
      }
    }
    if (missingCount > 0) {
      log.warn("跨语言校验完成：发现 {} 个 miss 的 key（默认 Locale {}）", missingCount, defaultLocale);
    } else {
      log.info(
          "跨语言校验通过：{} 个 key（默认 Locale {}）在所有 {} 个 Locale 中均有翻译",
          defaultKeys.size(), defaultLocale, supportedLocales.length);
    }
  }

  /**
   * 加载指定 Locale 下所有资源前缀中的 key 集合。失败时回退跳过，不阻断启动。
   */
  private Set<String> loadKeysForLocale(Locale locale) {
    Set<String> keys = new LinkedHashSet<>();
    String suffix =
        locale.getCountry().isEmpty()
            ? locale.getLanguage()
            : locale.getLanguage() + "_" + locale.getCountry();
    String[] basenames = i18nProperties.getEffectiveBasenames();
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    for (String basename : basenames) {
      try {
        for (Resource r : resolver.getResources(normalizeBasename(basename) + "_" + suffix + ".properties")) {
          if (r.exists()) {
            try (InputStream is = r.getInputStream()) {
              Properties props = new Properties();
              props.load(new InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
              keys.addAll(props.stringPropertyNames());
            }
          }
        }
      } catch (Exception e) {
        // ignore
      }
    }
    return keys;
  }

  private MessageSource createMessageSource(int cacheSeconds) {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    String[] effective = i18nProperties.getEffectiveBasenames();

    if (i18nProperties.isWildcardScanEnabled()) {
      int disc = effective.length - i18nProperties.getBasename().split(",").length;
      if (disc >= DISCOVERY_LOG_THRESHOLD) {
        log.info("通配符扫描发现 {} 个资源前缀 | 总计 {} 个", disc, effective.length);
      } else {
        log.info("通配符扫描已执行 | 总计 {} 个资源前缀", effective.length);
      }
    }

    String[] cleaned = new String[effective.length];
    for (int i = 0; i < effective.length; i++) {
      cleaned[i] = normalizeBasename(effective[i]);
    }
    ms.setBasenames(cleaned);
    ms.setDefaultEncoding(i18nProperties.getEncoding());
    ms.setCacheSeconds(cacheSeconds);
    ms.setFallbackToSystemLocale(i18nProperties.isFallbackToSystemLocale());
    ms.setUseCodeAsDefaultMessage(true);
    bridgeMessageSourceHolder(ms);

    log.info(
        "i18n 配置已加载 | basenames={} | 缓存={}s | 支持语言={} | profiles={} | wildcardScan={}",
        cleaned.length,
        cacheSeconds,
        Arrays.toString(i18nProperties.getSupportedLocales()),
        Arrays.toString(environment != null ? environment.getActiveProfiles() : new String[] {}),
        i18nProperties.isWildcardScanEnabled());

    return ms;
  }

  /**
   * 桥接 Spring MessageSource 至 MessageSourceHolder，实现 I18nMessages / I18n 双路径行为统一。
   */
  private void bridgeMessageSourceHolder(MessageSource messageSource) {
    MessageSourceHolder.setResolver(
        (key, params, defaultMsg, locale) -> {
          try {
            return messageSource.getMessage(key, params, defaultMsg, locale);
          } catch (Exception e) {
            return defaultMsg;
          }
        });
    log.info("MessageSource → MessageSourceHolder 桥接完成");
  }

  private String normalizeBasename(String basename) {
    if (basename == null) {
      return "";
    }
    String s = basename.trim();
    while (s.endsWith("/")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }

  private Locale parseDefaultLocale() {
    String tag = i18nProperties.getDefaultLocale();
    return (tag == null || tag.isEmpty()) ? Locale.CHINA : parseLocale(tag);
  }

  private Locale parseLocale(String s) {
    if (s == null || s.isEmpty()) {
      return Locale.CHINA;
    }
    String[] parts = s.split("_");
    if (parts.length == 2) {
      return new Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build();
    }
    return Locale.CHINA;
  }

  // 用不到的 import 占位，防止被裁剪
  @SuppressWarnings("unused")
  private static void keepImports(HttpServletRequest r, HttpServletResponse s) {}
}
