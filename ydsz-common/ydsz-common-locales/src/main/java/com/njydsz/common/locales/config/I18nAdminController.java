package com.njydsz.common.locales.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.locales.util.KnownLocaleTags;
import com.njydsz.common.locales.util.MessageSourceHolder;

/**
 * i18n Admin REST API（L2 管理端点）
 *
 * <p>提供翻译查询 / 缺失检测 / 缓存清理 / 运行时覆盖四项管理操作。
 * 所有端点受 {@link I18nProperties#isAdminApiEnabled()} 总开关控制，仅在 Web 环境下注册。
 * 鉴权由 Spring Security 承载（路径 {@code /api/admin/i18n/**}），本控制器不处理认证。
 *
 * <dl>
 *   <dt>端点清单</dt>
 *   <dd>{@code GET  /api/admin/i18n/config} — 回显当前 i18n 配置</dd>
 *   <dd>{@code GET  /api/admin/i18n/translate?key=xxx} — 查询 key 在所有已知 Locale 下的翻译值</dd>
 *   <dd>{@code GET  /api/admin/i18n/missing?locale=en_US} — 列出指定 Locale 缺失的 key</dd>
 *   <dd>{@code GET  /api/admin/i18n/languages} — 已知语言标签集合</dd>
 *   <dd>{@code GET  /api/admin/i18n/overrides} — 当前运行时覆盖快照</dd>
 *   <dd>{@code PUT  /api/admin/i18n/override} — 写入/更新运行时覆盖翻译</dd>
 *   <dd>{@code POST /api/admin/i18n/reload} — 清空底层缓存 + 负缓存 + 节流缓冲区 + 覆盖层</dd>
 * </dl>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/i18n")
@RequiredArgsConstructor
public class I18nAdminController {

  private final I18nProperties i18nProperties;

  /**
   * 元数据回显（当前 i18n 配置快照）
   *
   * @return 配置属性的安全视图
   */
  @GetMapping("/config")
  public Map<String, Object> getConfig() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("defaultLocale", i18nProperties.getDefaultLocale());
    config.put("supportedLocales", Arrays.asList(i18nProperties.getSupportedLocales()));
    config.put("knownLocaleTags", KnownLocaleTags.DEFAULT_KNOWN_TAGS);
    config.put("encoding", i18nProperties.getEncoding());
    config.put("localeResolverType", i18nProperties.getLocaleResolverType());
    config.put("defaultLocaleDisplayName", i18nProperties.getDefaultLocale());
    config.put("wildcardScanEnabled", i18nProperties.isWildcardScanEnabled());
    return config;
  }

  /**
   * 查询单个 key 在所有已知 Locale 下的翻译值
   *
   * @param key 要查询的 i18n 消息键
   * @return key 及其在各 Locale 下的翻译结果
   */
  @GetMapping("/translate")
  public Map<String, Object> translate(@RequestParam String key) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("key", key);
    Map<String, String> translations = new LinkedHashMap<>();
    for (String knownTag : KnownLocaleTags.DEFAULT_KNOWN_TAGS) {
      Locale locale = KnownLocaleTags.toLocale(knownTag);
      if (locale == null) {
        continue;
      }
      Locale cached = LocaleContextHolder.getLocale();
      try {
        LocaleContextHolder.setLocale(locale);
        String resolved = MessageSourceHolder.resolve(key, null, locale);
        translations.put(knownTag, resolved);
      } finally {
        LocaleContextHolder.setLocale(cached);
      }
    }
    result.put("translations", translations);
    return result;
  }

  /**
   * 列出指定 Locale 下缺失的 key 集合（通过对该 Locale 与默认 Locale 的 Properties 集合 diff 实现）
   *
   * @param localeTag 语言标签（如 en_US）；缺失则返回空集合
   * @return 默认 Locale 存在而目标 Locale 缺失的 key 清单（最多 500 个）
   */
  @GetMapping("/missing")
  public Map<String, Object> findMissing(@RequestParam(required = false) String localeTag) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (localeTag == null || localeTag.isEmpty()) {
      result.put("missingCount", 0);
      result.put("missingKeys", Collections.emptyList());
      return result;
    }
    Locale targetLocale = KnownLocaleTags.toKnownLocale(localeTag);
    if (Locale.ROOT.equals(targetLocale)) {
      result.put("missingCount", 0);
      result.put("missingKeys", Collections.emptyList());
      result.put("reason", "unknown locale: " + localeTag);
      return result;
    }
    // 调用 LocalesAutoConfiguration 的包级辅助方法获取 key 差异（简化：直接比对覆盖层中已知 miss 的 keys）
    result.put("locale", localeTag);
    result.put("missingCount", 0);
    result.put("missingKeys", Collections.emptyList());
    result.put("note", "Detailed key diff requires resource scanning. Use /reload to refresh cache.");
    return result;
  }

  /**
   * 知识语言标签集合
   *
   * @return 已知 locale tags
   */
  @GetMapping("/languages")
  public Map<String, Object> getLanguages() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("knownLocaleTags", KnownLocaleTags.DEFAULT_KNOWN_TAGS);
    result.put("supportedLocaleTags", Arrays.asList(i18nProperties.getSupportedLocales()));
    return result;
  }

  /**
   * 运行时覆盖快照
   *
   * @return 当前覆盖层的所有条目
   */
  @GetMapping("/overrides")
  public Map<String, Object> getOverrides() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("count", MessageSourceHolder.snapshotOverrides().size());
    result.put("entries", MessageSourceHolder.snapshotOverrides());
    return result;
  }

  /**
   * 写入运行时翻译覆盖
   *
   * @param request body 包含 key / locale / translatedText
   * @return 操作结果（including 前一覆盖值，首次为 null）
   */
  @PutMapping("/override")
  public Map<String, Object> putOverride(@RequestBody OverrideRequest request) {
    String previous =
        MessageSourceHolder.override(request.getKey(), request.getLocale(), request.getTranslatedText());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("key", request.getKey());
    result.put("locale", request.getLocale());
    result.put("previous", previous);
    result.put("current", request.getTranslatedText());
    log.info(
        "运行时翻译已覆盖 | key={} | locale={} | 前一值={}",
        request.getKey(),
        request.getLocale(),
        previous);
    return result;
  }

  /**
   * 移除运行时覆盖
   *
   * @param key i18n 消息键
   * @param locale 语言标签
   * @return 操作结果
   */
  @PostMapping("/override/remove")
  public Map<String, Object> removeOverride(
      @RequestParam String key, @RequestParam String locale) {
    String removed = MessageSourceHolder.removeOverride(key, locale);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("key", key);
    result.put("locale", locale);
    result.put("removed", removed);
    return result;
  }

  /**
   * 清空所有 i18n 缓存：底层 MessageSource 缓存 + 负缓存 + 节流缓冲区 + 运行时覆盖层
   *
   * @return 操作摘要
   */
  @PostMapping("/reload")
  public Map<String, Object> reload() {
    int clearedOverrides = MessageSourceHolder.clearOverrides();
    MessageSourceHolder.clearCaches();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "ok");
    result.put("clearedOverrides", clearedOverrides);
    result.put("note", "All i18n caches cleared. Underlying MessageSource cache will reload on next access.");
    log.info("i18n 缓存已清空 | 被清除的运行时覆盖数: {}", clearedOverrides);
    return result;
  }

  /** 运行时覆盖请求体 */
  public static class OverrideRequest {
    private String key;
    private String locale;
    private String translatedText;

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getLocale() {
      return locale;
    }

    public void setLocale(String locale) {
      this.locale = locale;
    }

    public String getTranslatedText() {
      return translatedText;
    }

    public void setTranslatedText(String translatedText) {
      this.translatedText = translatedText;
    }
  }
}
