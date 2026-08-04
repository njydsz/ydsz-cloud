package com.remisoft.common.core.code;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ResultCode} 契约测试
 *
 * <p>覆盖：实现类必须显式声明 HTTP 状态码（禁止前缀猜测）、
 * messageKey 默认实现、BaseResultCode 数据驱动映射等行为。
 *
 * @author remi-team
 * @since 1.2.0
 */
@DisplayName("ResultCode 接口契约测试")
class ResultCodeTest {

    @Test
    @DisplayName("实现类必须显式声明 HTTP 状态码")
    void explicitHttpStatusRequired() {
        // TestCode 为每个枚举显式声明了 httpStatus，不依赖前缀推断
        assertEquals(400, TestCode.BAD_REQUEST.getHttpStatusCode());
        assertEquals(429, TestCode.RATE_LIMIT.getHttpStatusCode());
        assertEquals(404, TestCode.NOT_FOUND.getHttpStatusCode());
        assertEquals(200, TestCode.SUCCESS.getHttpStatusCode());
    }

    @Test
    @DisplayName("getMessageKey 默认返回 error.枚举名")
    void messageKey() {
        assertEquals("error.BAD_REQUEST", TestCode.BAD_REQUEST.getMessageKey());
        assertEquals("error.RATE_LIMIT", TestCode.RATE_LIMIT.getMessageKey());
    }

    @Test
    @DisplayName("BaseResultCode 全部枚举显式声明 HTTP 状态码")
    void baseResultCodeExplicitMapping() {
        // 抽查常见错误码的精确映射（覆盖此前前缀推断会出错的场景）
        assertEquals(400, BaseResultCode.BAD_REQUEST.getHttpStatusCode());
        assertEquals(404, BaseResultCode.NOT_FOUND.getHttpStatusCode());
        assertEquals(409, BaseResultCode.DUPLICATE_KEY.getHttpStatusCode());
        assertEquals(429, BaseResultCode.RATE_LIMIT.getHttpStatusCode());
        assertEquals(401, BaseResultCode.UNAUTHORIZED.getHttpStatusCode());
        assertEquals(403, BaseResultCode.FORBIDDEN.getHttpStatusCode());
        assertEquals(500, BaseResultCode.INTERNAL_ERROR.getHttpStatusCode());
        assertEquals(503, BaseResultCode.SERVICE_UNAVAILABLE.getHttpStatusCode());
        assertEquals(200, BaseResultCode.SUCCESS.getHttpStatusCode());
    }

    @Test
    @DisplayName("BaseResultCode 每个枚举的 HTTP 状态码与其段位语义一致")
    void baseResultCodeSegmentConsistency() {
        // A2 段位（认证授权）应映射 401/403/423，而非统一 400
        assertTrue(BaseResultCode.UNAUTHORIZED.getHttpStatusCode() >= 401);
        assertTrue(BaseResultCode.TOKEN_EXPIRED.getHttpStatusCode() >= 401);
        assertTrue(BaseResultCode.ACCOUNT_LOCKED.getHttpStatusCode() >= 423);
        // C 段位（第三方服务）应为 5xx
        assertTrue(BaseResultCode.THIRD_PARTY_SERVICE_ERROR.getHttpStatusCode() >= 500);
        assertTrue(BaseResultCode.MQ_PUBLISH_FAILED.getHttpStatusCode() >= 500);
    }

    /**
     * 测试用 ResultCode 实现（显式声明 HTTP 状态码，验证强制契约）。
     */
    private enum TestCode implements ResultCode {
        SUCCESS("A00000", 200),
        BAD_REQUEST("A10001", 400),
        NOT_FOUND("A10101", 404),
        RATE_LIMIT("A10301", 429),
        UNAUTHORIZED("A20001", 401);

        private final String code;
        private final int httpStatus;

        TestCode(String code, int httpStatus) {
            this.code = code;
            this.httpStatus = httpStatus;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMsg() {
            return "test";
        }

        @Override
        public int getHttpStatusCode() {
            return httpStatus;
        }
    }
}
