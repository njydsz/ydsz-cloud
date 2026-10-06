package com.njydsz.system.web.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.code.CoreExceptionCode;

/**
 * MvcExceptionHandler 单元测试 — 验证错误码体系完整性（无 Spring Context，轻量快速）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>CoreExceptionCode 核心错误码存在且 code 非空</li>
 *   <li>错误码符合 B + 5 位数字格式</li>
 *   <li>枚举项 i18n key 非空</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.05
 */
@DisplayName("MvcExceptionHandler - 错误码与 ExceptionCode 体系单元测试")
class MvcExceptionHandlerTest {

  @Nested
  @DisplayName("CoreExceptionCode 核心错误码验证")
  class CoreExceptionCodeValidation {

    @Test
    @DisplayName("SYSTEM_ERROR 枚举应存在且错误码 = B01052")
    void systemError_shouldExistWithCorrectCode() {
      assertThat(CoreExceptionCode.SYSTEM_ERROR).isNotNull();
      assertThat(CoreExceptionCode.SYSTEM_ERROR.getCode()).isEqualTo("B01052");
    }

    @Test
    @DisplayName("所有 CoreExceptionCode 枚举项应具有非空 code")
    void allErrorCode_shouldHaveNonEmptyCode() {
      for (CoreExceptionCode ec : CoreExceptionCode.values()) {
        assertThat(ec.getCode())
            .as("CoreExceptionCode.%s 不应有空错误码", ec.name())
            .isNotEmpty();
      }
    }

    @Test
    @DisplayName("错误码格式应符合 字母 + 5 位数字模式（如 A01052、B01052）")
    void errorCodeFormat_shouldMatchPattern() {
      for (CoreExceptionCode ec : CoreExceptionCode.values()) {
        String code = ec.getCode();
        assertThat(code)
            .as("错误码 %s 应匹配 字母 + 5 位数字模式", code)
            .matches("[A-Z]\\d{5}");
      }
    }

    @Test
    @DisplayName("所有枚举项应具有非空 i18n key")
    void allEnumItems_shouldHaveNonEmptyI18nKey() {
      for (CoreExceptionCode ec : CoreExceptionCode.values()) {
        assertThat(ec.getKey())
            .as("CoreExceptionCode.%s 不应有空 i18n key", ec.name())
            .isNotEmpty();
      }
    }

    @Test
    @DisplayName("每个枚举项的 HTTP 状态码应在合法范围（200/207 或 400-599）")
    void httpStatus_shouldBeInValidRange() {
      for (CoreExceptionCode ec : CoreExceptionCode.values()) {
        int status = ec.getHttpStatus();
        // SUCCESS 为 200（成功）；BATCH_PARTIAL_SUCCESS 为 207（部分成功）；其余错误码应为 4xx/5xx
        if (ec == CoreExceptionCode.SUCCESS) {
          assertThat(status).as("SUCCESS HTTP 状态码应为 200").isEqualTo(200);
        } else if (ec == CoreExceptionCode.BATCH_PARTIAL_SUCCESS) {
          assertThat(status).as("BATCH_PARTIAL_SUCCESS HTTP 状态码应为 207").isEqualTo(207);
        } else {
          assertThat(status)
              .as("CoreExceptionCode.%s HTTP 状态码应在 400-599 范围内", ec.name())
              .isGreaterThanOrEqualTo(400)
              .isLessThan(600);
        }
      }
    }
  }
}
