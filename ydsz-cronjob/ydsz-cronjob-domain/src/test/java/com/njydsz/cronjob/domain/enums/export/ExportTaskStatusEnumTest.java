package com.njydsz.cronjob.domain.enums.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExportTaskStatusEnum} 单元测试。
 *
 * <p>覆盖：code/description getter、isTerminal 终态判定、fromCode 解析（含 null/非法）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("异步导出任务状态枚举")
class ExportTaskStatusEnumTest {

  @Nested
  @DisplayName("code/description getter")
  class Accessors {

    @ParameterizedTest
    @CsvSource({
      "PENDING, PENDING, 待处理",
      "PROCESSING, PROCESSING, 处理中",
      "SUCCEEDED, SUCCEEDED, 导出成功",
      "FAILED, FAILED, 导出失败",
      "CANCELED, CANCELED, 已取消"
    })
    @DisplayName("每个枚举的 code 与 description 一致")
    void codeAndDescription(
        ExportTaskStatusEnum status, String expectedCode, String expectedDesc) {
      assertThat(status.getCode()).isEqualTo(expectedCode);
      assertThat(status.getDescription()).isEqualTo(expectedDesc);
    }
  }

  @Nested
  @DisplayName("isTerminal 终态判定")
  class Terminal {

    @ParameterizedTest
    @ValueSource(strings = {"SUCCEEDED", "FAILED", "CANCELED"})
    @DisplayName("终态码返回 true")
    void terminalCodes(String code) {
      assertThat(ExportTaskStatusEnum.isTerminal(code)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PROCESSING"})
    @DisplayName("非终态码返回 false")
    void nonTerminalCodes(String code) {
      assertThat(ExportTaskStatusEnum.isTerminal(code)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/空串返回 false")
    void nullAndEmpty(String code) {
      assertThat(ExportTaskStatusEnum.isTerminal(code)).isFalse();
    }

    @Test
    @DisplayName("未匹配码返回 false")
    void unknownCode() {
      assertThat(ExportTaskStatusEnum.isTerminal("NOT_EXIST")).isFalse();
    }
  }

  @Nested
  @DisplayName("fromCode 解析")
  class FromCode {

    @ParameterizedTest
    @CsvSource({
      "PENDING, PENDING",
      "PROCESSING, PROCESSING",
      "SUCCEEDED, SUCCEEDED",
      "FAILED, FAILED",
      "CANCELED, CANCELED"
    })
    @DisplayName("合法码解析为对应枚举")
    void validCode(String code, ExportTaskStatusEnum expected) {
      assertThat(ExportTaskStatusEnum.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 返回 null")
    void nullReturnsNull() {
      assertThat(ExportTaskStatusEnum.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("非法码返回 null")
    void illegalReturnsNull() {
      assertThat(ExportTaskStatusEnum.fromCode("UNKNOWN")).isNull();
    }
  }
}
