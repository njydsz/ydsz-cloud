package com.njydsz.system.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SystemExceptionCode} 单元测试。
 *
 * <p>覆盖：错误码/消息键/HTTP 状态码映射、分类推断、actionHintKey 覆写。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("系统异常码枚举解析")
class SystemExceptionCodeTest {

  @Nested
  @DisplayName("基础三要素")
  class Basic {

    @ParameterizedTest(name = "{0} 的错误码应为 {1}")
    @CsvSource({
        "CONFIG_NOT_FOUND, B90001",
        "CONFIG_KEY_DUPLICATE, B90002",
        "DICT_TYPE_NOT_FOUND, B81001",
        "VARIABLE_NOT_FOUND, B82001",
        "APP_INFO_NOT_FOUND, B83001",
        "TENANT_NOT_FOUND, B84001",
        "TENANT_PLAN_NOT_FOUND, B95001",
        "ENTITY_VERSION_NOT_FOUND, B86001",
        "MONITOR_SOURCE_STORE_FAILED, B97001",
        "FEATURE_FLAG_NOT_FOUND, B99001",
        "SECONDARY_AUTH_PASSWORD_INCORRECT, B98001"
    })
    @DisplayName("错误码映射正确")
    void should_have_expected_code(SystemExceptionCode code, String expected) {
      assertThat(code.getCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("getKey 返回 i18n 消息键")
    void should_return_message_key() {
      assertThat(SystemExceptionCode.CONFIG_NOT_FOUND.getKey())
          .isEqualTo("system.config.not.found");
    }

    @Test
    @DisplayName("getMsg 默认返回 key")
    void should_return_msg_as_key() {
      assertThat(SystemExceptionCode.CONFIG_NOT_FOUND.getMsg())
          .isEqualTo("system.config.not.found");
    }
  }

  @Nested
  @DisplayName("HTTP 状态码")
  class HttpStatus {

    @ParameterizedTest(name = "{0} 的 HTTP 状态码应为 {1}")
    @CsvSource({
        "CONFIG_NOT_FOUND, 404",
        "CONFIG_KEY_DUPLICATE, 400",
        "DICT_TYPE_NOT_FOUND, 404",
        "DICT_ITEM_NOT_FOUND, 404",
        "VARIABLE_NOT_FOUND, 404",
        "TENANT_NOT_FOUND, 404",
        "TENANT_PLAN_NOT_FOUND, 404",
        "ENTITY_VERSION_NOT_FOUND, 404",
        "MONITOR_SOURCE_STORE_FAILED, 500",
        "SNAPSHOT_PARSE_ERROR, 500",
        "FEATURE_FLAG_NOT_FOUND, 404"
    })
    @DisplayName("HTTP 状态码映射正确")
    void should_have_expected_http_status(SystemExceptionCode code, int expected) {
      assertThat(code.getHttpStatus()).isEqualTo(expected);
    }

    @Test
    @DisplayName("默认 400")
    void should_default_to_400() {
      assertThat(SystemExceptionCode.PARAM_ERROR.getHttpStatus()).isEqualTo(400);
    }
  }

  @Nested
  @DisplayName("异常分类")
  class Category {

    @Test
    @DisplayName("system 前缀归为 SYSTEM 分类")
    void should_be_system_category() {
      assertThat(SystemExceptionCode.CONFIG_NOT_FOUND.getCategory().name())
          .isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("全部异常码均以 system 开头，归为 SYSTEM 分类")
    void all_codes_are_system_category() {
      for (SystemExceptionCode code : SystemExceptionCode.values()) {
        assertThat(code.getCategory().name()).isEqualTo("SYSTEM");
      }
    }
  }

  @Nested
  @DisplayName("actionHintKey")
  class ActionHint {

    @Test
    @DisplayName("覆写 actionHintKey 的枚举返回对应键")
    void should_return_action_hint() {
      assertThat(SystemExceptionCode.CONFIG_NOT_FOUND.actionHintKey())
          .isEqualTo("action.refresh_and_retry");
      assertThat(SystemExceptionCode.TENANT_NOT_FOUND.actionHintKey())
          .isEqualTo("action.contact_admin");
      assertThat(SystemExceptionCode.CONFIG_KEY_DUPLICATE.actionHintKey())
          .isEqualTo("action.modify_and_retry");
    }

    @Test
    @DisplayName("未覆写时返回 null")
    void should_return_null_by_default() {
      assertThat(SystemExceptionCode.PARAM_ERROR.actionHintKey()).isNull();
    }
  }

  @Nested
  @DisplayName("可恢复性")
  class Retryable {

    @Test
    @DisplayName("默认不可恢复")
    void should_not_be_retryable_by_default() {
      assertThat(SystemExceptionCode.CONFIG_NOT_FOUND.retryable()).isFalse();
      assertThat(SystemExceptionCode.CONFIG_NOT_FOUND.retryAfterSeconds()).isZero();
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("至少 25 个异常码")
    void should_have_many_codes() {
      assertThat(SystemExceptionCode.values().length).isGreaterThanOrEqualTo(25);
    }
  }
}
