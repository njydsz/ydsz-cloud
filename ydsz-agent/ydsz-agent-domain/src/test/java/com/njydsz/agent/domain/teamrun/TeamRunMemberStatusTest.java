package com.njydsz.agent.domain.teamrun;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TeamRunMemberStatus} 单元测试。
 *
 * <p>覆盖：编码解析、终态判定（含 SKIPPED）、状态流转规则、全量状态列表。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("TeamRunMemberStatus 团队成员状态枚举")
class TeamRunMemberStatusTest {

  @Nested
  @DisplayName("编码解析")
  class FromCodeTest {

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
    @CsvSource({
      "PENDING, PENDING",
      "RUNNING, RUNNING",
      "COMPLETED, COMPLETED",
      "FAILED, FAILED",
      "SKIPPED, SKIPPED",
      "CANCELLED, CANCELLED",
    })
    @DisplayName("合法编码应解析为对应枚举")
    void shouldParseValidCode(String code, TeamRunMemberStatus expected) {
      assertThat(TeamRunMemberStatus.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("未知编码应返回 null")
    void shouldReturnNullForUnknownCode() {
      assertThat(TeamRunMemberStatus.fromCode("NOT_EXIST")).isNull();
    }

    @Test
    @DisplayName("null 编码应返回 null")
    void shouldReturnNullForNullCode() {
      assertThat(TeamRunMemberStatus.fromCode(null)).isNull();
    }
  }

  @Nested
  @DisplayName("终态判定")
  class TerminalTest {

    @ParameterizedTest(name = "isTerminal({0}) 应为 {1}")
    @CsvSource({
      "COMPLETED, true",
      "FAILED, true",
      "SKIPPED, true",
      "CANCELLED, true",
      "PENDING, false",
      "RUNNING, false",
    })
    @DisplayName("终态判定应正确")
    void shouldJudgeTerminal(TeamRunMemberStatus status, boolean expected) {
      assertThat(status.isTerminal()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("状态流转")
  class TransitTest {

    @ParameterizedTest(name = "{0} → {1} 应允许")
    @CsvSource({
      "PENDING, RUNNING",
      "PENDING, SKIPPED",
      "PENDING, CANCELLED",
      "RUNNING, COMPLETED",
      "RUNNING, FAILED",
      "RUNNING, CANCELLED",
      "PENDING, PENDING",
      "RUNNING, RUNNING",
    })
    @DisplayName("合法流转应被允许")
    void shouldAllowValidTransit(TeamRunMemberStatus from, TeamRunMemberStatus to) {
      assertThat(from.canTransitTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} 应拒绝")
    @CsvSource({
      "PENDING, COMPLETED",
      "PENDING, FAILED",
      "RUNNING, PENDING",
      "RUNNING, SKIPPED",
      "COMPLETED, RUNNING",
      "FAILED, RUNNING",
      "SKIPPED, RUNNING",
      "CANCELLED, PENDING",
    })
    @DisplayName("非法流转应被拒绝")
    void shouldRejectInvalidTransit(TeamRunMemberStatus from, TeamRunMemberStatus to) {
      assertThat(from.canTransitTo(to)).isFalse();
    }

    @Test
    @DisplayName("目标为 null 应拒绝")
    void shouldRejectNullTarget() {
      assertThat(TeamRunMemberStatus.PENDING.canTransitTo(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("全量状态")
  class AllStatesTest {

    @Test
    @DisplayName("allStates 应返回全部 6 个状态")
    void shouldReturnAllStates() {
      assertThat(TeamRunMemberStatus.PENDING.allStates())
          .containsExactly(TeamRunMemberStatus.values());
    }
  }
}
