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
 * {@link RuleStatus} 单元测试。
 *
 * <p>覆盖：i18n key、编码解析（大小写不敏感 / 空值 / 非法）、多级审批流状态流转规则。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("规则生命周期状态枚举")
class RuleStatusTest {

  @Nested
  @DisplayName("i18n key")
  class I18nKey {

    @Test
    @DisplayName("每个状态都有对应 i18n key")
    void hasI18nKey() {
      assertThat(RuleStatus.DRAFT.getI18nKey()).isEqualTo("rule.status.DRAFT");
      assertThat(RuleStatus.REVIEW.getI18nKey()).isEqualTo("rule.status.REVIEW");
      assertThat(RuleStatus.REVIEW_L1.getI18nKey()).isEqualTo("rule.status.REVIEW_L1");
      assertThat(RuleStatus.REVIEW_L2.getI18nKey()).isEqualTo("rule.status.REVIEW_L2");
      assertThat(RuleStatus.REVIEW_FINAL.getI18nKey()).isEqualTo("rule.status.REVIEW_FINAL");
      assertThat(RuleStatus.PUBLISHED.getI18nKey()).isEqualTo("rule.status.PUBLISHED");
      assertThat(RuleStatus.DISABLED.getI18nKey()).isEqualTo("rule.status.DISABLED");
      assertThat(RuleStatus.ARCHIVED.getI18nKey()).isEqualTo("rule.status.ARCHIVED");
    }
  }

  @Nested
  @DisplayName("fromCode 解析")
  class FromCode {

    @ParameterizedTest
    @CsvSource({
      "DRAFT,DRAFT",
      "draft,DRAFT",
      "PUBLISHED,PUBLISHED",
      "REVIEW_L1,REVIEW_L1",
      " review_final ,REVIEW_FINAL"
    })
    @DisplayName("大小写不敏感 + 去空白解析成功")
    void parsesCaseInsensitive(String input, RuleStatus expected) {
      assertThat(RuleStatus.fromCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown"})
    @DisplayName("空值或非法返回 null")
    void returnsNullForInvalid(String input) {
      assertThat(RuleStatus.fromCode(input)).isNull();
    }
  }

  @Nested
  @DisplayName("canTransitionTo 状态流转")
  class CanTransitionTo {

    @ParameterizedTest
    @CsvSource({
      // DRAFT
      "DRAFT,REVIEW,true",
      "DRAFT,REVIEW_L1,true",
      "DRAFT,PUBLISHED,true",
      "DRAFT,ARCHIVED,true",
      "DRAFT,REVIEW_L2,false",
      // REVIEW_L1
      "REVIEW_L1,REVIEW_L2,true",
      "REVIEW_L1,DRAFT,true",
      "REVIEW_L1,ARCHIVED,true",
      "REVIEW_L1,PUBLISHED,true",
      "REVIEW_L1,REVIEW_FINAL,false",
      // REVIEW_L2
      "REVIEW_L2,REVIEW_FINAL,true",
      "REVIEW_L2,REVIEW_L1,true",
      "REVIEW_L2,ARCHIVED,true",
      "REVIEW_L2,PUBLISHED,true",
      // REVIEW_FINAL
      "REVIEW_FINAL,PUBLISHED,true",
      "REVIEW_FINAL,REVIEW_L2,true",
      "REVIEW_FINAL,ARCHIVED,true",
      "REVIEW_FINAL,DRAFT,false",
      // REVIEW 向后兼容
      "REVIEW,PUBLISHED,true",
      "REVIEW,DRAFT,true",
      "REVIEW,ARCHIVED,true",
      "REVIEW,REVIEW_L2,true",
      // PUBLISHED / DISABLED
      "PUBLISHED,DISABLED,true",
      "PUBLISHED,ARCHIVED,true",
      "PUBLISHED,DRAFT,false",
      "DISABLED,PUBLISHED,true",
      "DISABLED,ARCHIVED,true",
      "DISABLED,DRAFT,false",
      // ARCHIVED 终态
      "ARCHIVED,PUBLISHED,false",
      "ARCHIVED,DRAFT,false"
    })
    @DisplayName("覆盖全部流转分支")
    void coversTransitions(String from, String to, boolean expected) {
      assertThat(RuleStatus.valueOf(from).canTransitionTo(RuleStatus.valueOf(to))).isEqualTo(expected);
    }
  }
}
