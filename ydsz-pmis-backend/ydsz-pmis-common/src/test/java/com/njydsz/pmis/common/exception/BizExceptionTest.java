package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.api.BizErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BizException 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("BizException 测试")
class BizExceptionTest {

    // ==================== BizException(BizErrorCode) ====================

    @Test
    @DisplayName("构造函数 - BizErrorCode 参数应正确设置 code 和 errorMessage")
    void constructor_withBizErrorCode_shouldSetFields() {
        BizException ex = new BizException(BizErrorCode.BAD_REQUEST);
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals(BizErrorCode.BAD_REQUEST.getMessage(), ex.getErrorMessage());
        assertEquals(BizErrorCode.BAD_REQUEST.getMessage(), ex.getMessage());
    }

    @Test
    @DisplayName("构造函数 - BizErrorCode OK 应正确设置")
    void constructor_withOk_shouldSetFields() {
        BizException ex = new BizException(BizErrorCode.OK);
        assertEquals(0, ex.getCode());
        assertEquals("ok", ex.getErrorMessage());
    }

    // ==================== BizException(BizErrorCode, String) ====================

    @Test
    @DisplayName("构造函数 - BizErrorCode + 自定义消息应覆盖 message")
    void constructor_withBizErrorCodeAndMessage_shouldOverrideMessage() {
        BizException ex = new BizException(BizErrorCode.BAD_REQUEST, "自定义错误信息");
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("自定义错误信息", ex.getErrorMessage());
        assertEquals("自定义错误信息", ex.getMessage());
    }

    // ==================== BizException(int, String) ====================

    @Test
    @DisplayName("构造函数 - 自定义 code 和 message")
    void constructor_withCodeAndMessage_shouldSetFields() {
        BizException ex = new BizException(999, "自定义异常");
        assertEquals(999, ex.getCode());
        assertEquals("自定义异常", ex.getErrorMessage());
        assertEquals("自定义异常", ex.getMessage());
    }

    // ==================== BizException(String) ====================

    @Test
    @DisplayName("构造函数 - 仅 message 参数，code 应为 INTERNAL_ERROR")
    void constructor_withMessageOnly_shouldUseInternalErrorCode() {
        BizException ex = new BizException("内部错误");
        assertEquals(BizErrorCode.INTERNAL_ERROR.getCode(), ex.getCode());
        assertEquals("内部错误", ex.getErrorMessage());
        assertEquals("内部错误", ex.getMessage());
    }

    // ==================== 继承关系 ====================

    @Test
    @DisplayName("BizException 应继承 RuntimeException")
    void bizException_shouldExtendRuntimeException() {
        BizException ex = new BizException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }

    // ==================== 完整 BizErrorCode 枚举覆盖 ====================

    @Test
    @DisplayName("所有 BizErrorCode 枚举值构造 BizException 均不抛异常")
    void allBizErrorCodes_shouldConstructWithoutException() {
        for (BizErrorCode errorCode : BizErrorCode.values()) {
            BizException ex = new BizException(errorCode);
            assertNotNull(ex);
            assertEquals(errorCode.getCode(), ex.getCode());
            assertEquals(errorCode.getMessage(), ex.getErrorMessage());
        }
    }

    // ==================== 边界 ====================

    @Test
    @DisplayName("BizException - 负数 code 应正确存储")
    void constructor_withNegativeCode_shouldWork() {
        BizException ex = new BizException(-1, "负数码");
        assertEquals(-1, ex.getCode());
        assertEquals("负数码", ex.getErrorMessage());
    }

    @Test
    @DisplayName("BizException - 空 message 应正确存储")
    void constructor_withEmptyMessage_shouldWork() {
        BizException ex = new BizException(500, "");
        assertEquals(500, ex.getCode());
        assertEquals("", ex.getErrorMessage());
    }

    @Test
    @DisplayName("BizException - null message 应正确存储")
    void constructor_withNullMessage_shouldWork() {
        BizException ex = new BizException(500, null);
        assertEquals(500, ex.getCode());
        assertNull(ex.getErrorMessage());
    }
}