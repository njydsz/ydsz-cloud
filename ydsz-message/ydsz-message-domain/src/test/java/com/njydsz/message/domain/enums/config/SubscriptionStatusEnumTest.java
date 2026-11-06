package com.njydsz.message.domain.enums.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SubscriptionStatusEnum} 单元测试。
 *
 * <p>覆盖：双向可逆流转、无终态语义、枚举值总量。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("订阅状态枚举解析")
class SubscriptionStatusEnumTest {

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 2 个状态")
    void should_have_two_statuses() {
      assertThat(SubscriptionStatusEnum.values()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("终态判定")
  class Terminal {

    @Test
    @DisplayName("无终态，订阅状态可逆")
    void no_terminal_state() {
      assertThat(SubscriptionStatusEnum.SUBSCRIBED.isTerminal()).isFalse();
      assertThat(SubscriptionStatusEnum.UNSUBSCRIBED.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("状态流转")
  class Transition {

    @ParameterizedTest(name = "{0} → {1} 允许")
    @CsvSource({
        "SUBSCRIBED, UNSUBSCRIBED",
        "UNSUBSCRIBED, SUBSCRIBED"
    })
    @DisplayName("双向可逆流转")
    void should_allow_bidirectional_transition(
        SubscriptionStatusEnum from, SubscriptionStatusEnum to) {
      assertThat(from.canTransitTo(to)).isTrue();
    }

    @Test
    @DisplayName("自身到自身允许")
    void self_transition_allowed() {
      assertThat(SubscriptionStatusEnum.SUBSCRIBED.canTransitTo(SubscriptionStatusEnum.SUBSCRIBED))
          .isTrue();
      assertThat(SubscriptionStatusEnum.UNSUBSCRIBED.canTransitTo(SubscriptionStatusEnum.UNSUBSCRIBED))
          .isTrue();
    }
  }
}
