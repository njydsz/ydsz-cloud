package com.njydsz.message.domain.enums.receipt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ReceiptTypeEnum} 单元测试。
 *
 * <p>覆盖：全量枚举值、valueOf 往返、未知值拒绝。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("回执类型枚举解析")
class ReceiptTypeEnumTest {

  @Nested
  @DisplayName("枚举值")
  class Values {

    @ParameterizedTest(name = "包含 {0}")
    @CsvSource({"DELIVERED", "READ", "CLICKED", "FAILED"})
    @DisplayName("全量枚举值存在")
    void should_contain_all_types(String name) {
      assertThat(ReceiptTypeEnum.valueOf(name)).isNotNull();
    }

    @Test
    @DisplayName("共 4 个类型")
    void should_have_four_types() {
      assertThat(ReceiptTypeEnum.values()).hasSize(4);
    }

    @Test
    @DisplayName("valueOf 往返一致")
    void should_round_trip_value_of() {
      for (ReceiptTypeEnum type : ReceiptTypeEnum.values()) {
        assertThat(ReceiptTypeEnum.valueOf(type.name())).isEqualTo(type);
      }
    }
  }

  @Nested
  @DisplayName("未知值拒绝")
  class Unknown {

    @ParameterizedTest(name = "valueOf(\"{0}\") 抛出异常")
    @CsvSource({"RECEIVE", "NONEX", "DELIVEREDX", "receive_read"})
    @DisplayName("未知类型抛出异常")
    void should_throw_for_unknown(String input) {
      assertThatThrownBy(() -> ReceiptTypeEnum.valueOf(input))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("不存在 RECEIVE 类型")
    void should_not_have_receive() {
      assertThatThrownBy(() -> ReceiptTypeEnum.valueOf("RECEIVE"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("枚举名")
  class Names {

    @ParameterizedTest(name = "name() 应为 {0}")
    @CsvSource({"DELIVERED, DELIVERED", "READ, READ", "CLICKED, CLICKED", "FAILED, FAILED"})
    @DisplayName("枚举名正确")
    void should_have_expected_name(ReceiptTypeEnum type, String expected) {
      assertThat(type.name()).isEqualTo(expected);
    }
  }
}
