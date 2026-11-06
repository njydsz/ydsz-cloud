package com.njydsz.message.domain.enums.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TemplateStatusEnum} 单元测试。
 *
 * <p>覆盖：fromString 安全解析（大小写/空白/非法回退 DISABLED）、全量枚举值。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("模板状态枚举解析")
class TemplateStatusEnumTest {

  @Nested
  @DisplayName("fromString 合法值")
  class FromStringValid {

    @ParameterizedTest(name = "fromString(\"{0}\") 应返回 {1}")
    @CsvSource({
        "ENABLED, ENABLED",
        "DISABLED, DISABLED"
    })
    @DisplayName("全量枚举名均可解析")
    void should_parse_all_names(String input, TemplateStatusEnum expected) {
      assertThat(TemplateStatusEnum.fromString(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "fromString(\"{0}\") 大小写无关")
    @CsvSource({
        "enabled, ENABLED",
        "Disabled, DISABLED"
    })
    @DisplayName("大小写无关解析")
    void should_be_case_insensitive(String input, TemplateStatusEnum expected) {
      assertThat(TemplateStatusEnum.fromString(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("前后空白被裁剪")
    void should_trim_whitespace() {
      assertThat(TemplateStatusEnum.fromString("  ENABLED  ")).isEqualTo(TemplateStatusEnum.ENABLED);
    }
  }

  @Nested
  @DisplayName("fromString 非法值回退")
  class FromStringInvalid {

    @Test
    @DisplayName("null 回退为 DISABLED")
    void should_fallback_for_null() {
      assertThat(TemplateStatusEnum.fromString(null)).isEqualTo(TemplateStatusEnum.DISABLED);
    }

    @Test
    @DisplayName("空串回退为 DISABLED")
    void should_fallback_for_empty() {
      assertThat(TemplateStatusEnum.fromString("")).isEqualTo(TemplateStatusEnum.DISABLED);
    }

    @Test
    @DisplayName("纯空白回退为 DISABLED")
    void should_fallback_for_blank() {
      assertThat(TemplateStatusEnum.fromString("   ")).isEqualTo(TemplateStatusEnum.DISABLED);
    }

    @ParameterizedTest(name = "fromString(\"{0}\") 未知值回退 DISABLED")
    @ValueSource(strings = {"DRAFT", "ENABLEDX", "1", "enabled_disabled"})
    @DisplayName("未知值回退 DISABLED")
    void should_fallback_for_unknown(String input) {
      assertThat(TemplateStatusEnum.fromString(input)).isEqualTo(TemplateStatusEnum.DISABLED);
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 2 个状态")
    void should_have_two_statuses() {
      assertThat(TemplateStatusEnum.values()).hasSize(2);
    }
  }
}
