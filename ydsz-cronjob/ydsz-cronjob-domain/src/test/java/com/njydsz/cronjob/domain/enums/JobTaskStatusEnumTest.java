package com.njydsz.cronjob.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JobTaskStatusEnum} 单元测试。
 *
 * <p>覆盖：解析（大小写/空值/非法）、终态判定、状态流转规则（PENDING→RUNNING→SUCCESS/FAILED）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("子任务执行状态枚举")
class JobTaskStatusEnumTest {

  @Nested
  @DisplayName("parse 解析")
  class Parse {

    @ParameterizedTest
    @CsvSource({
      "PENDING, PENDING",
      "pending, PENDING",
      "Running, RUNNING",
      "SUCCESS, SUCCESS",
      "FAILED, FAILED"
    })
    @DisplayName("大小写不敏感解析")
    void caseInsensitive(String input, JobTaskStatusEnum expected) {
      assertThat(JobTaskStatusEnum.parse(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 返回 null")
    void nullReturnsNull() {
      assertThat(JobTaskStatusEnum.parse(null)).isNull();
    }

    @Test
    @DisplayName("空白字符串返回 null")
    void blankReturnsNull() {
      assertThat(JobTaskStatusEnum.parse("  ")).isNull();
    }

    @Test
    @DisplayName("非法值返回 null（不抛异常）")
    void illegalReturnsNull() {
      assertThat(JobTaskStatusEnum.parse("NOT_A_STATUS")).isNull();
    }
  }

  @Nested
  @DisplayName("isTerminal 终态判定")
  class Terminal {

    @Test
    @DisplayName("终态为 SUCCESS/FAILED")
    void terminalStates() {
      assertThat(JobTaskStatusEnum.SUCCESS.isTerminal()).isTrue();
      assertThat(JobTaskStatusEnum.FAILED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("非终态为 PENDING/RUNNING")
    void nonTerminalStates() {
      assertThat(JobTaskStatusEnum.PENDING.isTerminal()).isFalse();
      assertThat(JobTaskStatusEnum.RUNNING.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("canTransitTo 状态流转")
  class Transition {

    @Test
    @DisplayName("相同状态允许（幂等）")
    void sameStateAllowed() {
      for (JobTaskStatusEnum s : JobTaskStatusEnum.values()) {
        assertThat(s.canTransitTo(s)).isTrue();
      }
    }

    @Test
    @DisplayName("PENDING → RUNNING 允许")
    void pendingToRunning() {
      assertThat(JobTaskStatusEnum.PENDING.canTransitTo(JobTaskStatusEnum.RUNNING)).isTrue();
    }

    @Test
    @DisplayName("PENDING → SUCCESS/FAILED 拒绝")
    void pendingToTerminalRejected() {
      assertThat(JobTaskStatusEnum.PENDING.canTransitTo(JobTaskStatusEnum.SUCCESS)).isFalse();
      assertThat(JobTaskStatusEnum.PENDING.canTransitTo(JobTaskStatusEnum.FAILED)).isFalse();
    }

    @Test
    @DisplayName("RUNNING → SUCCESS/FAILED 允许")
    void runningToTerminal() {
      assertThat(JobTaskStatusEnum.RUNNING.canTransitTo(JobTaskStatusEnum.SUCCESS)).isTrue();
      assertThat(JobTaskStatusEnum.RUNNING.canTransitTo(JobTaskStatusEnum.FAILED)).isTrue();
    }

    @Test
    @DisplayName("RUNNING → PENDING 拒绝")
    void runningToPendingRejected() {
      assertThat(JobTaskStatusEnum.RUNNING.canTransitTo(JobTaskStatusEnum.PENDING)).isFalse();
    }

    @Test
    @DisplayName("SUCCESS → 任意状态拒绝")
    void successToAnyRejected() {
      for (JobTaskStatusEnum target : JobTaskStatusEnum.values()) {
        if (target == JobTaskStatusEnum.SUCCESS) {
          continue;
        }
        assertThat(JobTaskStatusEnum.SUCCESS.canTransitTo(target)).isFalse();
      }
    }

    @Test
    @DisplayName("FAILED → 任意状态拒绝")
    void failedToAnyRejected() {
      for (JobTaskStatusEnum target : JobTaskStatusEnum.values()) {
        if (target == JobTaskStatusEnum.FAILED) {
          continue;
        }
        assertThat(JobTaskStatusEnum.FAILED.canTransitTo(target)).isFalse();
      }
    }
  }
}
