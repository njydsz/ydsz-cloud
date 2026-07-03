package com.njydsz.pmis.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BizErrorCode 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("BizErrorCode 测试")
class BizErrorCodeTest {

    // ==================== 基本属性 ====================

    @Test
    @DisplayName("所有枚举值都应有非空 code 和 message")
    void allEnumValues_shouldHaveNonEmptyCodeAndMessage() {
        for (BizErrorCode errorCode : BizErrorCode.values()) {
            assertNotNull(errorCode.getCode(), errorCode.name() + " 的 code 不应为 null");
            assertNotNull(errorCode.getMessage(), errorCode.name() + " 的 message 不应为 null");
            assertFalse(errorCode.getMessage().isBlank(), errorCode.name() + " 的 message 不应为空");
        }
    }

    @Test
    @DisplayName("OK - code 应为 0，message 应为 ok")
    void ok_shouldHaveCorrectValues() {
        assertEquals(0, BizErrorCode.OK.getCode());
        assertEquals("ok", BizErrorCode.OK.getMessage());
    }

    @Test
    @DisplayName("INTERNAL_ERROR - code 应为 10201")
    void internalError_shouldHaveCorrectCode() {
        assertEquals(10201, BizErrorCode.INTERNAL_ERROR.getCode());
        assertEquals("系统内部错误", BizErrorCode.INTERNAL_ERROR.getMessage());
    }

    @Test
    @DisplayName("BAD_REQUEST - code 应为 10001")
    void badRequest_shouldHaveCorrectCode() {
        assertEquals(10001, BizErrorCode.BAD_REQUEST.getCode());
        assertEquals("请求参数错误", BizErrorCode.BAD_REQUEST.getMessage());
    }

    @Test
    @DisplayName("UNAUTHORIZED - code 应为 20001")
    void unauthorized_shouldHaveCorrectCode() {
        assertEquals(20001, BizErrorCode.UNAUTHORIZED.getCode());
        assertEquals("未登录", BizErrorCode.UNAUTHORIZED.getMessage());
    }

    // ==================== getMessageKey ====================

    @Test
    @DisplayName("getMessageKey - 应返回 error.ENUM_NAME 格式")
    void getMessageKey_shouldReturnCorrectFormat() {
        assertEquals("error.OK", BizErrorCode.OK.getMessageKey());
        assertEquals("error.BAD_REQUEST", BizErrorCode.BAD_REQUEST.getMessageKey());
        assertEquals("error.INTERNAL_ERROR", BizErrorCode.INTERNAL_ERROR.getMessageKey());
        assertEquals("error.UNAUTHORIZED", BizErrorCode.UNAUTHORIZED.getMessageKey());
    }

    @Test
    @DisplayName("getMessageKey - 所有枚举值都应返回非空 key")
    void getMessageKey_shouldReturnNonEmptyForAll() {
        for (BizErrorCode errorCode : BizErrorCode.values()) {
            String key = errorCode.getMessageKey();
            assertNotNull(key, errorCode.name() + " 的 messageKey 不应为 null");
            assertTrue(key.startsWith("error."), errorCode.name() + " 的 messageKey 应以 error. 开头");
        }
    }

    // ==================== 错误码段位 ====================

    @Test
    @DisplayName("1xxxx 段位应包含通用错误")
    void segment1xxxx_shouldContainCommonErrors() {
        assertTrue(BizErrorCode.BAD_REQUEST.getCode() >= 10000 && BizErrorCode.BAD_REQUEST.getCode() < 20000);
        assertTrue(BizErrorCode.INTERNAL_ERROR.getCode() >= 10000 && BizErrorCode.INTERNAL_ERROR.getCode() < 20000);
    }

    @Test
    @DisplayName("2xxxx 段位应包含认证授权错误")
    void segment2xxxx_shouldContainAuthErrors() {
        assertTrue(BizErrorCode.UNAUTHORIZED.getCode() >= 20000 && BizErrorCode.UNAUTHORIZED.getCode() < 30000);
        assertTrue(BizErrorCode.FORBIDDEN.getCode() >= 20000 && BizErrorCode.FORBIDDEN.getCode() < 30000);
    }

    @Test
    @DisplayName("9xxxx 段位应包含 UNKNOWN 错误")
    void segment9xxxx_shouldContainUnknownError() {
        assertEquals(99999, BizErrorCode.UNKNOWN.getCode());
    }

    // ==================== 枚举方法 ====================

    @Test
    @DisplayName("values() - 应返回超过 30 个枚举值")
    void values_shouldReturnManyValues() {
        assertTrue(BizErrorCode.values().length > 30, "应有超过 30 个错误码");
    }

    @Test
    @DisplayName("valueOf - 应能通过名称获取枚举值")
    void valueOf_shouldWork() {
        assertEquals(BizErrorCode.OK, BizErrorCode.valueOf("OK"));
        assertEquals(BizErrorCode.BAD_REQUEST, BizErrorCode.valueOf("BAD_REQUEST"));
    }
}