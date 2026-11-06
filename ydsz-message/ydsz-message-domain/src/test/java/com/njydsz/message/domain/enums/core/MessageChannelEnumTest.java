package com.njydsz.message.domain.enums.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MessageChannelEnum} 单元测试。
 *
 * <p>覆盖：安全解析（大小写/空白/非法）、空值与边界输入、全量枚举值。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("消息通道枚举解析")
class MessageChannelEnumTest {

  @Nested
  @DisplayName("parse 合法值")
  class ParseValid {

    @ParameterizedTest(name = "parse(\"{0}\") 应返回 {1}")
    @CsvSource({
        "SMS, SMS",
        "EMAIL, EMAIL",
        "PUSH, PUSH",
        "INAPP, INAPP",
        "WEBHOOK, WEBHOOK",
        "DINGTALK, DINGTALK",
        "DINGTALK_WORK, DINGTALK_WORK",
        "WECOM, WECOM",
        "WECOM_APP, WECOM_APP",
        "FEISHU, FEISHU",
        "WX_MINI, WX_MINI",
        "ALIPAY_MINI, ALIPAY_MINI"
    })
    @DisplayName("全量枚举值均可解析")
    void should_parse_all_channels(String input, MessageChannelEnum expected) {
      assertThat(MessageChannelEnum.parse(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "parse(\"{0}\") 大小写无关")
    @CsvSource({
        "sms, SMS",
        "email, EMAIL",
        "Push, PUSH",
        "inApp, INAPP",
        "dingtalk_work, DINGTALK_WORK",
        "WeCom_App, WECOM_APP",
        "alipay_mini, ALIPAY_MINI"
    })
    @DisplayName("大小写无关解析")
    void should_be_case_insensitive(String input, MessageChannelEnum expected) {
      assertThat(MessageChannelEnum.parse(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("前后空白被裁剪")
    void should_trim_whitespace() {
      assertThat(MessageChannelEnum.parse("  SMS  ")).isEqualTo(MessageChannelEnum.SMS);
      assertThat(MessageChannelEnum.parse("\tEMAIL\n")).isEqualTo(MessageChannelEnum.EMAIL);
    }
  }

  @Nested
  @DisplayName("parse 非法值")
  class ParseInvalid {

    @ParameterizedTest(name = "parse(\"{0}\") 应抛出 IllegalArgumentException")
    @ValueSource(strings = {"FAKE", "SMSX", "sms_email", "UNKNOWN", "1"})
    @DisplayName("未知通道抛出异常")
    void should_throw_for_unknown(String input) {
      assertThatThrownBy(() -> MessageChannelEnum.parse(input))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("unsupported_channel");
    }

    @Test
    @DisplayName("null 抛出异常并携带 required 提示")
    void should_throw_for_null() {
      assertThatThrownBy(() -> MessageChannelEnum.parse(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("channel_required");
    }

    @Test
    @DisplayName("空串抛出异常并携带 required 提示")
    void should_throw_for_empty() {
      assertThatThrownBy(() -> MessageChannelEnum.parse(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("channel_required");
    }

    @Test
    @DisplayName("纯空白串抛出异常并携带 required 提示")
    void should_throw_for_blank() {
      assertThatThrownBy(() -> MessageChannelEnum.parse("   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("channel_required");
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 12 个通道")
    void should_have_twelve_channels() {
      assertThat(MessageChannelEnum.values()).hasSize(12);
    }
  }
}
