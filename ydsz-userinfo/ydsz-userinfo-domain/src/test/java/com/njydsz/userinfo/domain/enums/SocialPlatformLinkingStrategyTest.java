package com.njydsz.userinfo.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SocialPlatformLinkingStrategy} 单元测试。
 *
 * <p>覆盖：fromCode 解析（空值/非法回退 MANUAL_BIND）、code 与 displayName 映射。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("社交账号绑定策略枚举")
class SocialPlatformLinkingStrategyTest {

  @Nested
  @DisplayName("fromCode 解析")
  class FromCode {

    @ParameterizedTest
    @CsvSource({
        "auto_bind, AUTO_BIND",
        "manual_bind, MANUAL_BIND",
        "conflict_reject, CONFLICT_REJECT"
    })
    @DisplayName("合法 code 解析正确")
    void validCodes(String code, SocialPlatformLinkingStrategy expected) {
      assertThat(SocialPlatformLinkingStrategy.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("code 大小写敏感，大写不匹配时回退 MANUAL_BIND")
    void caseSensitive() {
      assertThat(SocialPlatformLinkingStrategy.fromCode("AUTO_BIND"))
          .isEqualTo(SocialPlatformLinkingStrategy.MANUAL_BIND);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "auto-bind", "auto"})
    @DisplayName("null/空/未知 code 回退 MANUAL_BIND")
    void fallbackToManualBind(String code) {
      assertThat(SocialPlatformLinkingStrategy.fromCode(code))
          .isEqualTo(SocialPlatformLinkingStrategy.MANUAL_BIND);
    }
  }

  @Nested
  @DisplayName("枚举属性")
  class Properties {

    @Test
    @DisplayName("每个枚举值的 code 与 displayName 非空")
    void allHaveCodeAndDisplayName() {
      for (SocialPlatformLinkingStrategy strategy : SocialPlatformLinkingStrategy.values()) {
        assertThat(strategy.getCode()).isNotBlank();
        assertThat(strategy.getDisplayName()).isNotBlank();
      }
    }
  }
}
