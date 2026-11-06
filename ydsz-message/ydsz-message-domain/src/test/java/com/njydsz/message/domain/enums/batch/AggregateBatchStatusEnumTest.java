package com.njydsz.message.domain.enums.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AggregateBatchStatusEnum} 单元测试。
 *
 * <p>覆盖：终态判定、状态流转规则（PENDING/READY/SENDING 中间态与 SENT/CANCELLED 终态）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("聚合批次状态枚举解析")
class AggregateBatchStatusEnumTest {

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 5 个状态")
    void should_have_five_statuses() {
      assertThat(AggregateBatchStatusEnum.values()).hasSize(5);
    }
  }

  @Nested
  @DisplayName("终态判定")
  class Terminal {

    @ParameterizedTest(name = "{0} 为终态")
    @CsvSource({"SENT", "CANCELLED"})
    @DisplayName("SENT/CANCELLED 为终态")
    void should_be_terminal(String name) {
      assertThat(AggregateBatchStatusEnum.valueOf(name).isTerminal()).isTrue();
    }

    @ParameterizedTest(name = "{0} 非终态")
    @CsvSource({"PENDING", "READY", "SENDING"})
    @DisplayName("PENDING/READY/SENDING 非终态")
    void should_not_be_terminal(String name) {
      assertThat(AggregateBatchStatusEnum.valueOf(name).isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("状态流转")
  class Transition {

    @ParameterizedTest(name = "{0} → {1} 允许")
    @CsvSource({
        "PENDING, READY",
        "PENDING, CANCELLED",
        "READY, SENDING",
        "READY, CANCELLED",
        "SENDING, SENT",
        "SENDING, READY",
        "SENDING, CANCELLED"
    })
    @DisplayName("合法流转")
    void should_allow_transition(AggregateBatchStatusEnum from, AggregateBatchStatusEnum to) {
      assertThat(from.canTransitTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} 禁止")
    @CsvSource({
        "PENDING, SENDING",
        "PENDING, SENT",
        "READY, PENDING",
        "READY, SENT",
        "SENDING, PENDING",
        "SENT, PENDING",
        "SENT, READY",
        "SENT, SENDING",
        "CANCELLED, PENDING",
        "CANCELLED, READY"
    })
    @DisplayName("非法流转")
    void should_reject_transition(AggregateBatchStatusEnum from, AggregateBatchStatusEnum to) {
      assertThat(from.canTransitTo(to)).isFalse();
    }

    @Test
    @DisplayName("自身到自身允许")
    void self_transition_allowed() {
      for (AggregateBatchStatusEnum status : AggregateBatchStatusEnum.values()) {
        assertThat(status.canTransitTo(status)).isTrue();
      }
    }
  }
}
