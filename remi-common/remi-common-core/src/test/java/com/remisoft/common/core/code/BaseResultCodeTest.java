package com.remisoft.common.core.code;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link BaseResultCode} 单元测试
 *
 * <p>覆盖全部枚举值的 code 唯一性、消息非空、HTTP 状态码语义映射、
 * getMessageKey 默认实现、Segment 规划一致性等行为。
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("BaseResultCode 标准结果码测试")
class BaseResultCodeTest {

    @Test
    @DisplayName("SUCCESS 使用 A00000")
    void successCode() {
        assertEquals("A00000", BaseResultCode.SUCCESS.getCode());
        assertEquals(200, BaseResultCode.SUCCESS.getHttpStatusCode());
    }

    @Test
    @DisplayName("所有 code 唯一且非空")
    void codes_uniqueAndNonBlank() {
        Set<String> codes = new HashSet<>();
        for (BaseResultCode rc : BaseResultCode.values()) {
            assertNotNull(rc.getCode(), "code must not be null: " + rc.name());
            assertFalse(rc.getCode().isBlank(), "code must not be blank: " + rc.name());
            assertTrue(codes.add(rc.getCode()), "duplicate code: " + rc.getCode());
        }
        // 47 个枚举值 = SUCCESS(1) + 错误码(46)（已移除 B3xxxx/B7xxxx 业务模块错误码）
        // 注意：新增枚举值时必须同步更新此断言
        assertEquals(47, BaseResultCode.values().length);
    }

    @Test
    @DisplayName("所有消息非空")
    void messages_nonBlank() {
        for (BaseResultCode rc : BaseResultCode.values()) {
            assertNotNull(rc.getMsg(), "msg must not be null: " + rc.name());
            assertFalse(rc.getMsg().isBlank(), "msg must not be blank: " + rc.name());
        }
    }

    @Test
    @DisplayName("getMessageKey 默认返回 error.枚举名")
    void messageKey_default() {
        assertEquals("error.SUCCESS", BaseResultCode.SUCCESS.getMessageKey());
        assertEquals("error.NOT_FOUND", BaseResultCode.NOT_FOUND.getMessageKey());
    }

    @Test
    @DisplayName("code 前缀符合 Segment 规划（A/B/C）")
    void codeSegments() {
        for (BaseResultCode rc : BaseResultCode.values()) {
            char prefix = rc.getCode().charAt(0);
            assertTrue(prefix == 'A' || prefix == 'B' || prefix == 'C',
                    "code must start with A/B/C: " + rc.getCode());
        }
    }

    @Test
    @DisplayName("HTTP 状态码在合法范围（2xx/4xx/5xx）")
    void httpStatusInValidRange() {
        for (BaseResultCode rc : BaseResultCode.values()) {
            int status = rc.getHttpStatusCode();
            assertTrue(status >= 200 && status < 600,
                    "invalid http status " + status + " for " + rc.getCode());
            assertTrue(status < 300 || status >= 400,
                    "status must be 2xx or 4xx/5xx: " + status + " for " + rc.getCode());
        }
    }

    @Test
    @DisplayName("参数校验类错误映射到 400")
    void validation_400() {
        assertEquals(400, BaseResultCode.BAD_REQUEST.getHttpStatusCode());
        assertEquals(400, BaseResultCode.VALIDATION_FAILED.getHttpStatusCode());
        assertEquals(400, BaseResultCode.MISSING_PARAMETER.getHttpStatusCode());
        assertEquals(400, BaseResultCode.BIZ_ERROR.getHttpStatusCode());
        assertEquals(400, BaseResultCode.PAYLOAD_TOO_LARGE.getHttpStatusCode());
    }

    @Test
    @DisplayName("认证类错误映射到 401")
    void auth_401() {
        assertEquals(401, BaseResultCode.UNAUTHORIZED.getHttpStatusCode());
        assertEquals(401, BaseResultCode.TOKEN_EXPIRED.getHttpStatusCode());
        assertEquals(401, BaseResultCode.TOKEN_INVALID.getHttpStatusCode());
        assertEquals(401, BaseResultCode.PASSWORD_EXPIRED.getHttpStatusCode());
        assertEquals(401, BaseResultCode.MFA_REQUIRED.getHttpStatusCode());
    }

    @Test
    @DisplayName("授权类错误映射到 403")
    void forbidden_403() {
        assertEquals(403, BaseResultCode.FORBIDDEN.getHttpStatusCode());
        assertEquals(403, BaseResultCode.DATA_SCOPE_FORBIDDEN.getHttpStatusCode());
    }

    @Test
    @DisplayName("资源不存在类错误映射到 404")
    void notFound_404() {
        assertEquals(404, BaseResultCode.NOT_FOUND.getHttpStatusCode());
    }

    @Test
    @DisplayName("冲突类错误映射到 409")
    void conflict_409() {
        assertEquals(409, BaseResultCode.DUPLICATE_KEY.getHttpStatusCode());
        assertEquals(409, BaseResultCode.DB_DUPLICATE_KEY.getHttpStatusCode());
        assertEquals(409, BaseResultCode.RESOURCE_CONFLICT.getHttpStatusCode());
        assertEquals(409, BaseResultCode.RESOURCE_LOCKED.getHttpStatusCode());
    }

    @Test
    @DisplayName("限流类错误映射到 429")
    void rateLimit_429() {
        assertEquals(429, BaseResultCode.RATE_LIMIT.getHttpStatusCode());
        assertEquals(429, BaseResultCode.QUOTA_EXCEEDED.getHttpStatusCode());
        assertEquals(429, BaseResultCode.TOO_MANY_REQUESTS.getHttpStatusCode());
    }

    @Test
    @DisplayName("服务不可用类错误映射到 503")
    void unavailable_503() {
        assertEquals(503, BaseResultCode.SERVICE_UNAVAILABLE.getHttpStatusCode());
        assertEquals(503, BaseResultCode.SYSTEM_MAINTENANCE.getHttpStatusCode());
        assertEquals(503, BaseResultCode.DB_CONNECTION_FAILED.getHttpStatusCode());
        assertEquals(503, BaseResultCode.THIRD_PARTY_TIMEOUT.getHttpStatusCode());
    }

    @Test
    @DisplayName("锁定类错误映射到 423")
    void locked_423() {
        assertEquals(423, BaseResultCode.ACCOUNT_LOCKED.getHttpStatusCode());
    }

    @Test
    @DisplayName("方法不允许映射到 405，请求超时映射到 408")
    void methodAndTimeout() {
        assertEquals(405, BaseResultCode.METHOD_NOT_ALLOWED.getHttpStatusCode());
        assertEquals(408, BaseResultCode.REQUEST_TIMEOUT.getHttpStatusCode());
    }

    @Test
    @DisplayName("内部错误类映射到 500")
    void internal_500() {
        assertEquals(500, BaseResultCode.INTERNAL_ERROR.getHttpStatusCode());
        assertEquals(500, BaseResultCode.UNKNOWN.getHttpStatusCode());
        assertEquals(500, BaseResultCode.CIRCUIT_BREAKER_OPEN.getHttpStatusCode());
        assertEquals(500, BaseResultCode.MQ_PUBLISH_FAILED.getHttpStatusCode());
    }

    @Test
    @DisplayName("所有枚举值均可通过 code 反查（枚举 values 与 code 一一对应）")
    void codeToEnumBijection() {
        Map<String, BaseResultCode> map = Stream.of(BaseResultCode.values())
                .collect(Collectors.toMap(BaseResultCode::getCode, rc -> rc));
        assertEquals(BaseResultCode.values().length, map.size());
        for (BaseResultCode rc : BaseResultCode.values()) {
            assertEquals(rc, map.get(rc.getCode()));
        }
    }
}
