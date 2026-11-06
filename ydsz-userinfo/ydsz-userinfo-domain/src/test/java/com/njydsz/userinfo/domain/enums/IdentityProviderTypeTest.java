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
 * {@link IdentityProviderType} 单元测试。
 *
 * <p>覆盖：fromCode 解析（大小写敏感/空值/非法回退）、code 与 displayName 映射。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("身份提供者类型枚举")
class IdentityProviderTypeTest {

  @Nested
  @DisplayName("fromCode 解析")
  class FromCode {

    @ParameterizedTest
    @CsvSource({
        "local, LOCAL",
        "ldap, LDAP",
        "saml, SAML",
        "social, SOCIAL",
        "oauth2, OAUTH2"
    })
    @DisplayName("合法 code 解析正确")
    void validCodes(String code, IdentityProviderType expected) {
      assertThat(IdentityProviderType.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("code 大小写敏感，大写不匹配时回退 LOCAL")
    void caseSensitive() {
      assertThat(IdentityProviderType.fromCode("LOCAL")).isEqualTo(IdentityProviderType.LOCAL);
      assertThat(IdentityProviderType.fromCode("Local")).isEqualTo(IdentityProviderType.LOCAL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "oidc", "saml2"})
    @DisplayName("null/空/未知 code 回退 LOCAL")
    void fallbackToLocal(String code) {
      assertThat(IdentityProviderType.fromCode(code)).isEqualTo(IdentityProviderType.LOCAL);
    }
  }

  @Nested
  @DisplayName("枚举属性")
  class Properties {

    @Test
    @DisplayName("每个枚举值的 code 与 displayName 非空")
    void allHaveCodeAndDisplayName() {
      for (IdentityProviderType type : IdentityProviderType.values()) {
        assertThat(type.getCode()).isNotBlank();
        assertThat(type.getDisplayName()).isNotBlank();
      }
    }

    @Test
    @DisplayName("displayName 是 i18n key 格式")
    void displayNameIsI18nKey() {
      assertThat(IdentityProviderType.LOCAL.getDisplayName()).startsWith("userinfo.idp.");
    }
  }
}
