package com.njydsz.agent.domain.trigger;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TriggerType} 单元测试。
 *
 * <p>覆盖：编码解析（精确匹配，小写 code）、未知/null 兜底、编码与描述字段。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("TriggerType 触发器类型枚举")
class TriggerTypeTest {

  @Nested
  @DisplayName("编码解析")
  class FromCodeTest {

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
    @CsvSource({
      "cron, CRON",
      "webhook, WEBHOOK",
      "channel_message, CHANNEL_MESSAGE",
      "agent_lifecycle, AGENT_LIFECYCLE",
      "content_match, CONTENT_MATCH",
      "workflow_completion, WORKFLOW_COMPLETION",
    })
    @DisplayName("合法编码应解析为对应枚举")
    void shouldParseValidCode(String code, TriggerType expected) {
      assertThat(TriggerType.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 编码应返回 null")
    void shouldReturnNullForNullCode() {
      assertThat(TriggerType.fromCode(null)).isNull();
    }

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 null")
    @ValueSource(strings = {"CRON", "Webhook", "unknown"})
    @DisplayName("大小写不符/未知应返回 null（精确匹配）")
    void shouldReturnNullForUnknown(String code) {
      assertThat(TriggerType.fromCode(code)).isNull();
    }
  }

  @Nested
  @DisplayName("元数据")
  class MetaTest {

    @Test
    @DisplayName("编码应为小写且唯一")
    void shouldHaveUniqueLowercaseCodes() {
      long distinct = Arrays.stream(TriggerType.values())
          .map(TriggerType::getCode)
          .distinct()
          .count();
      assertThat(distinct).isEqualTo(TriggerType.values().length);
      for (TriggerType type : TriggerType.values()) {
        assertThat(type.getCode()).isEqualTo(type.getCode().toLowerCase());
      }
    }

    @Test
    @DisplayName("描述应为非空中文")
    void shouldHaveDescription() {
      for (TriggerType type : TriggerType.values()) {
        assertThat(type.getDesc()).isNotBlank();
      }
    }
  }
}
