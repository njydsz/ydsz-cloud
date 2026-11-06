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
 * {@link HitPolicy} 单元测试。
 *
 * <p>覆盖：编码解析（大小写不敏感 / 空白 / 非法回退默认）、枚举完整性。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("决策表命中策略")
class HitPolicyTest {

  @Nested
  @DisplayName("fromCode 解析")
  class FromCode {

    @ParameterizedTest
    @CsvSource({
      "UNIQUE,UNIQUE",
      "unique,UNIQUE",
      "Unique,UNIQUE",
      "FIRST,FIRST",
      "PRIORITY,PRIORITY",
      "COLLECT,COLLECT",
      "ANY,ANY",
      "RULE_ORDER,RULE_ORDER",
      " rule_order ,RULE_ORDER"
    })
    @DisplayName("大小写不敏感 + 去空白解析成功")
    void parsesCaseInsensitive(String input, HitPolicy expected) {
      assertThat(HitPolicy.fromCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown", "ALL"})
    @DisplayName("空值或非法回退默认 FIRST")
    void fallsBackToFirst(String input) {
      assertThat(HitPolicy.fromCode(input)).isEqualTo(HitPolicy.FIRST);
    }
  }

  @Nested
  @DisplayName("枚举完整性")
  class Values {

    @Test
    @DisplayName("包含全部 6 种 DMN 命中策略")
    void containsAllPolicies() {
      assertThat(HitPolicy.values())
          .containsExactlyInAnyOrder(
              HitPolicy.UNIQUE,
              HitPolicy.FIRST,
              HitPolicy.PRIORITY,
              HitPolicy.COLLECT,
              HitPolicy.ANY,
              HitPolicy.RULE_ORDER);
    }

    @Test
    @DisplayName("默认策略为 FIRST")
    void defaultIsFirst() {
      assertThat(HitPolicy.valueOf("FIRST")).isEqualTo(HitPolicy.fromCode(null));
    }
  }
}
