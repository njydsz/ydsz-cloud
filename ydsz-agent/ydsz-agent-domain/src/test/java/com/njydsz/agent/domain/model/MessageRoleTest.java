package com.njydsz.agent.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MessageRole} 单元测试。
 *
 * <p>覆盖：大小写不敏感解析、未知值降级为 USER、null 兜底、apiValue 字段。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("MessageRole 消息角色枚举")
class MessageRoleTest {

  @ParameterizedTest(name = "fromApiValue(\"{0}\") 应返回 {1}")
  @CsvSource({
    "system, SYSTEM",
    "user, USER",
    "assistant, ASSISTANT",
    "tool, TOOL",
    "System, SYSTEM",
    "USER, USER",
    "Assistant, ASSISTANT",
    "TOOL, TOOL",
  })
  @DisplayName("大小写不敏感解析应成功")
  void shouldParseCaseInsensitive(String value, MessageRole expected) {
    assertThat(MessageRole.fromApiValue(value)).isEqualTo(expected);
  }

  @Test
  @DisplayName("null 应降级为 USER")
  void shouldFallbackForNull() {
    assertThat(MessageRole.fromApiValue(null)).isEqualTo(MessageRole.USER);
  }

  @ParameterizedTest(name = "fromApiValue(\"{0}\") 应降级为 USER")
  @ValueSource(strings = {"human", " ", "", "unknown"})
  @DisplayName("未知/空白值应降级为 USER")
  void shouldFallbackForUnknown(String value) {
    assertThat(MessageRole.fromApiValue(value)).isEqualTo(MessageRole.USER);
  }

  @Test
  @DisplayName("apiValue 字段应符合定义")
  void shouldHaveExpectedApiValues() {
    assertThat(MessageRole.SYSTEM.getApiValue()).isEqualTo("system");
    assertThat(MessageRole.USER.getApiValue()).isEqualTo("user");
    assertThat(MessageRole.ASSISTANT.getApiValue()).isEqualTo("assistant");
    assertThat(MessageRole.TOOL.getApiValue()).isEqualTo("tool");
  }
}
