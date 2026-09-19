package com.njydsz.common.base.actuator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import com.njydsz.common.locales.config.I18nProperties;

import lombok.RequiredArgsConstructor;

/**
 * i18n 元数据 Actuator 端点（L2 基础设施信息暴露）。
 *
 * <p>通过 Actuator 端点暴露 i18n 配置与状态信息，供运维人员在线查询国际化运行环境：
 *
 * <ul>
 *   <li>{@code GET /actuator/i18n-metadata} — i18n 配置摘要（编码、默认语言、支持语言数量等）
 *   <li>{@code GET /actuator/i18n-metadata/languages} — 支持的语言标签列表
 * </ul>
 *
 * <p>仅当 {@code management.endpoint.i18n-metadata.enabled=true}（Actuator 端点默认启用策略）且 {@code
 * ydsz.i18n.metadata-api-enabled=true} 时激活。
 *
 * <p><b>设计决策：</b>本端点使用 Spring Boot Actuator {@code @Endpoint} 机制，与 {@link
 * ConfigRegistryEndpoint} 保持一致，避免引入 {@code @RestController} 对 L2 模块的 Web 污染。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Endpoint(id = "i18n-metadata")
@RequiredArgsConstructor
public class I18nMetadataActuatorEndpoint {

  private final I18nProperties i18nProperties;

  /**
   * 读取 i18n 配置摘要。
   *
   * <p>返回编码、默认语言、回退策略、启动校验开关、通配符扫描开关、缺失翻译告警开关、负缓存开关等配置全貌。
   *
   * @return 配置摘要对象
   */
  @ReadOperation
  public I18nConfigSummary config() {
    return new I18nConfigSummary(
        i18nProperties.getDefaultLocale(),
        i18nProperties.getEncoding(),
        i18nProperties.isFallbackToSystemLocale(),
        i18nProperties.getSupportedLocales().length,
        i18nProperties.isValidateOnStartup(),
        i18nProperties.isWildcardScanEnabled(),
        i18nProperties.isMissingTranslationLogEnabled(),
        i18nProperties.isNegativeCacheEnabled());
  }

  /**
   * 读取支持的语言标签列表。
   *
   * @return 语言标签有序列表（如 zh_CN、en_US、zh_TW）
   */
  @ReadOperation
  public List<String> languages() {
    String[] locales = i18nProperties.getSupportedLocales();
    List<String> result = new ArrayList<>(locales.length);
    for (String locale : locales) {
      result.add(locale);
    }
    Collections.sort(result);
    return result;
  }

  /**
   * 校验给定语言标签是否在支持列表中。
   *
   * @param tag 语言标签（如 zh_CN）
   * @return 是否受支持
   */
  @ReadOperation
  public boolean languageSupported(@Selector String tag) {
    return i18nProperties.isSupported(tag);
  }

  /**
   * i18n 配置摘要（供 JSON 序列化）。
   *
   * <p>不可变值对象，所有字段 final + getter。
   */
  public static class I18nConfigSummary {
    private final String defaultLocale;
    private final String encoding;
    private final boolean fallbackToSystemLocale;
    private final int supportedLocaleCount;
    private final boolean validateOnStartup;
    private final boolean wildcardScanEnabled;
    private final boolean missingTranslationLogEnabled;
    private final boolean negativeCacheEnabled;

    public I18nConfigSummary(
        String defaultLocale,
        String encoding,
        boolean fallbackToSystemLocale,
        int supportedLocaleCount,
        boolean validateOnStartup,
        boolean wildcardScanEnabled,
        boolean missingTranslationLogEnabled,
        boolean negativeCacheEnabled) {
      this.defaultLocale = defaultLocale;
      this.encoding = encoding;
      this.fallbackToSystemLocale = fallbackToSystemLocale;
      this.supportedLocaleCount = supportedLocaleCount;
      this.validateOnStartup = validateOnStartup;
      this.wildcardScanEnabled = wildcardScanEnabled;
      this.missingTranslationLogEnabled = missingTranslationLogEnabled;
      this.negativeCacheEnabled = negativeCacheEnabled;
    }

    public String getDefaultLocale() {
      return defaultLocale;
    }

    public String getEncoding() {
      return encoding;
    }

    public boolean isFallbackToSystemLocale() {
      return fallbackToSystemLocale;
    }

    public int getSupportedLocaleCount() {
      return supportedLocaleCount;
    }

    public boolean isValidateOnStartup() {
      return validateOnStartup;
    }

    public boolean isWildcardScanEnabled() {
      return wildcardScanEnabled;
    }

    public boolean isMissingTranslationLogEnabled() {
      return missingTranslationLogEnabled;
    }

    public boolean isNegativeCacheEnabled() {
      return negativeCacheEnabled;
    }
  }
}
