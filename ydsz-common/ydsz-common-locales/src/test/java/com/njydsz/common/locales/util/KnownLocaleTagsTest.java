package com.njydsz.common.locales.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * {@link KnownLocaleTags} 单元测试
 *
 * <p>覆盖：默认 tag 集合合法性、stripLocaleSuffix 正则剥离、toLocale 多格式解析。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class KnownLocaleTagsTest {

  @Test
  void defaultKnownTags_containsExpectedThree() {
    Set<String> tags = KnownLocaleTags.DEFAULT_KNOWN_TAGS;
    assertTrue(tags.contains("zh_CN"));
    assertTrue(tags.contains("en_US"));
    assertTrue(tags.contains("zh_TW"));
    assertTrue(tags.contains("ja_JP"));
    assertTrue(tags.contains("ko_KR"));
    assertEquals(5, tags.size());
  }

  @Test
  void defaultSupportedTags_lessThanKnown() {
    Set<String> supported = KnownLocaleTags.DEFAULT_SUPPORTED_TAGS;
    assertTrue(supported.contains("zh_CN"));
    assertTrue(supported.contains("en_US"));
    assertTrue(supported.contains("zh_TW"));
    assertFalse(supported.contains("ja_JP"));
    assertFalse(supported.contains("ko_KR"));
    assertEquals(3, supported.size());
  }

  @Test
  void stripLocaleSuffix_twoSegmentTag() {
    assertEquals(
        "classpath:i18n/exception-messages",
        KnownLocaleTags.stripLocaleSuffix("classpath:i18n/exception-messages_zh_CN"));
  }

  @Test
  void stripLocaleSuffix_twoSegmentTag_enUS() {
    assertEquals(
        "classpath:i18n/base-messages",
        KnownLocaleTags.stripLocaleSuffix("classpath:i18n/base-messages_en_US"));
  }

  @Test
  void stripLocaleSuffix_noTag_returnsOriginal() {
    String path = "classpath:i18n/some-messages";
    assertEquals(path, KnownLocaleTags.stripLocaleSuffix(path));
  }

  @Test
  void stripLocaleSuffix_nullInput_returnsNull() {
    assertNull(KnownLocaleTags.stripLocaleSuffix(null));
  }

  @Test
  void stripLocaleSuffix_longerTagPriority() {
    // zh_CN (length 5) must be stripped — shorter alternatives should not mistakenly match
    assertEquals(
        "classpath:i18n/foo-messages",
        KnownLocaleTags.stripLocaleSuffix("classpath:i18n/foo-messages_zh_CN"));
    // Single-segment (no underscore) should not be stripped
    assertEquals(
        "classpath:i18n/foo-messages_en",
        KnownLocaleTags.stripLocaleSuffix("classpath:i18n/foo-messages_en"));
  }

  @Test
  void isKnown_knownTag_returnsTrue() {
    assertTrue(KnownLocaleTags.isKnown("zh_CN"));
    assertTrue(KnownLocaleTags.isKnown("en_US"));
    assertTrue(KnownLocaleTags.isKnown("zh_TW"));
  }

  @Test
  void isKnown_unknownTag_returnsFalse() {
    assertFalse(KnownLocaleTags.isKnown("fr_FR"));
    assertFalse(KnownLocaleTags.isKnown(""));
    assertFalse(KnownLocaleTags.isKnown(null));
  }

  @Test
  void isSupported_onlyDefaultThree_returnsTrue() {
    assertTrue(KnownLocaleTags.isSupported("zh_CN"));
    assertTrue(KnownLocaleTags.isSupported("en_US"));
    assertTrue(KnownLocaleTags.isSupported("zh_TW"));
    assertFalse(KnownLocaleTags.isSupported("ja_JP"));
    assertFalse(KnownLocaleTags.isSupported(null));
  }

  @Test
  void toLocale_twoSegment_createsLocale() {
    Locale locale = KnownLocaleTags.toLocale("zh_CN");
    assertNotNull(locale);
    assertEquals("zh", locale.getLanguage());
    assertEquals("CN", locale.getCountry());
  }

  @Test
  void toLocale_oneSegment_createsLanguageOnlyLocale() {
    Locale locale = KnownLocaleTags.toLocale("en");
    assertNotNull(locale);
    assertEquals("en", locale.getLanguage());
    assertEquals("", locale.getCountry());
  }

  @Test
  void toLocale_nullInput_returnsNull() {
    assertNull(KnownLocaleTags.toLocale(null));
  }

  @Test
  void toLocale_emptyInput_returnsNull() {
    assertNull(KnownLocaleTags.toLocale(""));
  }

  @Test
  void toKnownLocale_known_returnsLocale() {
    assertEquals("zh", KnownLocaleTags.toKnownLocale("zh_CN").getLanguage());
  }

  @Test
  void toKnownLocale_unknown_returnsRoot() {
    assertEquals(Locale.ROOT, KnownLocaleTags.toKnownLocale("fr_FR"));
  }

  @Test
  void localeSuffixPattern_isCompiledAndValid() {
    assertNotNull(KnownLocaleTags.LOCALE_SUFFIX_PATTERN);
    // Spot-check: matches known tags
    assertTrue(KnownLocaleTags.LOCALE_SUFFIX_PATTERN.matcher("_zh_CN").find());
    assertTrue(KnownLocaleTags.LOCALE_SUFFIX_PATTERN.matcher("_en_US").find());
    assertTrue(KnownLocaleTags.LOCALE_SUFFIX_PATTERN.matcher("_ko_KR").find());
  }
}
