package com.njydsz.pmis.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Result} 统一响应封装测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("Result 统一响应封装测试")
class ResultTest {

    @Nested
    @DisplayName("成功响应")
    class OkTest {

        @Test
        @DisplayName("ok() 返回 code=0 的成功响应")
        void shouldReturnSuccessWithZeroCode() {
            Result<Void> result = Result.ok();
            assertEquals(Result.CODE_SUCCESS, result.getCode());
            assertEquals("ok", result.getMessage());
            assertNull(result.getData());
        }

        @Test
        @DisplayName("ok(data) 返回带数据的成功响应")
        void shouldReturnSuccessWithData() {
            Result<String> result = Result.ok("hello");
            assertEquals(0, result.getCode());
            assertEquals("hello", result.getData());
        }

        @Test
        @DisplayName("ok(data, message) 返回自定义消息的成功响应")
        void shouldReturnSuccessWithCustomMessage() {
            Result<String> result = Result.ok("data", "操作成功");
            assertEquals(0, result.getCode());
            assertEquals("data", result.getData());
            assertEquals("操作成功", result.getMessage());
        }
    }

    @Nested
    @DisplayName("失败响应")
    class FailedTest {

        @Test
        @DisplayName("failed(code, message) 返回指定错误码和消息")
        void shouldReturnFailedWithCodeAndMessage() {
            Result<Void> result = Result.failed(400, "参数错误");
            assertEquals(400, result.getCode());
            assertEquals("参数错误", result.getMessage());
        }

        @Test
        @DisplayName("fail(message) 等价于 failed(-1, message)")
        void shouldReturnFailWithDefaultCode() {
            Result<Void> result = Result.fail("出错了");
            assertEquals(-1, result.getCode());
            assertEquals("出错了", result.getMessage());
        }

        @Test
        @DisplayName("failed(BizErrorCode) 从错误码枚举构建")
        void shouldReturnFailedFromBizErrorCode() {
            Result<Void> result = Result.failed(BizErrorCode.UNAUTHORIZED);
            assertEquals(BizErrorCode.UNAUTHORIZED.getCode(), result.getCode());
            assertEquals(BizErrorCode.UNAUTHORIZED.getMessage(), result.getMessage());
        }

        @Test
        @DisplayName("failed(BizErrorCode, message) 覆盖错误码的消息")
        void shouldReturnFailedWithCustomMessage() {
            Result<Void> result = Result.failed(BizErrorCode.UNAUTHORIZED, "自定义消息");
            assertEquals(BizErrorCode.UNAUTHORIZED.getCode(), result.getCode());
            assertEquals("自定义消息", result.getMessage());
        }
    }

    @Nested
    @DisplayName("工具方法")
    class UtilityTest {

        @Test
        @DisplayName("isSuccess() 对成功响应返回 true")
        void shouldReturnTrueForSuccess() {
            assertTrue(Result.ok().isSuccess());
            assertTrue(Result.ok("data").isSuccess());
        }

        @Test
        @DisplayName("isSuccess() 对失败响应返回 false")
        void shouldReturnFalseForFailed() {
            assertFalse(Result.failed(400, "error").isSuccess());
            assertFalse(Result.fail("error").isSuccess());
        }

        @Test
        @DisplayName("traceId 可设置和读取")
        void shouldSetAndGetTraceId() {
            Result<Void> result = Result.ok();
            result.setTraceId("trace-abc-123");
            assertEquals("trace-abc-123", result.getTraceId());
        }

        @Test
        @DisplayName("timestamp 自动设置")
        void shouldAutoSetTimestamp() {
            long before = System.currentTimeMillis();
            Result<Void> result = Result.ok();
            long after = System.currentTimeMillis();

            assertTrue(result.getTimestamp() >= before && result.getTimestamp() <= after);
        }
    }

    @Nested
    @DisplayName("BizErrorCode 错误码")
    class BizErrorCodeTest {

        @Test
        @DisplayName("每个错误码的 code 值唯一")
        void shouldHaveUniqueCodes() {
            java.util.Set<Integer> codes = new java.util.HashSet<>();
            for (BizErrorCode ec : BizErrorCode.values()) {
                assertTrue(codes.add(ec.getCode()),
                        "错误码 %d 重复: %s".formatted(ec.getCode(), ec.name()));
            }
        }

        @Test
        @DisplayName("getMessageKey 返回 error. + 枚举名")
        void shouldReturnCorrectMessageKey() {
            assertEquals("error.UNAUTHORIZED", BizErrorCode.UNAUTHORIZED.getMessageKey());
            assertEquals("error.BAD_REQUEST", BizErrorCode.BAD_REQUEST.getMessageKey());
        }

        @Test
        @DisplayName("getHttpStatus 认证类错误返回 401")
        void shouldReturn401ForAuthErrors() {
            assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    BizErrorCode.UNAUTHORIZED.getHttpStatus());
            assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    BizErrorCode.TOKEN_EXPIRED.getHttpStatus());
            assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    BizErrorCode.TOKEN_INVALID.getHttpStatus());
        }

        @Test
        @DisplayName("getHttpStatus 权限类错误返回 403")
        void shouldReturn403ForForbiddenErrors() {
            assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
                    BizErrorCode.FORBIDDEN.getHttpStatus());
            assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
                    BizErrorCode.DATA_SCOPE_FORBIDDEN.getHttpStatus());
        }

        @Test
        @DisplayName("getHttpStatus 资源不存在返回 404")
        void shouldReturn404ForNotFound() {
            assertEquals(org.springframework.http.HttpStatus.NOT_FOUND,
                    BizErrorCode.NOT_FOUND.getHttpStatus());
            assertEquals(org.springframework.http.HttpStatus.NOT_FOUND,
                    BizErrorCode.USER_NOT_FOUND.getHttpStatus());
            assertEquals(org.springframework.http.HttpStatus.NOT_FOUND,
                    BizErrorCode.PROJECT_NOT_FOUND.getHttpStatus());
        }

        @Test
        @DisplayName("getHttpStatus 限流返回 429")
        void shouldReturn429ForRateLimit() {
            assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    BizErrorCode.RATE_LIMIT.getHttpStatus());
            assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    BizErrorCode.QUOTA_EXCEEDED.getHttpStatus());
        }

        @Test
        @DisplayName("getHttpStatus 系统错误返回 500")
        void shouldReturn500ForInternalError() {
            assertEquals(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    BizErrorCode.INTERNAL_ERROR.getHttpStatus());
            assertEquals(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    BizErrorCode.UNKNOWN.getHttpStatus());
        }

        @Test
        @DisplayName("OK 错误码的 HTTP 状态为 200")
        void shouldReturn200ForOK() {
            assertEquals(org.springframework.http.HttpStatus.OK,
                    BizErrorCode.OK.getHttpStatus());
        }
    }
}
