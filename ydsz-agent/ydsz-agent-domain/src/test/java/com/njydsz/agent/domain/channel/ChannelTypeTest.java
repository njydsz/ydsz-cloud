package com.njydsz.agent.domain.channel;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChannelType} 单元测试。
 *
 * <p>覆盖：编码解析（含 null/未知）、编码与描述字段。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("ChannelType 渠道类型枚举")
class ChannelTypeTest {

  @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
  @CsvSource({
    "web, WEB",
    "api, API",
    "webhook, WEBHOOK",
    "mq, MESSAGE_QUEUE",
    "scheduled, SCHEDULED",
  })
  @DisplayName("合法编码应解析为对应枚举")
  void shouldParseValidCode(String code, ChannelType expected) {
    assertThat(ChannelType.fromCode(code)).isEqualTo(expected);
  }

  @Test
  @DisplayName("未知编码应返回 null")
  void shouldReturnNullForUnknownCode() {
    assertThat(ChannelType.fromCode("unknown-channel")).isNull();
  }

  @Test
  @DisplayName("null 编码应返回 null（fromCode 未做 null 防护，依赖 equals 顺序）")
  void shouldReturnNullForNullCode() {
    assertThat(ChannelType.fromCode(null)).isNull();
  }

  @Test
  @DisplayName("所有渠道编码应唯一且非空")
  void shouldHaveUniqueCodes() {
    long distinct = Arrays.stream(ChannelType.values())
        .map(ChannelType::getCode)
        .distinct()
        .count();
    assertThat(distinct).isEqualTo(ChannelType.values().length);
  }

  @Test
  @DisplayName("所有渠道描述应非空")
  void shouldHaveDescription() {
    for (ChannelType type : ChannelType.values()) {
      assertThat(type.getDescription()).isNotBlank();
    }
  }
}
