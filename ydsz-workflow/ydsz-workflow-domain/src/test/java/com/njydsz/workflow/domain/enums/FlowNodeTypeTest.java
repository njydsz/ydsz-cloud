package com.njydsz.workflow.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.njydsz.common.exception.custom.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FlowNodeType} 单元测试。
 *
 * <p>覆盖：of 宽松解析（null/非法回退 APPROVAL）、ofStrict 严格解析（null/非法抛异常）、code 映射。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("流程节点类型枚举")
class FlowNodeTypeTest {

  @Nested
  @DisplayName("of 宽松解析")
  class Of {

    @ParameterizedTest
    @CsvSource({
        "0, START",
        "1, APPROVAL",
        "2, CC",
        "3, CONDITION",
        "4, PARALLEL",
        "5, INCLUSIVE",
        "6, END",
        "7, SUBPROCESS",
        "8, SERVICE",
        "9, FOREACH",
        "10, LEVEL_APPROVAL",
        "11, AI_AGENT"
    })
    @DisplayName("合法 code 解析正确")
    void validCodes(int code, FlowNodeType expected) {
      assertThat(FlowNodeType.of(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 回退 APPROVAL")
    void nullFallsBackToApproval() {
      assertThat(FlowNodeType.of(null)).isEqualTo(FlowNodeType.APPROVAL);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 99, 100})
    @DisplayName("未知 code 回退 APPROVAL")
    void unknownFallsBackToApproval(int code) {
      assertThat(FlowNodeType.of(code)).isEqualTo(FlowNodeType.APPROVAL);
    }
  }

  @Nested
  @DisplayName("ofStrict 严格解析")
  class OfStrict {

    @ParameterizedTest
    @CsvSource({
        "0, START",
        "1, APPROVAL",
        "8, SERVICE",
        "11, AI_AGENT"
    })
    @DisplayName("合法 code 解析正确")
    void validCodes(int code, FlowNodeType expected) {
      assertThat(FlowNodeType.ofStrict(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 抛 BusinessException")
    void nullThrows() {
      assertThatThrownBy(() -> FlowNodeType.ofStrict(null))
          .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 99, 100})
    @DisplayName("未知 code 抛 BusinessException")
    void unknownThrows(int code) {
      assertThatThrownBy(() -> FlowNodeType.ofStrict(code))
          .isInstanceOf(BusinessException.class);
    }
  }

  @Nested
  @DisplayName("枚举属性")
  class Properties {

    @Test
    @DisplayName("每个枚举值的 code 唯一且连续")
    void codeUniqueAndContiguous() {
      int expected = 0;
      for (FlowNodeType type : FlowNodeType.values()) {
        assertThat(type.getCode()).isEqualTo(expected);
        expected++;
      }
    }

    @Test
    @DisplayName("每个枚举值的 desc 非空")
    void allHaveDesc() {
      for (FlowNodeType type : FlowNodeType.values()) {
        assertThat(type.getDesc()).isNotBlank();
      }
    }
  }
}
