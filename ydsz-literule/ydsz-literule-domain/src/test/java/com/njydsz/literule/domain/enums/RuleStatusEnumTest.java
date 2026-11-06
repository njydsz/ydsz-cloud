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
 * {@link RuleStatusEnum} 单元测试。
 *
 * <p>覆盖：parse 解析（大小写不敏感 / 空值 / 非法）、终态判定、状态流转规则。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("规则发布状态枚举")
class RuleStatusEnumTest {

  @Nested
  @DisplayName("parse 解析")
  class Parse {

    @ParameterizedTest
    @CsvSource({"DRAFT,DRAFT", "draft,DRAFT", "PUBLISHED,PUBLISHED", "disabled,DISABLED"})
    @DisplayName("大小写不敏感解析成功")
    void parsesCaseInsensitive(String input, RuleStatusEnum expected) {
      assertThat(RuleStatusEnum.parse(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown"})
    @DisplayName("空值或非法返回 null")
    void returnsNullForInvalid(String input) {
      assertThat(RuleStatusEnum.parse(input)).isNull();
    }
  }

  @Nested
  @DisplayName("isTerminal 终态判定")
  class Terminal {

    @ParameterizedTest
    @CsvSource({"DRAFT,false", "PUBLISHED,false", "DISABLED,false"})
    @DisplayName("本枚举无严格终态，恒为 false")
    void neverTerminal(String name, boolean expected) {
      assertThat(RuleStatusEnum.valueOf(name).isTerminal()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("canTransitTo 状态流转")
  class CanTransitTo {

    @ParameterizedTest
    @CsvSource({
      "DRAFT,DRAFT,true",
      "DRAFT,PUBLISHED,true",
      "DRAFT,DISABLED,true",
      "PUBLISHED,DISABLED,true",
      "PUBLISHED,DRAFT,true",
      "DISABLED,DRAFT,true",
      "DISABLED,PUBLISHED,true"
    })
    @DisplayName("覆盖全部流转分支")
    void coversTransitions(String from, String to, boolean expected) {
      assertThat(RuleStatusEnum.valueOf(from).canTransitTo(RuleStatusEnum.valueOf(to))).isEqualTo(expected);
    }
  }
}
