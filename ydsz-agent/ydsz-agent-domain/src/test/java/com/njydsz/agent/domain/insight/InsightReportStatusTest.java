package com.njydsz.agent.domain.insight;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InsightReportStatus} 单元测试。
 *
 * <p>覆盖：编码解析（未知/null 兜底 DRAFT）、终态判定、状态流转规则、编码与描述字段。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("InsightReportStatus 洞察报告状态枚举")
class InsightReportStatusTest {

  @Nested
  @DisplayName("编码解析")
  class FromCodeTest {

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
    @CsvSource({
      "draft, DRAFT",
      "completed, COMPLETED",
      "failed, FAILED",
      "exported, EXPORTED",
    })
    @DisplayName("合法编码应解析为对应枚举")
    void shouldParseValidCode(String code, InsightReportStatus expected) {
      assertThat(InsightReportStatus.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 应兜底为 DRAFT")
    void shouldFallbackToDraftForNull() {
      assertThat(InsightReportStatus.fromCode(null)).isEqualTo(InsightReportStatus.DRAFT);
    }

    @Test
    @DisplayName("空白字符串应兜底为 DRAFT")
    void shouldFallbackToDraftForBlank() {
      assertThat(InsightReportStatus.fromCode("   ")).isEqualTo(InsightReportStatus.DRAFT);
    }

    @ParameterizedTest(name = "fromCode(\"{0}\") 应兜底为 DRAFT")
    @ValueSource(strings = {"DRAFT", "unknown", "GENERATING"})
    @DisplayName("未知/大写编码应兜底为 DRAFT")
    void shouldFallbackToDraftForUnknown(String code) {
      assertThat(InsightReportStatus.fromCode(code)).isEqualTo(InsightReportStatus.DRAFT);
    }
  }

  @Nested
  @DisplayName("终态判定")
  class TerminalTest {

    @ParameterizedTest(name = "isTerminal({0}) 应为 {1}")
    @CsvSource({
      "FAILED, true",
      "EXPORTED, true",
      "DRAFT, false",
      "COMPLETED, false",
    })
    @DisplayName("终态判定应正确")
    void shouldJudgeTerminal(InsightReportStatus status, boolean expected) {
      assertThat(status.isTerminal()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("状态流转")
  class TransitTest {

    @ParameterizedTest(name = "{0} → {1} 应允许")
    @CsvSource({
      "DRAFT, COMPLETED",
      "DRAFT, FAILED",
      "COMPLETED, EXPORTED",
      "DRAFT, DRAFT",
      "COMPLETED, COMPLETED",
      "FAILED, FAILED",
      "EXPORTED, EXPORTED",
    })
    @DisplayName("合法流转应被允许")
    void shouldAllowValidTransit(InsightReportStatus from, InsightReportStatus to) {
      assertThat(from.canTransitTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} 应拒绝")
    @CsvSource({
      "DRAFT, EXPORTED",
      "COMPLETED, DRAFT",
      "COMPLETED, FAILED",
      "FAILED, DRAFT",
      "FAILED, COMPLETED",
      "EXPORTED, DRAFT",
      "EXPORTED, COMPLETED",
    })
    @DisplayName("非法流转应被拒绝")
    void shouldRejectInvalidTransit(InsightReportStatus from, InsightReportStatus to) {
      assertThat(from.canTransitTo(to)).isFalse();
    }

    @Test
    @DisplayName("目标为 null 应拒绝")
    void shouldRejectNullTarget() {
      assertThat(InsightReportStatus.DRAFT.canTransitTo(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("编码与描述")
  class MetaTest {

    @Test
    @DisplayName("编码应为小写且唯一")
    void shouldHaveUniqueLowercaseCodes() {
      long distinct = Arrays.stream(InsightReportStatus.values())
          .map(InsightReportStatus::getCode)
          .distinct()
          .count();
      assertThat(distinct).isEqualTo(InsightReportStatus.values().length);
      for (InsightReportStatus status : InsightReportStatus.values()) {
        assertThat(status.getCode()).isEqualTo(status.getCode().toLowerCase());
      }
    }

    @Test
    @DisplayName("描述应为非空中文")
    void shouldHaveDescription() {
      for (InsightReportStatus status : InsightReportStatus.values()) {
        assertThat(status.getDescription()).isNotBlank();
      }
    }
  }
}
