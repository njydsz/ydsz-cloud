package com.njydsz.agent.domain.teamrun;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TeamRunStatus} 单元测试。
 *
 * <p>覆盖：编码解析、终态判定、状态流转规则（含 WAITING_APPROVAL 分支）、isActive 判定、全量状态列表。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("TeamRunStatus 团队运行状态枚举")
class TeamRunStatusTest {

  @Nested
  @DisplayName("编码解析")
  class FromCodeTest {

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
    @CsvSource({
      "CREATED, CREATED",
      "RUNNING, RUNNING",
      "WAITING_APPROVAL, WAITING_APPROVAL",
      "COMPLETED, COMPLETED",
      "FAILED, FAILED",
      "CANCELLED, CANCELLED",
      "TIMEOUT, TIMEOUT",
    })
    @DisplayName("合法编码应解析为对应枚举")
    void shouldParseValidCode(String code, TeamRunStatus expected) {
      assertThat(TeamRunStatus.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("未知编码应返回 null")
    void shouldReturnNullForUnknownCode() {
      assertThat(TeamRunStatus.fromCode("NOT_EXIST")).isNull();
    }

    @Test
    @DisplayName("null 编码应返回 null")
    void shouldReturnNullForNullCode() {
      assertThat(TeamRunStatus.fromCode(null)).isNull();
    }
  }

  @Nested
  @DisplayName("终态判定")
  class TerminalTest {

    @ParameterizedTest(name = "isTerminal({0}) 应为 {1}")
    @CsvSource({
      "COMPLETED, true",
      "FAILED, true",
      "CANCELLED, true",
      "TIMEOUT, true",
      "CREATED, false",
      "RUNNING, false",
      "WAITING_APPROVAL, false",
    })
    @DisplayName("终态判定应正确")
    void shouldJudgeTerminal(TeamRunStatus status, boolean expected) {
      assertThat(status.isTerminal()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("活跃判定")
  class ActiveTest {

    @ParameterizedTest(name = "isActive({0}) 应为 {1}")
    @CsvSource({
      "RUNNING, true",
      "WAITING_APPROVAL, true",
      "CREATED, false",
      "COMPLETED, false",
      "FAILED, false",
      "CANCELLED, false",
      "TIMEOUT, false",
    })
    @DisplayName("RUNNING/WAITING_APPROVAL 为活跃")
    void shouldJudgeActive(TeamRunStatus status, boolean expected) {
      assertThat(status.isActive()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("状态流转")
  class TransitTest {

    @ParameterizedTest(name = "{0} → {1} 应允许")
    @CsvSource({
      "CREATED, RUNNING",
      "CREATED, CANCELLED",
      "CREATED, TIMEOUT",
      "RUNNING, WAITING_APPROVAL",
      "RUNNING, COMPLETED",
      "RUNNING, FAILED",
      "RUNNING, CANCELLED",
      "RUNNING, TIMEOUT",
      "WAITING_APPROVAL, RUNNING",
      "WAITING_APPROVAL, COMPLETED",
      "WAITING_APPROVAL, FAILED",
      "WAITING_APPROVAL, CANCELLED",
      "WAITING_APPROVAL, TIMEOUT",
      "CREATED, CREATED",
      "COMPLETED, COMPLETED",
    })
    @DisplayName("合法流转应被允许")
    void shouldAllowValidTransit(TeamRunStatus from, TeamRunStatus to) {
      assertThat(from.canTransitTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} 应拒绝")
    @CsvSource({
      "CREATED, COMPLETED",
      "CREATED, FAILED",
      "CREATED, WAITING_APPROVAL",
      "RUNNING, CREATED",
      "WAITING_APPROVAL, CREATED",
      "COMPLETED, RUNNING",
      "FAILED, RUNNING",
      "CANCELLED, CREATED",
      "TIMEOUT, RUNNING",
    })
    @DisplayName("非法流转应被拒绝")
    void shouldRejectInvalidTransit(TeamRunStatus from, TeamRunStatus to) {
      assertThat(from.canTransitTo(to)).isFalse();
    }

    @Test
    @DisplayName("目标为 null 应拒绝")
    void shouldRejectNullTarget() {
      assertThat(TeamRunStatus.CREATED.canTransitTo(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("全量状态")
  class AllStatesTest {

    @Test
    @DisplayName("allStates 应返回全部 7 个状态")
    void shouldReturnAllStates() {
      assertThat(TeamRunStatus.CREATED.allStates()).containsExactly(TeamRunStatus.values());
    }
  }
}
