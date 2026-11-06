package com.njydsz.system.domain.enums;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApiPermissionStatus} 单元测试。
 *
 * <p>覆盖：fromCode 解析（大小写/空白/未知返回 null）、状态码字符串、双向可逆流转、无终态、allStates。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("接口权限状态枚举解析")
class ApiPermissionStatusTest {

  @Nested
  @DisplayName("fromCode 合法值")
  class FromCodeValid {

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
    @CsvSource({"ENABLED, ENABLED", "DISABLED, DISABLED"})
    @DisplayName("全量状态码均可解析")
    void should_parse_all_codes(String input, ApiPermissionStatus expected) {
      assertThat(ApiPermissionStatus.fromCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "fromCode(\"{0}\") 大小写无关")
    @CsvSource({"enabled, ENABLED", "Disabled, DISABLED"})
    @DisplayName("大小写无关解析")
    void should_be_case_insensitive(String input, ApiPermissionStatus expected) {
      assertThat(ApiPermissionStatus.fromCode(input)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("fromCode 非法值返回 null")
  class FromCodeInvalid {

    @Test
    @DisplayName("null 返回 null")
    void should_return_null_for_null() {
      assertThat(ApiPermissionStatus.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("空串返回 null")
    void should_return_null_for_empty() {
      assertThat(ApiPermissionStatus.fromCode("")).isNull();
    }

    @Test
    @DisplayName("纯空白返回 null")
    void should_return_null_for_blank() {
      assertThat(ApiPermissionStatus.fromCode("   ")).isNull();
    }

    @ParameterizedTest(name = "fromCode(\"{0}\") 未知值返回 null")
    @ValueSource(strings = {"FAKE", "ENABLEDX", "1", "enabled_disabled"})
    @DisplayName("未知值返回 null")
    void should_return_null_for_unknown(String input) {
      assertThat(ApiPermissionStatus.fromCode(input)).isNull();
    }
  }

  @Nested
  @DisplayName("状态码字符串")
  class CodeValue {

    @ParameterizedTest(name = "{0}.getCode() 应为 \"{1}\"")
    @CsvSource({"ENABLED, ENABLED", "DISABLED, DISABLED"})
    @DisplayName("getCode 返回状态码")
    void should_return_code(ApiPermissionStatus status, String expected) {
      assertThat(status.getCode()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("状态流转")
  class Transition {

    @Test
    @DisplayName("启用/禁用双向可流转")
    void should_allow_bidirectional_transition() {
      assertThat(ApiPermissionStatus.ENABLED.canTransitTo(ApiPermissionStatus.DISABLED)).isTrue();
      assertThat(ApiPermissionStatus.DISABLED.canTransitTo(ApiPermissionStatus.ENABLED)).isTrue();
    }

    @Test
    @DisplayName("自身到自身允许")
    void self_transition_allowed() {
      assertThat(ApiPermissionStatus.ENABLED.canTransitTo(ApiPermissionStatus.ENABLED)).isTrue();
      assertThat(ApiPermissionStatus.DISABLED.canTransitTo(ApiPermissionStatus.DISABLED)).isTrue();
    }

    @Test
    @DisplayName("target 为 null 时返回 false")
    void should_reject_null_target() {
      assertThat(ApiPermissionStatus.ENABLED.canTransitTo(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("allStates")
  class AllStates {

    @Test
    @DisplayName("返回全量状态列表")
    void should_return_all_states() {
      List<ApiPermissionStatus> states = ApiPermissionStatus.ENABLED.allStates();

      assertThat(states).hasSize(2);
      assertThat(states).containsExactly(ApiPermissionStatus.ENABLED, ApiPermissionStatus.DISABLED);
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 2 个状态")
    void should_have_two_statuses() {
      assertThat(ApiPermissionStatus.values()).hasSize(2);
    }
  }
}
