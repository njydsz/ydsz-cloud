package com.njydsz.message.domain.enums.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MessageStatusEnum} 单元测试。
 *
 * <p>覆盖：终态判定、状态流转规则（PENDING/SCHEDULED/SENDING/RETRY 中间态与 FAILED/DEAD/RECALLED/SKIPPED 终态）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("消息发送状态枚举解析")
class MessageStatusEnumTest {

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 9 个状态")
    void should_have_nine_statuses() {
      assertThat(MessageStatusEnum.values()).hasSize(9);
    }
  }

  @Nested
  @DisplayName("终态判定")
  class Terminal {

    @ParameterizedTest(name = "{0} 为终态")
    @CsvSource({"FAILED", "DEAD", "RECALLED", "SKIPPED"})
    @DisplayName("FAILED/DEAD/RECALLED/SKIPPED 为终态")
    void should_be_terminal(String name) {
      assertThat(MessageStatusEnum.valueOf(name).isTerminal()).isTrue();
    }

    @ParameterizedTest(name = "{0} 非终态")
    @CsvSource({"PENDING", "SENDING", "SUCCESS", "RETRY", "SCHEDULED"})
    @DisplayName("中间态非终态")
    void should_not_be_terminal(String name) {
      assertThat(MessageStatusEnum.valueOf(name).isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("状态流转")
  class Transition {

    @ParameterizedTest(name = "{0} → {1} 允许")
    @CsvSource({
        "PENDING, SENDING",
        "PENDING, FAILED",
        "PENDING, RECALLED",
        "PENDING, SCHEDULED",
        "PENDING, SKIPPED",
        "SCHEDULED, SENDING",
        "SCHEDULED, FAILED",
        "SCHEDULED, RECALLED",
        "SCHEDULED, SKIPPED",
        "SENDING, SUCCESS",
        "SENDING, FAILED",
        "SENDING, RETRY",
        "SENDING, RECALLED",
        "SENDING, SKIPPED",
        "RETRY, SENDING",
        "RETRY, SUCCESS",
        "RETRY, FAILED",
        "RETRY, DEAD",
        "SUCCESS, RECALLED"
    })
    @DisplayName("合法流转")
    void should_allow_transition(MessageStatusEnum from, MessageStatusEnum to) {
      assertThat(from.canTransitTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} 禁止")
    @CsvSource({
        "PENDING, SUCCESS",
        "PENDING, DEAD",
        "SENDING, PENDING",
        "SENDING, SCHEDULED",
        "RETRY, PENDING",
        "RETRY, RECALLED",
        "RETRY, SKIPPED",
        "SUCCESS, FAILED",
        "SUCCESS, SENDING",
        "FAILED, SENDING",
        "DEAD, SENDING",
        "RECALLED, SENDING",
        "SKIPPED, SENDING"
    })
    @DisplayName("非法流转")
    void should_reject_transition(MessageStatusEnum from, MessageStatusEnum to) {
      assertThat(from.canTransitTo(to)).isFalse();
    }

    @Test
    @DisplayName("自身到自身允许")
    void self_transition_allowed() {
      for (MessageStatusEnum status : MessageStatusEnum.values()) {
        assertThat(status.canTransitTo(status)).isTrue();
      }
    }
  }
}
