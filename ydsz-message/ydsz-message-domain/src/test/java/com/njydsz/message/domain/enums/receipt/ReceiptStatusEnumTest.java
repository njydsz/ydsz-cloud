package com.njydsz.message.domain.enums.receipt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ReceiptStatusEnum} 单元测试。
 *
 * <p>覆盖：终态判定、状态流转规则（NONE/DELIVERED/READ 中间态与 CLICKED/FAILED/TIMEOUT 终态）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("回执状态枚举解析")
class ReceiptStatusEnumTest {

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 6 个状态")
    void should_have_six_statuses() {
      assertThat(ReceiptStatusEnum.values()).hasSize(6);
    }

    @ParameterizedTest(name = "包含 {0}")
    @CsvSource({"NONE", "DELIVERED", "READ", "CLICKED", "FAILED", "TIMEOUT"})
    @DisplayName("全量枚举值存在")
    void should_contain_all_statuses(String name) {
      assertThat(ReceiptStatusEnum.valueOf(name)).isNotNull();
    }
  }

  @Nested
  @DisplayName("终态判定")
  class Terminal {

    @ParameterizedTest(name = "{0} 为终态")
    @CsvSource({"CLICKED", "FAILED", "TIMEOUT"})
    @DisplayName("CLICKED/FAILED/TIMEOUT 为终态")
    void should_be_terminal(String name) {
      assertThat(ReceiptStatusEnum.valueOf(name).isTerminal()).isTrue();
    }

    @ParameterizedTest(name = "{0} 非终态")
    @CsvSource({"NONE", "DELIVERED", "READ"})
    @DisplayName("NONE/DELIVERED/READ 非终态")
    void should_not_be_terminal(String name) {
      assertThat(ReceiptStatusEnum.valueOf(name).isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("状态流转")
  class Transition {

    @ParameterizedTest(name = "{0} → {1} 允许")
    @CsvSource({
        "NONE, DELIVERED",
        "NONE, FAILED",
        "NONE, TIMEOUT",
        "DELIVERED, READ",
        "DELIVERED, TIMEOUT",
        "DELIVERED, FAILED",
        "READ, CLICKED"
    })
    @DisplayName("合法流转")
    void should_allow_transition(ReceiptStatusEnum from, ReceiptStatusEnum to) {
      assertThat(from.canTransitTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} 禁止")
    @CsvSource({
        "NONE, READ",
        "NONE, CLICKED",
        "DELIVERED, CLICKED",
        "READ, DELIVERED",
        "READ, FAILED",
        "READ, TIMEOUT",
        "CLICKED, NONE",
        "FAILED, NONE",
        "TIMEOUT, NONE"
    })
    @DisplayName("非法流转")
    void should_reject_transition(ReceiptStatusEnum from, ReceiptStatusEnum to) {
      assertThat(from.canTransitTo(to)).isFalse();
    }

    @Test
    @DisplayName("自身到自身允许")
    void self_transition_allowed() {
      for (ReceiptStatusEnum status : ReceiptStatusEnum.values()) {
        assertThat(status.canTransitTo(status)).isTrue();
      }
    }

    @Test
    @DisplayName("终态 requireTransitTo 抛出异常")
    void require_transit_throws() {
      assertThatThrownBy(() -> ReceiptStatusEnum.CLICKED.requireTransitTo(ReceiptStatusEnum.NONE))
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
