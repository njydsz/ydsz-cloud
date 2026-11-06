package com.njydsz.message.domain.enums.receipt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RecallStatusEnum} 单元测试。
 *
 * <p>覆盖：终态判定、状态流转规则、枚举值总量。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("撤回状态枚举解析")
class RecallStatusEnumTest {

  @Nested
  @DisplayName("枚举值")
  class Values {

    @ParameterizedTest(name = "包含 {0}")
    @CsvSource({"NONE", "RECALLED"})
    @DisplayName("全量枚举值存在")
    void should_contain_all_statuses(String name) {
      assertThat(RecallStatusEnum.valueOf(name)).isEqualTo(RecallStatusEnum.valueOf(name));
    }

    @Test
    @DisplayName("共 2 个状态")
    void should_have_two_statuses() {
      assertThat(RecallStatusEnum.values()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("终态判定")
  class Terminal {

    @Test
    @DisplayName("RECALLED 为终态")
    void recalled_is_terminal() {
      assertThat(RecallStatusEnum.RECALLED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("NONE 非终态")
    void none_is_not_terminal() {
      assertThat(RecallStatusEnum.NONE.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("状态流转")
  class Transition {

    @Test
    @DisplayName("NONE 可流转到 RECALLED")
    void none_to_recalled() {
      assertThat(RecallStatusEnum.NONE.canTransitTo(RecallStatusEnum.RECALLED)).isTrue();
    }

    @Test
    @DisplayName("RECALLED 为终态不可再流转")
    void recalled_is_terminal_transition() {
      assertThat(RecallStatusEnum.RECALLED.canTransitTo(RecallStatusEnum.NONE)).isFalse();
    }

    @Test
    @DisplayName("自身到自身允许")
    void self_transition_allowed() {
      assertThat(RecallStatusEnum.NONE.canTransitTo(RecallStatusEnum.NONE)).isTrue();
      assertThat(RecallStatusEnum.RECALLED.canTransitTo(RecallStatusEnum.RECALLED)).isTrue();
    }
  }

  @Nested
  @DisplayName("非法流转校验")
  class RequireTransit {

    @Test
    @DisplayName("终态 requireTransitTo 抛出异常")
    void require_transit_throws() {
      assertThatThrownBy(() -> RecallStatusEnum.RECALLED.requireTransitTo(RecallStatusEnum.NONE))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("合法流转 requireTransitTo 不抛异常")
    void require_transit_passes() {
      RecallStatusEnum.NONE.requireTransitTo(RecallStatusEnum.RECALLED);
    }
  }
}
