package com.njydsz.message.domain.enums.receipt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ReadStatusEnum} 单元测试。
 *
 * <p>覆盖：parse 兼容旧数据 0/1 与枚举名、toDbValue 持久化映射、终态判定、状态流转规则。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("已读状态枚举解析")
class ReadStatusEnumTest {

  @Nested
  @DisplayName("parse 合法值")
  class ParseValid {

    @ParameterizedTest(name = "parse(\"{0}\") 应返回 {1}")
    @CsvSource({
        "UNREAD, UNREAD",
        "READ, READ"
    })
    @DisplayName("全量枚举名均可解析")
    void should_parse_all_names(String input, ReadStatusEnum expected) {
      assertThat(ReadStatusEnum.parse(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "parse(\"{0}\") 大小写无关")
    @CsvSource({
        "unread, UNREAD",
        "Read, READ"
    })
    @DisplayName("大小写无关解析")
    void should_be_case_insensitive(String input, ReadStatusEnum expected) {
      assertThat(ReadStatusEnum.parse(input)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("parse 兼容旧数据")
  class ParseLegacy {

    @ParameterizedTest(name = "parse(\"{0}\") 应返回 {1}")
    @CsvSource({"0, UNREAD", "1, READ"})
    @DisplayName("旧数字字符串映射")
    void should_parse_legacy_numbers(String input, ReadStatusEnum expected) {
      assertThat(ReadStatusEnum.parse(input)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("parse 非法值回退")
  class ParseInvalid {

    @Test
    @DisplayName("null 回退为 UNREAD")
    void should_fallback_for_null() {
      assertThat(ReadStatusEnum.parse(null)).isEqualTo(ReadStatusEnum.UNREAD);
    }

    @Test
    @DisplayName("空串回退为 UNREAD")
    void should_fallback_for_empty() {
      assertThat(ReadStatusEnum.parse("")).isEqualTo(ReadStatusEnum.UNREAD);
    }

    @ParameterizedTest(name = "parse(\"{0}\") 未知值回退 UNREAD")
    @ValueSource(strings = {"DELETED", "UNREADX", "2", "read_unread"})
    @DisplayName("未知值回退 UNREAD")
    void should_fallback_for_unknown(String input) {
      assertThat(ReadStatusEnum.parse(input)).isEqualTo(ReadStatusEnum.UNREAD);
    }
  }

  @Nested
  @DisplayName("toDbValue 持久化映射")
  class ToDbValue {

    @ParameterizedTest(name = "{0} 应持久化为 \"{1}\"")
    @CsvSource({"UNREAD, 0", "READ, 1"})
    @DisplayName("枚举名到旧数字字符串")
    void should_map_to_db_value(ReadStatusEnum status, String expected) {
      assertThat(status.toDbValue()).isEqualTo(expected);
    }

    @Test
    @DisplayName("parse 与 toDbValue 互为逆操作")
    void should_round_trip() {
      for (ReadStatusEnum status : ReadStatusEnum.values()) {
        assertThat(ReadStatusEnum.parse(status.toDbValue())).isEqualTo(status);
      }
    }
  }

  @Nested
  @DisplayName("终态判定")
  class Terminal {

    @Test
    @DisplayName("READ 为终态")
    void read_is_terminal() {
      assertThat(ReadStatusEnum.READ.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("UNREAD 非终态")
    void unread_is_not_terminal() {
      assertThat(ReadStatusEnum.UNREAD.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("状态流转")
  class Transition {

    @Test
    @DisplayName("UNREAD 可流转到 READ")
    void unread_to_read() {
      assertThat(ReadStatusEnum.UNREAD.canTransitTo(ReadStatusEnum.READ)).isTrue();
    }

    @Test
    @DisplayName("READ 为终态不可再流转")
    void read_is_terminal_transition() {
      assertThat(ReadStatusEnum.READ.canTransitTo(ReadStatusEnum.UNREAD)).isFalse();
    }

    @Test
    @DisplayName("自身到自身允许")
    void self_transition_allowed() {
      assertThat(ReadStatusEnum.UNREAD.canTransitTo(ReadStatusEnum.UNREAD)).isTrue();
      assertThat(ReadStatusEnum.READ.canTransitTo(ReadStatusEnum.READ)).isTrue();
    }

    @Test
    @DisplayName("非法流转 requireTransitTo 抛出异常")
    void require_transit_throws() {
      assertThatThrownBy(() -> ReadStatusEnum.READ.requireTransitTo(ReadStatusEnum.UNREAD))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 2 个状态")
    void should_have_two_statuses() {
      assertThat(ReadStatusEnum.values()).hasSize(2);
    }
  }
}
