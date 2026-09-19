package com.njydsz.common.locales.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 已知 locale tags 统一管理（L2 基础设施常量）
 *
 * <p>提供 locale tag 白名单、正则剥离模式等，供 {@link
 * com.njydsz.common.locales.config.I18nProperties#extractBasenameFromResource}、{@link
 * com.njydsz.common.locales.util.Locales#parseLocaleTag(String)}、{@link
 * com.njydsz.common.locales.util.MessageSourceHolder#resolve(String, Object[], Locale)} 等
 * 多处共用。
 *
 * <p><b>变更约定：</b>新增支持的 locale tag（如 ja_JP、ko_KR），在本类 {@link #DEFAULT_KNOWN_TAGS} 与 {@link
 * #DEFAULT_SUPPORTED_TAGS} 两处同步登记即可，无需散落到各模块硬编码。
 *
 * <p><b>locale tag 命名规范：</b>遵循 {@code language_country} 下划线格式（如 zh_CN / en_US）， 不设语言
 * (lower-case language) + 国家 (UPPER-CASE region)，由 IETF BCP 47 派生。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class KnownLocaleTags {

  private KnownLocaleTags() {
    // 常量类禁止实例化
  }

  /**
   * 全量已知的 locale tag 集合（含默认支持 + 扩展观察）
   *
   * <p>用于 {@code extractBasenameFromResource} 中的后缀剥离操作。只要是框架已知的语言-区域组合，都应在此登记，确保
   * 资源文件 basename 能被正确剥离后缀还原。
   */
  public static final Set<String> DEFAULT_KNOWN_TAGS;

  /**
   * 默认向用户开放的 locale tag 集合（受支持的翻译列表）
   *
   * <p>用于 Accept-Language 解析验证、lang 参数校验。{@link
   * com.njydsz.common.locales.config.I18nProperties#getSupportedLocales()} 默认从此处读取。
   */
  public static final Set<String> DEFAULT_SUPPORTED_TAGS;

  /** 已知 locale tag 按长度降序排列的数组（用于正则构建，优先匹配较长的 tag 如 zh_CN 先于 zh） */
  private static final String[] KNOWN_TAGS_DESC;

  /** 编译后的 locale tag 后缀剥离正则（如 _(zh_CN|en_US|zh_TW|ja_JP|ko_KR)$） */
  public static final Pattern LOCALE_SUFFIX_PATTERN;

  /**
   * 从带后缀的 basename 路径中剥离 locale tag，返回裸 basename。
   *
   * <p>例如：{@code classpath:i18n/foo-messages_zh_CN} → {@code classpath:i18n/foo-messages}
   *
   * @param path 可能带 locale tag 后缀的路径
   * @return 已剥离后缀的路径；若无已知后缀则原样返回
   */
  public static String stripLocaleSuffix(String path) {
    if (path == null) {
      return null;
    }
    return LOCALE_SUFFIX_PATTERN.matcher(path).replaceAll("");
  }

  static {
    // 初始化已知 tags
    Set<String> known = new LinkedHashSet<>();
    known.add("zh_CN");
    known.add("en_US");
    known.add("zh_TW");
    known.add("ja_JP");
    known.add("ko_KR");
    DEFAULT_KNOWN_TAGS = Collections.unmodifiableSet(known);

    Set<String> supported = new LinkedHashSet<>();
    supported.add("zh_CN");
    supported.add("en_US");
    supported.add("zh_TW");
    DEFAULT_SUPPORTED_TAGS = Collections.unmodifiableSet(supported);

    // 降序排列（优先匹配长 tag）
    KNOWN_TAGS_DESC = DEFAULT_KNOWN_TAGS.toArray(new String[0]);
    Arrays.sort(KNOWN_TAGS_DESC, (a, b) -> Integer.compare(b.length(), a.length()));

    // 构建正则：_(zh_CN|en_US|zh_TW|ja_JP|ko_KR)$
    StringBuilder patternBuilder = new StringBuilder("_(");
    for (int i = 0; i < KNOWN_TAGS_DESC.length; i++) {
      if (i > 0) {
        patternBuilder.append("|");
      }
      patternBuilder.append(KNOWN_TAGS_DESC[i]);
    }
    patternBuilder.append(")$");
    LOCALE_SUFFIX_PATTERN = Pattern.compile(patternBuilder.toString());
  }

  /**
   * 判断给定的 locale tag 是否为框架已知的合法 locale tag。
   *
   * @param tag locale tag（如 zh_CN、en_US、ja_JP）
   * @return 已知返回 true
   */
  public static boolean isKnown(String tag) {
    return tag != null && DEFAULT_KNOWN_TAGS.contains(tag);
  }

  /**
   * 判断给定的 locale tag 是否为框架默认向用户开放的翻译语言。
   *
   * <p>注意：已知 ≠ 开放。ja_JP / ko_KR 可能在 {@link #DEFAULT_KNOWN_TAGS} 中（已有资源文件），但未在
   * {@link #DEFAULT_SUPPORTED_TAGS} 中（尚未正式对外提供）。
   *
   * @param tag locale tag
   * @return 默认开放返回 true
   */
  public static boolean isSupported(String tag) {
    return tag != null && DEFAULT_SUPPORTED_TAGS.contains(tag);
  }

  /**
   * 将 locale tag 字符串解析为 {@link Locale} 实例。
   *
   * <p>已知 locale tag 走白名单直接构造；未知 tag 回退到 Spring 默认解析：单段为纯语言，双段按 language_region 构造。
   *
   * @param tag locale tag（如 zh_CN / en_US / zh）
   * @return 对应的 Locale；tag 为 null 或空时返回 null
   */
  public static Locale toLocale(String tag) {
    if (tag == null || tag.isEmpty()) {
      return null;
    }
    String[] parts = tag.split("_");
    if (parts.length == 2) {
      return new Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build();
    }
    if (parts.length == 1) {
      return new Locale.Builder().setLanguage(parts[0]).build();
    }
    // 三段及以上未知格式，回退到 ROOT
    return Locale.ROOT;
  }

  /**
   * 从已知 locale tags 构造正则兼容的 {@link Locale}，若 tag 不在已知列表中则返回 {@link Locale#ROOT}。
   *
   * <p>适用于需要严格白名单的输入校验场景（如 setLocale / parseLangParam）。
   *
   * @param tag 语言标签
   * @return 合法 Locale；未知 tag 返回 {@link Locale#ROOT}
   */
  public static Locale toKnownLocale(String tag) {
    if (!isKnown(tag)) {
      return Locale.ROOT;
    }
    return toLocale(tag);
  }
}
