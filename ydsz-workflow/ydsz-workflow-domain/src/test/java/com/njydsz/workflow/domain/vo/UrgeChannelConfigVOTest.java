package com.njydsz.workflow.domain.vo;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UrgeChannelConfigVO} 单元测试。
 *
 * <p>覆盖：fromExt 解析（默认值/通道/间隔/次数/启用）、getValidChannels 过滤、Channel 常量不可实例化。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("催办通道配置值对象")
class UrgeChannelConfigVOTest {

  @Nested
  @DisplayName("fromExt 解析")
  class FromExt {

    @Test
    @DisplayName("null Map 使用默认值")
    void nullMapUsesDefaults() {
      UrgeChannelConfigVO config = UrgeChannelConfigVO.fromExt(null);
      assertThat(config.getUrgeChannels()).containsExactly("INAPP");
      assertThat(config.getUrgeIntervalMinutes()).isEqualTo(30);
      assertThat(config.getUrgeMaxCount()).isEqualTo(3);
      assertThat(config.isUrgeEnabled()).isTrue();
    }

    @Test
    @DisplayName("解析多通道与间隔")
    void multiChannels() {
      UrgeChannelConfigVO config =
          UrgeChannelConfigVO.fromExt(
              Map.of(
                  "urgeChannels", List.of("INAPP", "EMAIL", "SMS"),
                  "urgeIntervalMinutes", 15,
                  "urgeMaxCount", 5,
                  "urgeEnabled", false));
      assertThat(config.getUrgeChannels()).containsExactly("INAPP", "EMAIL", "SMS");
      assertThat(config.getUrgeIntervalMinutes()).isEqualTo(15);
      assertThat(config.getUrgeMaxCount()).isEqualTo(5);
      assertThat(config.isUrgeEnabled()).isFalse();
    }

    @Test
    @DisplayName("interval ≤0 时回退默认值")
    void intervalMin() {
      assertThat(
              UrgeChannelConfigVO.fromExt(Map.of("urgeIntervalMinutes", 0))
                  .getUrgeIntervalMinutes())
          .isEqualTo(30);
    }

    @Test
    @DisplayName("maxCount ≤0 时回退默认值")
    void maxCountMin() {
      assertThat(
              UrgeChannelConfigVO.fromExt(Map.of("urgeMaxCount", -1)).getUrgeMaxCount())
          .isEqualTo(3);
    }

    @Test
    @DisplayName("空通道列表回退默认值")
    void emptyChannelsFallback() {
      assertThat(
              UrgeChannelConfigVO.fromExt(Map.of("urgeChannels", List.of()))
                  .getUrgeChannels())
          .containsExactly("INAPP");
    }
  }

  @Nested
  @DisplayName("getValidChannels 过滤")
  class ValidChannels {

    @Test
    @DisplayName("过滤无效通道")
    void filtersInvalid() {
      UrgeChannelConfigVO config =
          UrgeChannelConfigVO.fromExt(
              Map.of("urgeChannels", List.of("INAPP", "BOGUS", "EMAIL")));
      assertThat(config.getValidChannels()).containsExactly("INAPP", "EMAIL");
    }

    @Test
    @DisplayName("全部无效时回退默认 INAPP")
    void allInvalidFallback() {
      UrgeChannelConfigVO config =
          UrgeChannelConfigVO.fromExt(Map.of("urgeChannels", List.of("BOGUS", "FAKE")));
      assertThat(config.getValidChannels()).containsExactly("INAPP");
    }
  }

  @Nested
  @DisplayName("fromExtJson 容错")
  class FromExtJson {

    @Test
    @DisplayName("非法 JSON 容错")
    void invalidJson() {
      assertThat(UrgeChannelConfigVO.fromExtJson("bad").getUrgeChannels())
          .containsExactly("INAPP");
    }

    @Test
    @DisplayName("合法 JSON 解析正确")
    void validJson() {
      UrgeChannelConfigVO config =
          UrgeChannelConfigVO.fromExtJson("{\"urgeChannels\":[\"SMS\"],\"urgeEnabled\":false}");
      assertThat(config.getUrgeChannels()).containsExactly("SMS");
      assertThat(config.isUrgeEnabled()).isFalse();
    }
  }

  @Nested
  @DisplayName("Channel 常量")
  class ChannelConstants {

    @Test
    @DisplayName("7 个通道常量值正确")
    void channelValues() {
      assertThat(UrgeChannelConfigVO.Channel.INAPP).isEqualTo("INAPP");
      assertThat(UrgeChannelConfigVO.Channel.EMAIL).isEqualTo("EMAIL");
      assertThat(UrgeChannelConfigVO.Channel.WEBHOOK).isEqualTo("WEBHOOK");
      assertThat(UrgeChannelConfigVO.Channel.SMS).isEqualTo("SMS");
      assertThat(UrgeChannelConfigVO.Channel.WECOM).isEqualTo("WECOM");
      assertThat(UrgeChannelConfigVO.Channel.DINGTALK).isEqualTo("DINGTALK");
      assertThat(UrgeChannelConfigVO.Channel.FEISHU).isEqualTo("FEISHU");
    }
  }
}
