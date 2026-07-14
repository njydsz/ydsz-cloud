package com.njydsz.pmis.common.exception.code;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CommonErrorCode 单元测试
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
@DisplayName("CommonErrorCode 公共错误码测试")
class CommonErrorCodeTest {

    @Test
    @DisplayName("所有错误码格式应符合 PM + 模块码 + 序号")
    void getCode_shouldMatchPattern() {
        for (CommonErrorCode code : CommonErrorCode.values()) {
            assertTrue(code.getCode().startsWith("PM"),
                    "Error code should start with PM: " + code);
            assertTrue(code.getCode().length() == 7,
                    "Error code should be 7 chars: " + code);
        }
    }

    @Test
    @DisplayName("所有错误码应唯一")
    void getCode_shouldBeUnique() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (CommonErrorCode code : CommonErrorCode.values()) {
            assertTrue(codes.add(code.getCode()),
                    "Duplicate error code: " + code);
        }
    }

    @Test
    @DisplayName("所有 i18n key 应唯一")
    void getKey_shouldBeUnique() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (CommonErrorCode code : CommonErrorCode.values()) {
            assertTrue(keys.add(code.getKey()),
                    "Duplicate i18n key: " + code);
        }
    }

    @Test
    @DisplayName("HTTP 状态码应在有效范围")
    void getHttpStatus_shouldBeValid() {
        for (CommonErrorCode code : CommonErrorCode.values()) {
            int status = code.getHttpStatus();
            assertTrue(status >= 200 && status < 600,
                    "Invalid HTTP status: " + code);
        }
    }

    @Test
    @DisplayName("描述不应为空")
    void getDescription_shouldNotBeEmpty() {
        for (CommonErrorCode code : CommonErrorCode.values()) {
            assertNotNull(code.getDescription());
            assertFalse(code.getDescription().isBlank(),
                    "Empty description: " + code);
        }
    }

    @Test
    @DisplayName("特定错误码验证")
    void specificCodes() {
        assertEquals(401, CommonErrorCode.UNAUTHORIZED.getHttpStatus());
        assertEquals(403, CommonErrorCode.FORBIDDEN.getHttpStatus());
        assertEquals(404, CommonErrorCode.RESOURCE_NOT_FOUND.getHttpStatus());
        assertEquals(429, CommonErrorCode.RATE_LIMITED.getHttpStatus());
        assertEquals(500, CommonErrorCode.INTERNAL_ERROR.getHttpStatus());
        assertEquals(503, CommonErrorCode.CIRCUIT_BREAKER_OPEN.getHttpStatus());
    }
}
