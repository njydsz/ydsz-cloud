package com.njydsz.agent.domain.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LessonType} 单元测试。
 *
 * <p>覆盖：编码解析（精确匹配）、未知/null 兜底、编码与描述字段。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("LessonType 经验类型枚举")
class LessonTypeTest {

  @Nested
  @DisplayName("编码解析")
  class FromCodeTest {

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
    @CsvSource({
      "BEST_PRACTICE, BEST_PRACTICE",
      "ERROR_LESSON, ERROR_LESSON",
      "USER_PREFERENCE, USER_PREFERENCE",
      "OPTIMIZATION, OPTIMIZATION",
      "CAUTION, CAUTION",
    })
    @DisplayName("合法编码应解析为对应枚举")
    void shouldParseValidCode(String code, LessonType expected) {
      assertThat(LessonType.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 编码应返回 null")
    void shouldReturnNullForNullCode() {
      assertThat(LessonType.fromCode(null)).isNull();
    }

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 null")
    @ValueSource(strings = {"best_practice", "UNKNOWN", "CAUTION "})
    @DisplayName("大小写不符/未知应返回 null（精确匹配）")
    void shouldReturnNullForUnknown(String code) {
      assertThat(LessonType.fromCode(code)).isNull();
    }
  }

  @Nested
  @DisplayName("元数据")
  class MetaTest {

    @Test
    @DisplayName("编码应与枚举名一致")
    void shouldCodeMatchName() {
      for (LessonType type : LessonType.values()) {
        assertThat(type.getCode()).isEqualTo(type.name());
      }
    }

    @Test
    @DisplayName("描述应为非空中文")
    void shouldHaveDescription() {
      for (LessonType type : LessonType.values()) {
        assertThat(type.getDescription()).isNotBlank();
      }
    }
  }
}
