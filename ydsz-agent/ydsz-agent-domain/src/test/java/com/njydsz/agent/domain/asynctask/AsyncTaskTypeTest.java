package com.njydsz.agent.domain.asynctask;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AsyncTaskType} 单元测试。
 *
 * <p>覆盖：编码解析、默认超时时间、描述非空。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("AsyncTaskType 异步任务类型枚举")
class AsyncTaskTypeTest {

  @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
  @CsvSource({
    "REPORT_GENERATE, REPORT_GENERATE",
    "DOC_INGEST, DOC_INGEST",
    "BATCH_CHAT, BATCH_CHAT",
    "CODE_EXECUTION, CODE_EXECUTION",
  })
  @DisplayName("合法编码应解析为对应枚举")
  void shouldParseValidCode(String code, AsyncTaskType expected) {
    assertThat(AsyncTaskType.fromCode(code)).isEqualTo(expected);
  }

  @Test
  @DisplayName("未知编码应返回 null")
  void shouldReturnNullForUnknownCode() {
    assertThat(AsyncTaskType.fromCode("NOT_A_TYPE")).isNull();
  }

  @Test
  @DisplayName("null 编码应返回 null")
  void shouldReturnNullForNullCode() {
    assertThat(AsyncTaskType.fromCode(null)).isNull();
  }

  @Test
  @DisplayName("默认超时时间应符合定义")
  void shouldHaveExpectedTimeouts() {
    assertThat(AsyncTaskType.REPORT_GENERATE.getDefaultTimeoutSeconds()).isEqualTo(300L);
    assertThat(AsyncTaskType.DOC_INGEST.getDefaultTimeoutSeconds()).isEqualTo(600L);
    assertThat(AsyncTaskType.BATCH_CHAT.getDefaultTimeoutSeconds()).isEqualTo(120L);
    assertThat(AsyncTaskType.CODE_EXECUTION.getDefaultTimeoutSeconds()).isEqualTo(60L);
  }

  @Test
  @DisplayName("所有类型编码应与枚举名一致")
  void shouldCodeMatchName() {
    for (AsyncTaskType type : AsyncTaskType.values()) {
      assertThat(type.getCode()).isEqualTo(type.name());
    }
  }

  @Test
  @DisplayName("所有类型描述应为非空 i18n key")
  void shouldHaveDescription() {
    for (AsyncTaskType type : AsyncTaskType.values()) {
      assertThat(type.getDescription()).isNotBlank();
    }
  }
}
