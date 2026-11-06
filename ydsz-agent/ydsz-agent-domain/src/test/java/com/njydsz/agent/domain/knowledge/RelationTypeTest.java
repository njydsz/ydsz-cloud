package com.njydsz.agent.domain.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RelationType} 单元测试。
 *
 * <p>覆盖：大小写不敏感解析（含 trim）、未知值降级为 RELATED_TO、null/空白兜底。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("RelationType 知识关系类型枚举")
class RelationTypeTest {

  @ParameterizedTest(name = "fromString(\"{0}\") 应返回 {1}")
  @CsvSource({
    "WORKS_AT, WORKS_AT",
    "LOCATED_IN, LOCATED_IN",
    "PART_OF, PART_OF",
    "RELATED_TO, RELATED_TO",
    "CREATED_BY, CREATED_BY",
    "DEPENDS_ON, DEPENDS_ON",
    "BELONGS_TO, BELONGS_TO",
    "works_at, WORKS_AT",
    "Related_To, RELATED_TO",
  })
  @DisplayName("大小写不敏感解析应成功")
  void shouldParseCaseInsensitive(String value, RelationType expected) {
    assertThat(RelationType.fromString(value)).isEqualTo(expected);
  }

  @Test
  @DisplayName("含首尾空白应 trim 后解析")
  void shouldTrimBeforeParse() {
    assertThat(RelationType.fromString("  works_at  ")).isEqualTo(RelationType.WORKS_AT);
  }

  @Test
  @DisplayName("null 应降级为 RELATED_TO")
  void shouldFallbackForNull() {
    assertThat(RelationType.fromString(null)).isEqualTo(RelationType.RELATED_TO);
  }

  @Test
  @DisplayName("空白字符串应降级为 RELATED_TO")
  void shouldFallbackForBlank() {
    assertThat(RelationType.fromString("   ")).isEqualTo(RelationType.RELATED_TO);
  }

  @ParameterizedTest(name = "fromString(\"{0}\") 应降级为 RELATED_TO")
  @ValueSource(strings = {"IS_A", "CONTAINS", "CAUSES", "SIMILAR_TO", "UNKNOWN"})
  @DisplayName("未知值应降级为 RELATED_TO")
  void shouldFallbackForUnknown(String value) {
    assertThat(RelationType.fromString(value)).isEqualTo(RelationType.RELATED_TO);
  }
}
