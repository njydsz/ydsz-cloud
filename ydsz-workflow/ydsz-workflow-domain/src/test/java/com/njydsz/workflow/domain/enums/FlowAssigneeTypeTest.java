package com.njydsz.workflow.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FlowAssigneeType} 单元测试。
 *
 * <p>覆盖：fromName 解析（忽略大小写/null/未知返回 null）、desc 映射。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("办理人类型枚举")
class FlowAssigneeTypeTest {

  @Nested
  @DisplayName("fromName 解析")
  class FromName {

    @ParameterizedTest
    @CsvSource({
        "USER, USER",
        "ROLE, ROLE",
        "DEPT, DEPT",
        "SPEL, SPEL",
        "INITIATOR, INITIATOR",
        "LEADER, LEADER",
        "POSITION, POSITION",
        "DEPT_LEADER, DEPT_LEADER",
        "SELF_SELECT, SELF_SELECT",
        "MULTI_LEADER, MULTI_LEADER",
        "GROUP_CLAIM, GROUP_CLAIM",
        "GROUP_ALL, GROUP_ALL"
    })
    @DisplayName("合法名称解析正确")
    void validNames(String name, FlowAssigneeType expected) {
      assertThat(FlowAssigneeType.fromName(name)).isEqualTo(expected);
    }

    @Test
    @DisplayName("忽略大小写")
    void caseInsensitive() {
      assertThat(FlowAssigneeType.fromName("user")).isEqualTo(FlowAssigneeType.USER);
      assertThat(FlowAssigneeType.fromName("group_claim")).isEqualTo(FlowAssigneeType.GROUP_CLAIM);
      assertThat(FlowAssigneeType.fromName("Group_All")).isEqualTo(FlowAssigneeType.GROUP_ALL);
    }

    @Test
    @DisplayName("null 返回 null")
    void nullReturnsNull() {
      assertThat(FlowAssigneeType.fromName(null)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "user ", " admin"})
    @DisplayName("未知/空名称返回 null")
    void unknownReturnsNull(String name) {
      assertThat(FlowAssigneeType.fromName(name)).isNull();
    }
  }

  @Nested
  @DisplayName("枚举属性")
  class Properties {

    @Test
    @DisplayName("每个枚举值的 desc 非空")
    void allHaveDesc() {
      for (FlowAssigneeType type : FlowAssigneeType.values()) {
        assertThat(type.getDesc()).isNotBlank();
      }
    }
  }
}
