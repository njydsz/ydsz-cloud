package com.njydsz.pmis.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.model.ProblemDetail;

import java.net.URI;

/**
 * {@link ProblemDetail} 和 {@link UnifiedExceptionCode} 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@DisplayName("ProblemDetail 与 UnifiedExceptionCode 测试")
class ProblemDetailAndErrorCodeTest {

    @Nested
    @DisplayName("ProblemDetail RFC 7807")
    class ProblemDetailTest {

        @Test
        @DisplayName("builder() 构建完整 ProblemDetail")
        void testBuilder() {
            ProblemDetail pd = ProblemDetail.builder()
                    .type(URI.create("https://pmis.njydsz.com/errors/business"))
                    .title("Business Error")
                    .status(400)
                    .detail("用户不存在")
                    .instance(URI.create("/api/v1/users/123"))
                    .traceId("trace-abc")
                    .errorCode("A01057")
                    .build();

            assertEquals("https://pmis.njydsz.com/errors/business", pd.getType().toString());
            assertEquals("Business Error", pd.getTitle());
            assertEquals(400, pd.getStatus());
            assertEquals("用户不存在", pd.getDetail());
            assertEquals("/api/v1/users/123", pd.getInstance().toString());
            assertEquals("trace-abc", pd.getTraceId());
            assertEquals("A01057", pd.getErrorCode());
            assertNotNull(pd.getTimestamp());
        }

        @Test
        @DisplayName("success() 工厂方法返回 status=200")
        void testSuccessFactory() {
            ProblemDetail pd = ProblemDetail.success();
            assertEquals(200, pd.getStatus());
            assertEquals("Success", pd.getTitle());
            assertNotNull(pd.getTimestamp());
        }

        @Test
        @DisplayName("of(String, ...) 工厂方法")
        void testOfStringFactory() {
            ProblemDetail pd = ProblemDetail.of("about:blank", "Bad Request", 400, "参数错误");
            assertEquals(URI.create("about:blank"), pd.getType());
            assertEquals("Bad Request", pd.getTitle());
            assertEquals(400, pd.getStatus());
            assertEquals("参数错误", pd.getDetail());
        }

        @Test
        @DisplayName("of(URI, ...) 工厂方法")
        void testOfUriFactory() {
            URI type = URI.create("https://example.com/errors/not-found");
            ProblemDetail pd = ProblemDetail.of(type, "Not Found", 404, "资源不存在");
            assertEquals(type, pd.getType());
            assertEquals(404, pd.getStatus());
        }

        @Test
        @DisplayName("of(null type, ...) 不抛异常")
        void testOfNullType() {
            ProblemDetail pd = ProblemDetail.of((String) null, "Error", 500, "内部错误");
            assertNull(pd.getType());
            assertEquals(500, pd.getStatus());
        }
    }

    @Nested
    @DisplayName("UnifiedExceptionCode 错误码体系")
    class UnifiedExceptionCodeTest {

        @Test
        @DisplayName("SUCCESS 编码为 A00000 / HTTP 200")
        void testSuccessCode() {
            assertEquals("A00000", UnifiedExceptionCode.SUCCESS.getCode());
            assertEquals("success", UnifiedExceptionCode.SUCCESS.getKey());
            assertEquals(200, UnifiedExceptionCode.SUCCESS.getHttpStatus());
        }

        @Test
        @DisplayName("错误码格式：1位类型 + 2位模块 + 3位序号 = 6位")
        void testCodeFormat() {
            for (UnifiedExceptionCode code : UnifiedExceptionCode.values()) {
                assertEquals(6, code.getCode().length(),
                        "Code should be 6 chars: " + code.name() + " -> " + code.getCode());
            }
        }

        @Test
        @DisplayName("A 类错误码对应 4xx HTTP 状态码")
        void testACategoryIs4xx() {
            for (UnifiedExceptionCode code : UnifiedExceptionCode.values()) {
                if (code.getCode().startsWith("A")) {
                    assertTrue(code.getHttpStatus() >= 400 && code.getHttpStatus() < 500,
                            "A-category should be 4xx: " + code.name());
                }
            }
        }

        @Test
        @DisplayName("B 类错误码对应 5xx HTTP 状态码")
        void testBCategoryIs5xx() {
            for (UnifiedExceptionCode code : UnifiedExceptionCode.values()) {
                if (code.getCode().startsWith("B")) {
                    assertTrue(code.getHttpStatus() >= 500,
                            "B-category should be 5xx: " + code.name());
                }
            }
        }

        @Test
        @DisplayName("C 类错误码对应 401/403 HTTP 状态码")
        void testCCategoryIsAuth() {
            for (UnifiedExceptionCode code : UnifiedExceptionCode.values()) {
                if (code.getCode().startsWith("C")) {
                    assertTrue(code.getHttpStatus() == 401 || code.getHttpStatus() == 403,
                            "C-category should be 401/403: " + code.name());
                }
            }
        }

        @Test
        @DisplayName("所有 key 非空且非空白")
        void testAllKeysNonBlank() {
            for (UnifiedExceptionCode code : UnifiedExceptionCode.values()) {
                assertNotNull(code.getKey());
                assertFalse(code.getKey().isBlank(),
                        "Key should not be blank: " + code.name());
            }
        }

        @Test
        @DisplayName("所有 code 唯一（无重复）")
        void testAllCodesUnique() {
            java.util.Set<String> codes = new java.util.HashSet<>();
            for (UnifiedExceptionCode code : UnifiedExceptionCode.values()) {
                assertTrue(codes.add(code.getCode()),
                        "Duplicate code: " + code.getCode());
            }
        }
    }
}
