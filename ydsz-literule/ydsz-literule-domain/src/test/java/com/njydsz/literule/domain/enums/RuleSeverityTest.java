package com.njydsz.literule.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RuleSeverity} 单元测试。
 *
 * <p>覆盖：编码/权重/i18n key 一致性、编码解析（大小写不敏感 / 空值 / 非法）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("规则严重度枚举")
class RuleSeverityTest {

  @Nested
  @DisplayName("属性一致性")
  class Attributes {

    @Test
    @DisplayName("三层级的 code / weight / i18nKey 均正确")
    void attributesAreConsistent() {
      assertThat(RuleSeverity.INFO.getCode()).isEqualTo("INFO");
      assertThat(RuleSeverity.INFO.getWeight()).isEqualTo(1);
      assertThat(RuleSeverity.INFO.getI18nKey()).isEqualTo("rule.severity.INFO");

      assertThat(RuleSeverity.YELLOW.getCode()).isEqualTo("YELLOW");
      assertThat(RuleSeverity.YELLOW.getWeight()).isEqualTo(2);
      assertThat(RuleSeverity.YELLOW.getI18nKey()).isEqualTo("rule.severity.YELLOW");

      assertThat(RuleSeverity.RED.getCode()).isEqualTo("RED");
      assertThat(RuleSeverity.RED.getWeight()).isEqualTo(3);
      assertThat(RuleSeverity.RED.getI18nKey()).isEqualTo("rule.severity.RED");
    }

    @Test
    @DisplayName("权重随严重度递增")
    void weightIncreases() {
      assertThat(RuleSeverity.INFO.getWeight())
          .isLessThan(RuleSeverity.YELLOW.getWeight());
      assertThat(RuleSeverity.YELLOW.getWeight())
          .isLessThan(RuleSeverity.RED.getWeight());
    }
  }

  @Nested
  @DisplayName("fromCode 解析")
  class FromCode {

    @ParameterizedTest
    @CsvSource({"INFO,INFO", "info,INFO", "Info,INFO", "YELLOW,YELLOW", "red,RED"})
    @DisplayName("大小写不敏感解析成功")
    void parsesCaseInsensitive(String input, RuleSeverity expected) {
      assertThat(RuleSeverity.fromCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "orange"})
    @DisplayName("空值或非法返回 null")
    void returnsNullForInvalid(String input) {
      assertThat(RuleSeverity.fromCode(input)).isNull();
    }
  }
}
