package com.njydsz.agent.domain.teamrun;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TeamRunPattern} 单元测试。
 *
 * <p>覆盖：编码解析（精确匹配）、未知/null 兜底、编码与描述字段。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("TeamRunPattern 团队协作模式枚举")
class TeamRunPatternTest {

  @Nested
  @DisplayName("编码解析")
  class FromCodeTest {

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
    @CsvSource({
      "SEQUENTIAL, SEQUENTIAL",
      "PARALLEL, PARALLEL",
      "HIERARCHICAL, HIERARCHICAL",
      "NEGOTIATION, NEGOTIATION",
    })
    @DisplayName("合法编码应解析为对应枚举")
    void shouldParseValidCode(String code, TeamRunPattern expected) {
      assertThat(TeamRunPattern.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 编码应返回 null")
    void shouldReturnNullForNullCode() {
      assertThat(TeamRunPattern.fromCode(null)).isNull();
    }

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 null")
    @ValueSource(strings = {"sequential", "Sequential", "UNKNOWN"})
    @DisplayName("大小写不符/未知应返回 null（精确匹配）")
    void shouldReturnNullForUnknown(String code) {
      assertThat(TeamRunPattern.fromCode(code)).isNull();
    }
  }

  @Nested
  @DisplayName("元数据")
  class MetaTest {

    @Test
    @DisplayName("编码应与枚举名一致")
    void shouldCodeMatchName() {
      for (TeamRunPattern pattern : TeamRunPattern.values()) {
        assertThat(pattern.getCode()).isEqualTo(pattern.name());
      }
    }

    @Test
    @DisplayName("描述应为非空 i18n key")
    void shouldHaveDescription() {
      for (TeamRunPattern pattern : TeamRunPattern.values()) {
        assertThat(pattern.getDescription()).isNotBlank();
      }
    }
  }
}
