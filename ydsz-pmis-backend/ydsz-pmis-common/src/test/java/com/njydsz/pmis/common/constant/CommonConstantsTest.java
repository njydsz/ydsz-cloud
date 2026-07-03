package com.njydsz.pmis.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommonConstants 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("CommonConstants 测试")
class CommonConstantsTest {

    @Test
    @DisplayName("字符集常量 - 应等于 UTF-8")
    void defaultCharset_shouldBeUtf8() {
        assertEquals("UTF-8", CommonConstants.DEFAULT_CHARSET);
    }

    @Test
    @DisplayName("链路追踪 Header 常量 - 应不为 null 且非空")
    void traceHeaders_shouldNotBeNullOrEmpty() {
        assertNotNull(CommonConstants.HEADER_TRACE_ID);
        assertFalse(CommonConstants.HEADER_TRACE_ID.isBlank());
        assertEquals("X-Trace-Id", CommonConstants.HEADER_TRACE_ID);

        assertNotNull(CommonConstants.MDC_TRACE_ID);
        assertFalse(CommonConstants.MDC_TRACE_ID.isBlank());
        assertEquals("traceId", CommonConstants.MDC_TRACE_ID);
    }

    @Test
    @DisplayName("用户 Header 常量 - 应不为 null 且非空")
    void userHeaders_shouldNotBeNullOrEmpty() {
        assertNotNull(CommonConstants.HEADER_USER_ID);
        assertEquals("X-User-Id", CommonConstants.HEADER_USER_ID);

        assertNotNull(CommonConstants.HEADER_USERNAME);
        assertEquals("X-Username", CommonConstants.HEADER_USERNAME);

        assertNotNull(CommonConstants.HEADER_USER_DEPT);
        assertEquals("X-User-Dept-Id", CommonConstants.HEADER_USER_DEPT);
    }

    @Test
    @DisplayName("默认密码 - 应等于 admin123")
    void defaultPassword_shouldBeAdmin123() {
        assertEquals("admin123", CommonConstants.DEFAULT_PASSWORD);
    }

    @Test
    @DisplayName("逻辑删除常量 - NOT_DELETED=0, DELETED=1")
    void deleteConstants_shouldHaveCorrectValues() {
        assertEquals(0, CommonConstants.NOT_DELETED);
        assertEquals(1, CommonConstants.DELETED);
    }

    @Test
    @DisplayName("业务状态常量 - 应不为 null 且非空")
    void statusConstants_shouldNotBeNullOrEmpty() {
        assertEquals("ENABLED", CommonConstants.STATUS_ENABLED);
        assertEquals("DISABLED", CommonConstants.STATUS_DISABLED);
        assertEquals("DRAFT", CommonConstants.STATUS_DRAFT);
        assertEquals("ACTIVE", CommonConstants.STATUS_ACTIVE);
        assertEquals("FINISHED", CommonConstants.STATUS_FINISHED);
    }
}