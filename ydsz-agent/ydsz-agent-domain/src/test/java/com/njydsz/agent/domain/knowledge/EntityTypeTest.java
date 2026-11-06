package com.njydsz.agent.domain.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EntityType} 单元测试。
 *
 * <p>覆盖：大小写不敏感解析（含 trim）、未知值降级为 UNKNOWN、isKnown 判定。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("EntityType 知识实体类型枚举")
class EntityTypeTest {

  @Nested
  @DisplayName("解析")
  class ParseTest {

    @ParameterizedTest(name = "fromString(\"{0}\") 应返回 {1}")
    @CsvSource({
      "PERSON, PERSON",
      "LOCATION, LOCATION",
      "ORGANIZATION, ORGANIZATION",
      "CONCEPT, CONCEPT",
      "EVENT, EVENT",
      "PRODUCT, PRODUCT",
      "TECHNOLOGY, TECHNOLOGY",
      "UNKNOWN, UNKNOWN",
      "person, PERSON",
      "Location, LOCATION",
    })
    @DisplayName("大小写不敏感解析应成功")
    void shouldParseCaseInsensitive(String value, EntityType expected) {
      assertThat(EntityType.fromString(value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("含首尾空白应 trim 后解析")
    void shouldTrimBeforeParse() {
      assertThat(EntityType.fromString("  person  ")).isEqualTo(EntityType.PERSON);
    }

    @Test
    @DisplayName("null 应降级为 UNKNOWN")
    void shouldFallbackToUnknownForNull() {
      assertThat(EntityType.fromString(null)).isEqualTo(EntityType.UNKNOWN);
    }

    @Test
    @DisplayName("空白字符串应降级为 UNKNOWN")
    void shouldFallbackToUnknownForBlank() {
      assertThat(EntityType.fromString("   ")).isEqualTo(EntityType.UNKNOWN);
    }

    @ParameterizedTest(name = "fromString(\"{0}\") 应降级为 UNKNOWN")
    @ValueSource(strings = {"CUSTOM", "TOPIC", "KEYWORD", "THING"})
    @DisplayName("未知值应降级为 UNKNOWN")
    void shouldFallbackToUnknownForUnknown(String value) {
      assertThat(EntityType.fromString(value)).isEqualTo(EntityType.UNKNOWN);
    }
  }

  @Nested
  @DisplayName("已知类型判定")
  class KnownTest {

    @ParameterizedTest(name = "isKnown({0}) 应为 {1}")
    @CsvSource({
      "PERSON, true",
      "LOCATION, true",
      "ORGANIZATION, true",
      "CONCEPT, true",
      "EVENT, true",
      "PRODUCT, true",
      "TECHNOLOGY, true",
      "UNKNOWN, false",
    })
    @DisplayName("内置类型应判定为已知，UNKNOWN 为未知")
    void shouldJudgeKnown(EntityType type, boolean expected) {
      assertThat(type.isKnown()).isEqualTo(expected);
    }

    @Test
    @DisplayName("未知字符串解析结果应判定为未知")
    void shouldJudgeUnknownFromString() {
      assertThat(EntityType.fromString("SOMETHING_NEW").isKnown()).isFalse();
    }
  }
}
