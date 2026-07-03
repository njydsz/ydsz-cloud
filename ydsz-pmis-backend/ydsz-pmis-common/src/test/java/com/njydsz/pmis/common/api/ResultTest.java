package com.njydsz.pmis.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("Result 测试")
class ResultTest {

    // ==================== ok() ====================

    @Test
    @DisplayName("ok() - 无参数应返回 code=0, message=ok, data=null")
    void ok_noArg_shouldReturnSuccessWithNullData() {
        Result<String> result = Result.ok();
        assertEquals(Result.CODE_SUCCESS, result.getCode());
        assertEquals("ok", result.getMessage());
        assertNull(result.getData());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("ok(data) - 带数据应返回 code=0, message=ok")
    void ok_withData_shouldReturnSuccess() {
        Result<String> result = Result.ok("hello");
        assertEquals(Result.CODE_SUCCESS, result.getCode());
        assertEquals("ok", result.getMessage());
        assertEquals("hello", result.getData());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("ok(data, message) - 带数据和自定义消息")
    void ok_withDataAndMessage_shouldReturnSuccess() {
        Result<Integer> result = Result.ok(42, "操作成功");
        assertEquals(Result.CODE_SUCCESS, result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertEquals(42, result.getData());
        assertTrue(result.isSuccess());
    }

    // ==================== failed(int, String) ====================

    @Test
    @DisplayName("failed(code, message) - 应返回指定 code 和 message")
    void failed_withCodeAndMessage_shouldReturnFailure() {
        Result<String> result = Result.failed(10001, "参数错误");
        assertEquals(10001, result.getCode());
        assertEquals("参数错误", result.getMessage());
        assertNull(result.getData());
        assertFalse(result.isSuccess());
    }

    // ==================== fail(String) ====================

    @Test
    @DisplayName("fail(message) - 应返回 code=-1")
    void fail_withMessage_shouldReturnNegativeOneCode() {
        Result<String> result = Result.fail("操作失败");
        assertEquals(-1, result.getCode());
        assertEquals("操作失败", result.getMessage());
        assertFalse(result.isSuccess());
    }

    // ==================== failed(BizErrorCode) ====================

    @Test
    @DisplayName("failed(BizErrorCode) - 应使用错误码的 code 和 message")
    void failed_withBizErrorCode_shouldUseErrorCode() {
        Result<String> result = Result.failed(BizErrorCode.BAD_REQUEST);
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), result.getCode());
        assertEquals(BizErrorCode.BAD_REQUEST.getMessage(), result.getMessage());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("failed(BizErrorCode) - 所有枚举值均应正确")
    void failed_withAllBizErrorCodes_shouldWork() {
        for (BizErrorCode errorCode : BizErrorCode.values()) {
            Result<String> result = Result.failed(errorCode);
            assertEquals(errorCode.getCode(), result.getCode());
            assertEquals(errorCode.getMessage(), result.getMessage());
        }
    }

    // ==================== failed(BizErrorCode, String) ====================

    @Test
    @DisplayName("failed(BizErrorCode, message) - 应使用错误码的 code 和自定义 message")
    void failed_withBizErrorCodeAndMessage_shouldOverrideMessage() {
        Result<String> result = Result.failed(BizErrorCode.BAD_REQUEST, "自定义参数错误");
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), result.getCode());
        assertEquals("自定义参数错误", result.getMessage());
        assertFalse(result.isSuccess());
    }

    // ==================== isSuccess ====================

    @Test
    @DisplayName("isSuccess - code=0 应返回 true")
    void isSuccess_shouldReturnTrueForCodeZero() {
        Result<String> result = new Result<>();
        result.setCode(0);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("isSuccess - code!=0 应返回 false")
    void isSuccess_shouldReturnFalseForNonZeroCode() {
        Result<String> result = new Result<>();
        result.setCode(1);
        assertFalse(result.isSuccess());

        result.setCode(-1);
        assertFalse(result.isSuccess());
    }

    // ==================== timestamp ====================

    @Test
    @DisplayName("timestamp - 新创建的 Result 应自动设置时间戳")
    void timestamp_shouldBeAutomaticallySet() {
        Result<String> result = new Result<>();
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    @DisplayName("timestamp - ok() 或其他工厂方法创建的 Result 应有时间戳")
    void timestamp_shouldBeSetInFactoryMethods() {
        Result<String> result = Result.ok();
        assertTrue(result.getTimestamp() > 0);

        result = Result.failed(500, "error");
        assertTrue(result.getTimestamp() > 0);
    }

    // ==================== 泛型 ====================

    @Test
    @DisplayName("泛型 - 不同数据类型应正确")
    void generic_shouldWorkWithDifferentTypes() {
        Result<Integer> intResult = Result.ok(123);
        assertEquals(123, intResult.getData());

        Result<String> strResult = Result.ok("test");
        assertEquals("test", strResult.getData());

        Result<Object> objResult = Result.ok();
        assertNull(objResult.getData());
    }

    // ==================== CODE_SUCCESS 常量 ====================

    @Test
    @DisplayName("CODE_SUCCESS 常量应为 0")
    void codeSuccess_shouldBeZero() {
        assertEquals(0, Result.CODE_SUCCESS);
    }

    // ==================== setter/getter ====================

    @Test
    @DisplayName("setTraceId - 应正确设置和获取 traceId")
    void setTraceId_shouldWork() {
        Result<String> result = Result.ok();
        result.setTraceId("trace-123");
        assertEquals("trace-123", result.getTraceId());
    }
}