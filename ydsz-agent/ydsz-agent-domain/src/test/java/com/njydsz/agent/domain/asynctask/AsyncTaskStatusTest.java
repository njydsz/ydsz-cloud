package com.njydsz.agent.domain.asynctask;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AsyncTaskStatus} 单元测试。
 *
 * <p>覆盖：编码解析、终态判定（实例方法 vs 静态方法语义差异）、可重试判定、状态流转规则、全量状态列表。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("AsyncTaskStatus 异步任务状态枚举")
class AsyncTaskStatusTest {

  @Nested
  @DisplayName("编码解析")
  class FromCodeTest {

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
    @CsvSource({
      "PENDING, PENDING",
      "RUNNING, RUNNING",
      "SUCCEEDED, SUCCEEDED",
      "FAILED, FAILED",
      "CANCELED, CANCELED",
      "EXPIRED, EXPIRED",
    })
    @DisplayName("合法编码应解析为对应枚举")
    void shouldParseValidCode(String code, AsyncTaskStatus expected) {
      assertThat(AsyncTaskStatus.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("未知编码应返回 null")
    void shouldReturnNullForUnknownCode() {
      assertThat(AsyncTaskStatus.fromCode("NOT_EXIST")).isNull();
    }

    @Test
    @DisplayName("null 编码应返回 null")
    void shouldReturnNullForNullCode() {
      assertThat(AsyncTaskStatus.fromCode(null)).isNull();
    }
  }

  @Nested
  @DisplayName("终态判定")
  class TerminalTest {

    @ParameterizedTest(name = "isTerminal(\"{0}\") 静态方法应为 {1}")
    @CsvSource({
      "SUCCEEDED, true",
      "FAILED, true",
      "CANCELED, true",
      "EXPIRED, true",
      "PENDING, false",
      "RUNNING, false",
    })
    @DisplayName("静态 isTerminal 应把 FAILED 视为业务终态")
    void shouldJudgeStaticTerminal(String code, boolean expected) {
      assertThat(AsyncTaskStatus.isTerminal(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}.isTerminal() 实例方法应为 {1}")
    @CsvSource({
      "SUCCEEDED, true",
      "CANCELED, true",
      "EXPIRED, true",
      "FAILED, false",
      "PENDING, false",
      "RUNNING, false",
    })
    @DisplayName("实例 isTerminal 应把 FAILED 排除（可重试）")
    void shouldJudgeInstanceTerminal(AsyncTaskStatus status, boolean expected) {
      assertThat(status.isTerminal()).isEqualTo(expected);
    }

    @Test
    @DisplayName("静态方法传入 null 或未知编码应返回 false")
    void shouldReturnFalseForNullOrUnknown() {
      assertThat(AsyncTaskStatus.isTerminal(null)).isFalse();
      assertThat(AsyncTaskStatus.isTerminal("UNKNOWN")).isFalse();
    }
  }

  @Nested
  @DisplayName("可重试判定")
  class RetryableTest {

    @Test
    @DisplayName("仅 FAILED 可重试")
    void shouldOnlyRetryFailed() {
      assertThat(AsyncTaskStatus.isRetryable("FAILED")).isTrue();
      assertThat(AsyncTaskStatus.isRetryable("SUCCEEDED")).isFalse();
      assertThat(AsyncTaskStatus.isRetryable("PENDING")).isFalse();
    }

    @Test
    @DisplayName("null 或未知编码不可重试")
    void shouldNotRetryNullOrUnknown() {
      assertThat(AsyncTaskStatus.isRetryable(null)).isFalse();
      assertThat(AsyncTaskStatus.isRetryable("NOPE")).isFalse();
    }
  }

  @Nested
  @DisplayName("状态流转")
  class TransitTest {

    @ParameterizedTest(name = "{0} → {1} 应允许")
    @CsvSource({
      "PENDING, RUNNING",
      "PENDING, CANCELED",
      "PENDING, EXPIRED",
      "RUNNING, SUCCEEDED",
      "RUNNING, FAILED",
      "RUNNING, EXPIRED",
      "FAILED, PENDING",
    })
    @DisplayName("合法流转应被允许")
    void shouldAllowValidTransit(AsyncTaskStatus from, AsyncTaskStatus to) {
      assertThat(from.canTransitTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} 应拒绝")
    @CsvSource({
      "PENDING, SUCCEEDED",
      "PENDING, FAILED",
      "RUNNING, PENDING",
      "RUNNING, CANCELED",
      "FAILED, RUNNING",
      "FAILED, SUCCEEDED",
      "SUCCEEDED, RUNNING",
      "CANCELED, PENDING",
      "EXPIRED, RUNNING",
    })
    @DisplayName("非法流转应被拒绝")
    void shouldRejectInvalidTransit(AsyncTaskStatus from, AsyncTaskStatus to) {
      assertThat(from.canTransitTo(to)).isFalse();
    }

    @Test
    @DisplayName("目标为 null 应拒绝")
    void shouldRejectNullTarget() {
      assertThat(AsyncTaskStatus.PENDING.canTransitTo(null)).isFalse();
    }

    @Test
    @DisplayName("自身到自身应始终允许")
    void shouldAllowSelfTransit() {
      for (AsyncTaskStatus status : AsyncTaskStatus.values()) {
        assertThat(status.canTransitTo(status)).isTrue();
      }
    }
  }

  @Nested
  @DisplayName("全量状态")
  class AllStatesTest {

    @Test
    @DisplayName("allStates 应返回全部 6 个状态")
    void shouldReturnAllStates() {
      List<AsyncTaskStatus> all = AsyncTaskStatus.PENDING.allStates();
      assertThat(all).containsExactly(AsyncTaskStatus.values());
    }
  }

  @Nested
  @DisplayName("编码与描述")
  class MetaTest {

    @Test
    @DisplayName("getCode 应与枚举名一致")
    void shouldCodeMatchName() {
      for (AsyncTaskStatus status : AsyncTaskStatus.values()) {
        assertThat(status.getCode()).isEqualTo(status.name());
      }
    }

    @Test
    @DisplayName("description 应为非空 i18n key")
    void shouldHaveDescription() {
      for (AsyncTaskStatus status : AsyncTaskStatus.values()) {
        assertThat(status.getDescription()).isNotBlank();
      }
    }
  }
}
