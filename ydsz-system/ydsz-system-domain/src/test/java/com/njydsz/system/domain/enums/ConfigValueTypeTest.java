package com.njydsz.system.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ConfigValueType 单元测试 — 验证类型校验与解析行为。
 *
 * <p>覆盖静态方法 {@link ConfigValueType#validate(String)} 的合法/非法分支。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@DisplayName("配置值类型枚举")
class ConfigValueTypeTest {

  @Nested
  @DisplayName("validate 方法")
  class ValidateTest {

    @ParameterizedTest
    @ValueSource(strings = {"STRING", "string", "String", "NUMBER", "number", "BOOLEAN", "boolean",
        "JSON", "json"})
    @DisplayName("合法类型码（大小写混合）应通过校验")
    void validCodes_shouldPass(String code) {
      assertThatNoException().isThrownBy(() -> ConfigValueType.validate(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", "INT", "FLOAT", "TEXT", "BOOL"})
    @DisplayName("非法类型码应抛出 IllegalArgumentException")
    void invalidCodes_shouldThrow(String code) {
      assertThatIllegalArgumentException().isThrownBy(() -> ConfigValueType.validate(code))
          .withMessageContaining(code);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    @DisplayName("空 / null 类型码应抛出 IllegalArgumentException")
    void nullEmptyCodes_shouldThrow(String code) {
      assertThatIllegalArgumentException().isThrownBy(() -> ConfigValueType.validate(code));
    }
  }

  @Nested
  @DisplayName("枚举值校验")
  class EnumValuesTest {

    @Test
    @DisplayName("枚举数量固定为 4")
    void shouldHaveExactlyFourValues() {
      assertThat(ConfigValueType.values()).hasSize(4);
    }

    @Test
    @DisplayName("valueOf 合法值应返回对应枚举")
    void valueOf_validName_shouldReturnEnumValue() {
      assertThat(ConfigValueType.valueOf("STRING")).isEqualTo(ConfigValueType.STRING);
      assertThat(ConfigValueType.valueOf("NUMBER")).isEqualTo(ConfigValueType.NUMBER);
      assertThat(ConfigValueType.valueOf("BOOLEAN")).isEqualTo(ConfigValueType.BOOLEAN);
      assertThat(ConfigValueType.valueOf("JSON")).isEqualTo(ConfigValueType.JSON);
    }
  }
}
