package com.njydsz.common.locales.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RuntimeStrictness} 枚举单测
 *
 * <p>覆盖：三档枚举值的 negativeCacheEnabled / missingTranslationLogEnabled 属性。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class RuntimeStrictnessTest {

  @Test
  void strict_enablesBoth() {
    RuntimeStrictness strict = RuntimeStrictness.STRICT;
    assertTrue(strict.isNegativeCacheEnabled());
    assertTrue(strict.isMissingTranslationLogEnabled());
  }

  @Test
  void relaxed_cacheEnabled_logDisabled() {
    RuntimeStrictness relaxed = RuntimeStrictness.RELAXED;
    assertTrue(relaxed.isNegativeCacheEnabled());
    assertFalse(relaxed.isMissingTranslationLogEnabled());
  }

  @Test
  void off_disablesBoth() {
    RuntimeStrictness off = RuntimeStrictness.OFF;
    assertFalse(off.isNegativeCacheEnabled());
    assertFalse(off.isMissingTranslationLogEnabled());
  }

  @Test
  void values_containsThreeEntries() {
    assertEquals(3, RuntimeStrictness.values().length);
  }
}
