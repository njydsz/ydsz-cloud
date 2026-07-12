package com.njydsz.pmis.common.exception.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProblemDetail 单元测试
 *
 * <p>覆盖 Builder 构建、工厂方法、@JsonInclude(NON_NULL) 行为、
 * equals/hashCode、extensions 扩展字段以及 RFC 7807 规范字段验证。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@DisplayName("ProblemDetail - RFC 7807 错误详情模型测试")
class ProblemDetailTest {

    // ==================== Builder 构建 ====================

    @Test
    @DisplayName("Builder 应正确构建所有字段")
    void shouldBuildAllFieldsViaBuilder() {
        Instant now = Instant.now();
        Map<String, Object> ext = new HashMap<>();
        ext.put("reason", "invalid");

        ProblemDetail pd = ProblemDetail.builder()
                .type(URI.create("https://example.com/errors/validation"))
                .title("Validation Failed")
                .status(400)
                .detail("参数校验失败")
                .instance(URI.create("/api/v1/users"))
                .traceId("trace-001")
                .requestId("req-001")
                .timestamp(now)
                .errorCode("A01052")
                .extensions(ext)
                .build();

        assertEquals(URI.create("https://example.com/errors/validation"), pd.getType());
        assertEquals("Validation Failed", pd.getTitle());
        assertEquals(400, pd.getStatus());
        assertEquals("参数校验失败", pd.getDetail());
        assertEquals(URI.create("/api/v1/users"), pd.getInstance());
        assertEquals("trace-001", pd.getTraceId());
        assertEquals("req-001", pd.getRequestId());
        assertEquals(now, pd.getTimestamp());
        assertEquals("A01052", pd.getErrorCode());
        assertEquals(ext, pd.getExtensions());
    }

    @Test
    @DisplayName("无参构造与 Setter 应正确设置字段")
    void shouldSetFieldsViaSetter() {
        ProblemDetail pd = new ProblemDetail();
        pd.setType(URI.create("about:blank"));
        pd.setTitle("Error");
        pd.setStatus(500);
        pd.setDetail("内部错误");

        assertEquals(URI.create("about:blank"), pd.getType());
        assertEquals("Error", pd.getTitle());
        assertEquals(500, pd.getStatus());
        assertEquals("内部错误", pd.getDetail());
    }

    // ==================== 工厂方法 success() ====================

    @Test
    @DisplayName("success() 应返回 status=200、title=Success、type=about:blank 且 timestamp 非空")
    void shouldCreateSuccessInstanceWithDefaultValues() {
        ProblemDetail pd = ProblemDetail.success();

        assertEquals(200, pd.getStatus());
        assertEquals("Success", pd.getTitle());
        assertEquals(URI.create("about:blank"), pd.getType());
        assertNotNull(pd.getTimestamp());
        // 未设置的字段应为 null
        assertNull(pd.getDetail());
        assertNull(pd.getInstance());
        assertNull(pd.getTraceId());
        assertNull(pd.getRequestId());
        assertNull(pd.getErrorCode());
        assertNull(pd.getExtensions());
    }

    // ==================== 工厂方法 of(String, ...) ====================

    @Test
    @DisplayName("of(String type, ...) 应将 type 字符串转换为 URI")
    void shouldConvertStringTypeToUri() {
        ProblemDetail pd = ProblemDetail.of("https://example.com/errors/not-found",
                "Not Found", 404, "资源不存在");

        assertEquals(URI.create("https://example.com/errors/not-found"), pd.getType());
        assertEquals("Not Found", pd.getTitle());
        assertEquals(404, pd.getStatus());
        assertEquals("资源不存在", pd.getDetail());
        assertNotNull(pd.getTimestamp());
    }

    @Test
    @DisplayName("of(String type, ...) 传 null type 时 type 字段为 null")
    void shouldHandleNullStringType() {
        // 显式强转为 String 以消除方法重载歧义
        ProblemDetail pd = ProblemDetail.of((String) null, "Error", 500, "服务异常");

        assertNull(pd.getType());
        assertEquals("Error", pd.getTitle());
        assertEquals(500, pd.getStatus());
        assertEquals("服务异常", pd.getDetail());
        assertNotNull(pd.getTimestamp());
    }

    // ==================== 工厂方法 of(URI, ...) ====================

    @Test
    @DisplayName("of(URI type, ...) 应直接使用传入的 URI")
    void shouldUseProvidedUriType() {
        URI typeUri = URI.create("https://example.com/errors/forbidden");
        ProblemDetail pd = ProblemDetail.of(typeUri, "Forbidden", 403, "禁止访问");

        assertSame(typeUri, pd.getType());
        assertEquals("Forbidden", pd.getTitle());
        assertEquals(403, pd.getStatus());
        assertEquals("禁止访问", pd.getDetail());
        assertNotNull(pd.getTimestamp());
    }

    @Test
    @DisplayName("of(URI type, ...) 传 null type 时 type 字段为 null")
    void shouldHandleNullUriType() {
        ProblemDetail pd = ProblemDetail.of((URI) null, "Error", 500, "服务异常");

        assertNull(pd.getType());
        assertEquals(500, pd.getStatus());
    }

    // ==================== @JsonInclude(NON_NULL) 行为 ====================

    @Test
    @DisplayName("JSON 序列化时应忽略 null 字段（@JsonInclude(NON_NULL)）")
    void shouldExcludeNullFieldsFromJson() throws Exception {
        // 仅设置部分字段，其余字段保持 null
        ProblemDetail pd = ProblemDetail.builder()
                .type(URI.create("about:blank"))
                .title("Bad Request")
                .status(400)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(pd);

        // 已设置的字段应出现
        assertTrue(json.contains("\"type\""));
        assertTrue(json.contains("\"title\""));
        assertTrue(json.contains("\"status\""));
        // null 字段不应出现
        assertFalse(json.contains("\"detail\""));
        assertFalse(json.contains("\"instance\""));
        assertFalse(json.contains("\"traceId\""));
        assertFalse(json.contains("\"requestId\""));
        assertFalse(json.contains("\"timestamp\""));
        assertFalse(json.contains("\"errorCode\""));
        assertFalse(json.contains("\"extensions\""));
    }

    @Test
    @DisplayName("所有字段均设置时 JSON 序列化应包含全部字段")
    void shouldIncludeAllFieldsWhenAllSet() throws Exception {
        // 注册 JavaTimeModule 以支持 Instant 序列化
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        Map<String, Object> ext = new HashMap<>();
        ext.put("key", "value");

        ProblemDetail pd = ProblemDetail.builder()
                .type(URI.create("about:blank"))
                .title("Error")
                .status(500)
                .detail("内部错误")
                .instance(URI.create("/api/test"))
                .traceId("trace-1")
                .requestId("req-1")
                .timestamp(Instant.now())
                .errorCode("E001")
                .extensions(ext)
                .build();

        String json = mapper.writeValueAsString(pd);

        assertTrue(json.contains("\"type\""));
        assertTrue(json.contains("\"title\""));
        assertTrue(json.contains("\"status\""));
        assertTrue(json.contains("\"detail\""));
        assertTrue(json.contains("\"instance\""));
        assertTrue(json.contains("\"traceId\""));
        assertTrue(json.contains("\"requestId\""));
        assertTrue(json.contains("\"timestamp\""));
        assertTrue(json.contains("\"errorCode\""));
        assertTrue(json.contains("\"extensions\""));
    }

    @Test
    @DisplayName("JSON 序列化后可反序列化回 ProblemDetail")
    void shouldDeserializeFromJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        Map<String, Object> ext = new HashMap<>();
        ext.put("reason", "test");

        ProblemDetail original = ProblemDetail.builder()
                .type(URI.create("https://example.com/errors/test"))
                .title("Test Error")
                .status(422)
                .detail("测试错误")
                .instance(URI.create("/api/v1/test"))
                .traceId("trace-2")
                .requestId("req-2")
                .timestamp(Instant.parse("2026-07-07T08:00:00Z"))
                .errorCode("T001")
                .extensions(ext)
                .build();

        String json = mapper.writeValueAsString(original);
        ProblemDetail parsed = mapper.readValue(json, ProblemDetail.class);

        assertEquals(original.getType(), parsed.getType());
        assertEquals(original.getTitle(), parsed.getTitle());
        assertEquals(original.getStatus(), parsed.getStatus());
        assertEquals(original.getDetail(), parsed.getDetail());
        assertEquals(original.getInstance(), parsed.getInstance());
        assertEquals(original.getTraceId(), parsed.getTraceId());
        assertEquals(original.getRequestId(), parsed.getRequestId());
        assertEquals(original.getTimestamp(), parsed.getTimestamp());
        assertEquals(original.getErrorCode(), parsed.getErrorCode());
        assertEquals(original.getExtensions(), parsed.getExtensions());
    }

    // ==================== equals/hashCode（Lombok @Data） ====================

    @Test
    @DisplayName("相同字段值的两个对象应相等且 hashCode 一致")
    void shouldBeEqualWhenFieldsAreSame() {
        Instant now = Instant.now();
        Map<String, Object> ext = new HashMap<>();
        ext.put("k", "v");

        ProblemDetail a = ProblemDetail.builder()
                .type(URI.create("about:blank"))
                .title("Error")
                .status(400)
                .detail("err")
                .instance(URI.create("/api"))
                .traceId("t1")
                .requestId("r1")
                .timestamp(now)
                .errorCode("E1")
                .extensions(ext)
                .build();

        ProblemDetail b = ProblemDetail.builder()
                .type(URI.create("about:blank"))
                .title("Error")
                .status(400)
                .detail("err")
                .instance(URI.create("/api"))
                .traceId("t1")
                .requestId("r1")
                .timestamp(now)
                .errorCode("E1")
                .extensions(ext)
                .build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("字段不同的两个对象不应相等")
    void shouldNotBeEqualWhenFieldsDiffer() {
        ProblemDetail a = ProblemDetail.builder()
                .title("A")
                .status(400)
                .build();

        ProblemDetail b = ProblemDetail.builder()
                .title("B")
                .status(400)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("对象应与自身相等，与 null 不相等")
    void shouldBeEqualToSelfAndNotEqualToNull() {
        ProblemDetail pd = ProblemDetail.success();

        assertEquals(pd, pd);
        assertNotEquals(pd, null);
        assertNotEquals(pd, "other type");
    }

    // ==================== extensions 扩展字段 ====================

    @Test
    @DisplayName("extensions 应正确存储扩展字段")
    void shouldStoreExtensions() {
        Map<String, Object> ext = new HashMap<>();
        ext.put("field1", "value1");
        ext.put("field2", 123);
        ext.put("field3", true);

        ProblemDetail pd = ProblemDetail.builder()
                .extensions(ext)
                .build();

        assertNotNull(pd.getExtensions());
        assertEquals(3, pd.getExtensions().size());
        assertEquals("value1", pd.getExtensions().get("field1"));
        assertEquals(123, pd.getExtensions().get("field2"));
        assertEquals(true, pd.getExtensions().get("field3"));
    }

    @Test
    @DisplayName("未设置 extensions 时应为 null")
    void shouldHaveNullExtensionsWhenNotSet() {
        ProblemDetail pd = ProblemDetail.success();
        assertNull(pd.getExtensions());
    }

    // ==================== RFC 7807 规范字段验证 ====================

    @Test
    @DisplayName("type 字段应为 URI 类型（RFC 7807 规范）")
    void shouldHaveUriTypeField() {
        URI typeUri = URI.create("https://api.example.com/errors/validation-failed");
        ProblemDetail pd = ProblemDetail.of(typeUri, "Validation Failed", 400, "校验失败");

        assertNotNull(pd.getType());
        assertTrue(pd.getType() instanceof URI);
        assertEquals(typeUri, pd.getType());
    }

    @Test
    @DisplayName("instance 字段应为 URI 类型（RFC 7807 规范）")
    void shouldHaveUriInstanceField() {
        URI instanceUri = URI.create("/api/v1/users/123");
        ProblemDetail pd = ProblemDetail.builder()
                .instance(instanceUri)
                .build();

        assertNotNull(pd.getInstance());
        assertTrue(pd.getInstance() instanceof URI);
        assertEquals(instanceUri, pd.getInstance());
    }

    @Test
    @DisplayName("type 字段序列化为 URI 字符串形式")
    void shouldSerializeTypeAsUriString() throws Exception {
        ProblemDetail pd = ProblemDetail.builder()
                .type(URI.create("https://example.com/errors/not-found"))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(pd);

        assertTrue(json.contains("\"type\":\"https://example.com/errors/not-found\""));
    }

    @Test
    @DisplayName("instance 字段序列化为 URI 字符串形式")
    void shouldSerializeInstanceAsUriString() throws Exception {
        ProblemDetail pd = ProblemDetail.builder()
                .instance(URI.create("/api/v1/orders/456"))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(pd);

        assertTrue(json.contains("\"instance\":\"/api/v1/orders/456\""));
    }

    // ==================== 默认值与边界 ====================

    @Test
    @DisplayName("无参构造的对象 status 默认为 0，引用字段默认为 null")
    void shouldHaveDefaultValuesAfterNoArgsConstruction() {
        ProblemDetail pd = new ProblemDetail();

        assertEquals(0, pd.getStatus());
        assertNull(pd.getType());
        assertNull(pd.getTitle());
        assertNull(pd.getDetail());
        assertNull(pd.getInstance());
        assertNull(pd.getTraceId());
        assertNull(pd.getRequestId());
        assertNull(pd.getTimestamp());
        assertNull(pd.getErrorCode());
        assertNull(pd.getExtensions());
    }

    @Test
    @DisplayName("AllArgsConstructor 应正确构建对象")
    void shouldConstructViaAllArgsConstructor() {
        Instant now = Instant.now();
        URI type = URI.create("about:blank");
        URI instance = URI.create("/api/test");
        Map<String, Object> ext = new HashMap<>();

        ProblemDetail pd = new ProblemDetail(type, "Title", 404, "Detail",
                instance, "trace", "req", now, "E001", ext);

        assertEquals(type, pd.getType());
        assertEquals("Title", pd.getTitle());
        assertEquals(404, pd.getStatus());
        assertEquals("Detail", pd.getDetail());
        assertEquals(instance, pd.getInstance());
        assertEquals("trace", pd.getTraceId());
        assertEquals("req", pd.getRequestId());
        assertEquals(now, pd.getTimestamp());
        assertEquals("E001", pd.getErrorCode());
        assertSame(ext, pd.getExtensions());
    }
}
