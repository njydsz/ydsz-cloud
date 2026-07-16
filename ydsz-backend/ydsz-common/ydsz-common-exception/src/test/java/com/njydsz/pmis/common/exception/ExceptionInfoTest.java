package com.njydsz.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.core.ExceptionInfo;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ExceptionInfo} 单元测试
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@DisplayName("ExceptionInfo 异常响应信息测试")
class ExceptionInfoTest {

    @Test
    @DisplayName("默认构造函数初始化时间戳")
    void testDefaultConstructor() {
        ExceptionInfo info = new ExceptionInfo();
        assertNotNull(info.getTimestamp());
        assertTrue(info.getTimestamp().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    @DisplayName("全参数构造函数正确赋值")
    void testFullConstructor() {
        ExceptionInfo info = new ExceptionInfo("A01052", "param.error", "参数错误", 400);
        assertEquals("A01052", info.getCode());
        assertEquals("param.error", info.getKey());
        assertEquals("参数错误", info.getMessage());
        assertEquals(400, info.getHttpStatus());
    }

    @Test
    @DisplayName("of(ExceptionCode) 工厂方法")
    void testOfExceptionCode() {
        ExceptionInfo info = ExceptionInfo.of(
                com.njydsz.common.exception.code.UnifiedExceptionCode.PARAM_ERROR);
        assertEquals("A01052", info.getCode());
        assertEquals("param.error", info.getKey());
    }

    @Test
    @DisplayName("of(String, String) 工厂方法")
    void testOfCodeMessage() {
        ExceptionInfo info = ExceptionInfo.of("CUSTOM_CODE", "自定义消息");
        assertEquals("CUSTOM_CODE", info.getCode());
        assertEquals("自定义消息", info.getMessage());
        assertNull(info.getKey());
    }

    @Test
    @DisplayName("Builder 模式构建完整 ExceptionInfo")
    void testBuilder() {
        ExceptionInfo info = ExceptionInfo.builder()
                .code("B01051")
                .key("internal.error")
                .message("系统内部错误")
                .httpStatus(500)
                .path("/api/v1/users")
                .traceId("trace-123")
                .detail("stackTrace", "java.lang.NullPointerException...")
                .build();

        assertEquals("B01051", info.getCode());
        assertEquals("internal.error", info.getKey());
        assertEquals("系统内部错误", info.getMessage());
        assertEquals(500, info.getHttpStatus());
        assertEquals("/api/v1/users", info.getPath());
        assertEquals("trace-123", info.getTraceId());
        assertNotNull(info.getDetails());
        assertTrue(info.getDetails().containsKey("stackTrace"));
    }

    @Test
    @DisplayName("setter 方法链式调用")
    void testSetters() {
        ExceptionInfo info = new ExceptionInfo();
        info.setCode("TEST");
        info.setKey("test.key");
        info.setMessage("测试消息");
        info.setHttpStatus(422);
        info.setPath("/test");
        info.setTraceId("trace-abc");

        assertEquals("TEST", info.getCode());
        assertEquals("test.key", info.getKey());
        assertEquals("测试消息", info.getMessage());
        assertEquals(422, info.getHttpStatus());
        assertEquals("/test", info.getPath());
        assertEquals("trace-abc", info.getTraceId());
    }
}
