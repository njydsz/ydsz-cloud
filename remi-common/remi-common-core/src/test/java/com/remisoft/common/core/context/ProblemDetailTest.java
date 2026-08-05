package com.remisoft.common.core.context;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.core.code.ResultCode;

/**
 * {@link ProblemDetail} 单元测试
 *
 * <p>覆盖所有工厂方法、Builder 模式、RFC 7807 字段映射、边界条件等。
 *
 * @author remi-team
 * @since 1.6.0
 */
@DisplayName("ProblemDetail RFC 7807 错误详情测试")
class ProblemDetailTest {

    @Test
    @DisplayName("of(ResultCode, detail) 使用标准结果码构建")
    void of_resultCodeAndDetail() {
        ProblemDetail problem = ProblemDetail.of(BaseResultCode.NOT_FOUND, "订单不存在");

        assertNotNull(problem);
        assertEquals(URI.create(ProblemDetail.DEFAULT_TYPE_PREFIX + "A10101"), problem.getType());
        assertEquals("资源不存在", problem.getTitle());
        assertEquals(404, problem.getStatus());
        assertEquals("订单不存在", problem.getDetail());
        assertEquals("A10101", problem.getErrorCode());
        assertNotNull(problem.getTimestamp());
        assertNull(problem.getInstance());
        assertNull(problem.getTraceId());
        assertNull(problem.getRequestId());
        assertNull(problem.getExtensions());
    }

    @Test
    @DisplayName("of(ResultCode, detail, instance) 携带请求路径")
    void of_resultCodeDetailAndInstance() {
        URI instance = URI.create("/api/v1/orders/12345");
        ProblemDetail problem = ProblemDetail.of(BaseResultCode.VALIDATION_FAILED, "字段不能为空", instance);

        assertEquals(instance, problem.getInstance());
        assertEquals(400, problem.getStatus());
        assertEquals("A10002", problem.getErrorCode());
    }

    @Test
    @DisplayName("of(String, String, int, String) 字符串类型构建")
    void of_stringType() {
        ProblemDetail problem = ProblemDetail.of(
                "https://errors.example.com/validation",
                "参数校验失败",
                400,
                "用户名不能为空");

        assertEquals(URI.create("https://errors.example.com/validation"), problem.getType());
        assertEquals("参数校验失败", problem.getTitle());
        assertEquals(400, problem.getStatus());
        assertEquals("用户名不能为空", problem.getDetail());
        assertNotNull(problem.getTimestamp());
        assertNull(problem.getErrorCode());
    }

    @Test
    @DisplayName("of(String, ...) 传入 null type 时 type 为 null")
    void of_stringType_nullType() {
        ProblemDetail problem = ProblemDetail.of((String) null, "错误", 500, "服务器错误");
        assertNull(problem.getType());
        assertEquals("错误", problem.getTitle());
        assertEquals(500, problem.getStatus());
    }

    @Test
    @DisplayName("of(URI, String, int, String) URI 类型构建")
    void of_uriType() {
        URI type = URI.create("https://errors.remi.com/custom");
        ProblemDetail problem = ProblemDetail.of(type, "自定义错误", 403, "无权限");

        assertEquals(type, problem.getType());
        assertEquals(403, problem.getStatus());
    }

    @Test
    @DisplayName("Builder 全字段构建")
    void builder_fullFields() {
        URI type = URI.create("https://errors.example.com/test");
        URI instance = URI.create("/api/test");
        Instant before = Instant.now();

        Map<String, Object> extensions = new HashMap<>();
        extensions.put("key1", "value1");
        extensions.put("key2", 42);

        ProblemDetail problem = ProblemDetail.builder()
                .type(type)
                .title("测试错误")
                .status(422)
                .detail("详情信息")
                .instance(instance)
                .traceId("trace-001")
                .requestId("req-001")
                .errorCode("E00001")
                .timestamp(Instant.now())
                .extensions(extensions)
                .build();

        assertEquals(type, problem.getType());
        assertEquals("测试错误", problem.getTitle());
        assertEquals(422, problem.getStatus());
        assertEquals("详情信息", problem.getDetail());
        assertEquals(instance, problem.getInstance());
        assertEquals("trace-001", problem.getTraceId());
        assertEquals("req-001", problem.getRequestId());
        assertEquals("E00001", problem.getErrorCode());
        assertNotNull(problem.getTimestamp());
        assertTrue(!problem.getTimestamp().isBefore(before));
        assertEquals(2, problem.getExtensions().size());
        assertEquals("value1", problem.getExtensions().get("key1"));
    }

    @Test
    @DisplayName("Builder 最小字段构建")
    void builder_minimalFields() {
        ProblemDetail problem = ProblemDetail.builder()
                .title("错误")
                .status(500)
                .build();

        assertNull(problem.getType());
        assertNull(problem.getDetail());
        assertNull(problem.getInstance());
        assertNull(problem.getTraceId());
        assertNull(problem.getRequestId());
        assertNull(problem.getErrorCode());
        assertNull(problem.getTimestamp());
        assertNull(problem.getExtensions());
    }

    @Test
    @DisplayName("扩展字段 extensions 可任意添加")
    void extensions_arbitraryData() {
        ProblemDetail problem = ProblemDetail.builder()
                .extensions(Map.of("orderId", 12345, "retryable", true))
                .build();

        assertNotNull(problem.getExtensions());
        assertEquals(12345, problem.getExtensions().get("orderId"));
        assertEquals(true, problem.getExtensions().get("retryable"));
    }

    @Test
    @DisplayName("timestamp 自动生成且非空")
    void timestamp_autoGenerated() {
        Instant before = Instant.now();
        ProblemDetail problem = ProblemDetail.of(BaseResultCode.BAD_REQUEST, "错误");
        Instant after = Instant.now();

        assertNotNull(problem.getTimestamp());
        assertTrue(!problem.getTimestamp().isBefore(before) && !problem.getTimestamp().isAfter(after),
                "timestamp 应在测试执行时间范围内");
    }

    @Test
    @DisplayName("DEFAULT_TYPE_PREFIX 常量值正确")
    void defaultTypePrefix() {
        assertEquals("https://errors.remi.com/", ProblemDetail.DEFAULT_TYPE_PREFIX);
    }

    @Test
    @DisplayName("不同 ResultCode 构建的 type URI 包含对应错误码")
    void typeUri_containsResultCode() {
        ResultCode[] codes = {
                BaseResultCode.SUCCESS,
                BaseResultCode.BAD_REQUEST,
                BaseResultCode.NOT_FOUND,
                BaseResultCode.UNAUTHORIZED,
                BaseResultCode.INTERNAL_ERROR
        };

        for (ResultCode code : codes) {
            ProblemDetail problem = ProblemDetail.of(code, "测试");
            assertTrue(problem.getType().toString().contains(code.getCode()),
                    "type URI 应包含错误码: " + code.getCode());
        }
    }

    @Test
    @DisplayName("所有 HTTP 状态码正确映射")
    void httpStatusMapping() {
        assertEquals(200, ProblemDetail.of(BaseResultCode.SUCCESS, "ok").getStatus());
        assertEquals(400, ProblemDetail.of(BaseResultCode.BAD_REQUEST, "bad").getStatus());
        assertEquals(401, ProblemDetail.of(BaseResultCode.UNAUTHORIZED, "unauth").getStatus());
        assertEquals(403, ProblemDetail.of(BaseResultCode.FORBIDDEN, "forbidden").getStatus());
        assertEquals(404, ProblemDetail.of(BaseResultCode.NOT_FOUND, "notfound").getStatus());
        assertEquals(500, ProblemDetail.of(BaseResultCode.INTERNAL_ERROR, "error").getStatus());
        assertEquals(503, ProblemDetail.of(BaseResultCode.SERVICE_UNAVAILABLE, "unavail").getStatus());
        assertEquals(429, ProblemDetail.of(BaseResultCode.RATE_LIMIT, "rate").getStatus());
    }

    @Test
    @DisplayName("实现 Serializable 接口")
    void serializable() {
        ProblemDetail problem = ProblemDetail.of(BaseResultCode.NOT_FOUND, "资源不存在");
        assertInstanceOf(java.io.Serializable.class, problem);
    }

    @Test
    @DisplayName("serialVersionUID 存在")
    void serialVersionUid() throws Exception {
        java.lang.reflect.Field field = ProblemDetail.class.getDeclaredField("serialVersionUID");
        field.setAccessible(true);
        assertEquals(1L, field.getLong(null));
    }

    @Test
    @DisplayName("Builder 支持全空字段构建（NoArgsConstructor）")
    void noArgsConstructor() {
        ProblemDetail problem = new ProblemDetail();
        assertNotNull(problem);
        assertNull(problem.getType());
        assertNull(problem.getTitle());
        assertNull(problem.getStatus());
    }

    @Test
    @DisplayName("AllArgsConstructor 支持全参数构造")
    void allArgsConstructor() {
        URI type = URI.create("https://example.com");
        URI instance = URI.create("/api/test");
        Instant now = Instant.now();

        ProblemDetail problem = new ProblemDetail(
                type, "title", 400, "detail", instance,
                "trace-1", "req-1", now, "E001", null);

        assertEquals(type, problem.getType());
        assertEquals("title", problem.getTitle());
        assertEquals(400, problem.getStatus());
        assertEquals("detail", problem.getDetail());
        assertEquals(instance, problem.getInstance());
        assertEquals("trace-1", problem.getTraceId());
        assertEquals("req-1", problem.getRequestId());
        assertEquals(now, problem.getTimestamp());
        assertEquals("E001", problem.getErrorCode());
    }
}
