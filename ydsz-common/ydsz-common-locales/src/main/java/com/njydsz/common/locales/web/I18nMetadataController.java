package com.njydsz.common.locales.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.locales.config.I18nProperties;

/**
 * i18n 元数据 REST API（L2 基础设施，Web 环境按需加载）。
 *
 * <p>暴露以下端点，供前端/运维查询 i18n 配置状态：
 *
 * <ul>
 *   <li>{@code GET /api/internal/i18n/languages} — 支持的语言列表
 *   <li>{@code GET /api/internal/i18n/languages/supported} — 是否支持某语言（query: tag）
 *   <li>{@code GET /api/internal/i18n/config} — i18n 配置摘要
 * </ul>
 *
 * <p>仅当类路径存在 {@link RestController} 且 {@code ydsz.i18n.metadata-api-enabled=true}（默认 false）时启用。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@RestController
@RequestMapping("/api/internal/i18n")
@ConditionalOnWebApplication
@ConditionalOnProperty(
    prefix = "ydsz.i18n",
    name = "metadata-api-enabled",
    havingValue = "true",
    matchIfMissing = false)
@RequiredArgsConstructor
public class I18nMetadataController {

  private final I18nProperties i18nProperties;

  /**
   * 获取支持的语言标签集合。
   *
   * @return 支持的语言标签（如 zh_CN、en_US、zh_TW），排序后返回
   */
  @GetMapping("/languages")
  public List<String> listSupportedLanguages() {
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
  @GetMapping("/languages/supported")
  public boolean isLanguageSupported(@RequestParam("tag") String tag) {
    return i18nProperties.isSupported(tag);
  }

  /**
   * 获取 i18n 配置摘要（编码、默认语言、支持语言数量等）。
   *
   * @return 配置摘要视图
   */
  @GetMapping("/config")
  public I18nConfigSummary getConfig() {
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
   * i18n 配置摘要视图（供 JSON 序列化）。
   *
   * <p>不可变数据对象，所有字段 final + getter。
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
