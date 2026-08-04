package com.njydsz.system.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 最小冒烟级单测，补充 P0 测试覆盖缺口。
 *
 * <p>测试 {@link ConfigValueType#validate(String)} 的值类型校验逻辑：
 * <ul>
 *   <li>合法枚举值（大小写不敏感）不抛异常</li>
 *   <li>非法值抛 IllegalArgumentException</li>
 *   <li>空 / null 值抛 IllegalArgumentException</li>
 * </ul>
 *
 * <p>纯计算、无外部依赖（DB/Redis），直接调用静态方法即可。
 */
class ConfigValueTypeSmokeTest {

    @Nested
    @DisplayName("validate - 值类型校验")
    class ValidateTests {

        @Test
        @DisplayName("大写枚举值合法")
        void uppercaseValues_areValid() {
            assertThatCode(() -> {
                ConfigValueType.validate("STRING");
                ConfigValueType.validate("NUMBER");
                ConfigValueType.validate("BOOLEAN");
                ConfigValueType.validate("JSON");
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("大小写混合也合法（case-insensitive）")
        void mixedCaseValues_areValid() {
            assertThatCode(() -> {
                ConfigValueType.validate("String");
                ConfigValueType.validate("number");
                ConfigValueType.validate("Json");
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("非法枚举值抛出 IllegalArgumentException")
        void invalidValue_throwsException() {
            assertThatThrownBy(() -> ConfigValueType.validate("XML"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("无效的值类型");
        }

        @Test
        @DisplayName("随机字符串抛出 IllegalArgumentException")
        void randomString_throwsException() {
            assertThatThrownBy(() -> ConfigValueType.validate("NOT_A_TYPE"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("空字符串抛出 IllegalArgumentException")
        void blankValue_throwsException() {
            assertThatThrownBy(() -> ConfigValueType.validate(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("值类型不能为空");
        }

        @Test
        @DisplayName("null 抛出 IllegalArgumentException")
        void nullValue_throwsException() {
            assertThatThrownBy(() -> ConfigValueType.validate(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("值类型不能为空");
        }
    }

    @Nested
    @DisplayName("enum completeness - 枚举值完整性")
    class EnumCompletenessTests {

        @Test
        @DisplayName("ConfigValueType 包含 4 个枚举值")
        void enumContainsExactlyFourValues() {
            assertThat(ConfigValueType.values()).hasSize(4);
        }

        @Test
        @DisplayName("valueOf 反向解析一致性")
        void valueOfRoundTrip_consistent() {
            for (ConfigValueType type : ConfigValueType.values()) {
                assertThat(ConfigValueType.valueOf(type.name())).isSameAs(type);
            }
        }
    }
}
