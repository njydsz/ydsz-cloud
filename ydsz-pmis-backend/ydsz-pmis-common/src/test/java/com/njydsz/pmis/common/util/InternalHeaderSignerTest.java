package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InternalHeaderSigner} 单元测试
 *
 * <p>覆盖 HMAC-SHA256 签名生成与校验、防重放时间戳窗口、常量时间比较等核心安全逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("InternalHeaderSigner 内部头签名工具测试")
class InternalHeaderSignerTest {

    private static final String SECRET = "this-is-a-test-secret-key-at-least-32-bytes-long!!";

    @Test
    @DisplayName("签名后验签应通过（正常流程）")
    void shouldVerifySuccessfullyWithCorrectParams() {
        long ts = System.currentTimeMillis() / 1000;
        String sig = InternalHeaderSigner.sign(SECRET, "trace-123", "user-456",
                "admin", "READ,WRITE", "USER_LIST", ts);

        boolean valid = InternalHeaderSigner.verify(SECRET, "trace-123", "user-456",
                "admin", "READ,WRITE", "USER_LIST", sig, ts, ts);

        assertTrue(valid);
    }

    @Test
    @DisplayName("密钥不同时验签失败")
    void shouldFailWithDifferentSecret() {
        long ts = System.currentTimeMillis() / 1000;
        String sig = InternalHeaderSigner.sign(SECRET, "trace-123", "user-456",
                "admin", "READ", "USER_LIST", ts);

        boolean valid = InternalHeaderSigner.verify("another-secret-key-at-least-32-bytes!!",
                "trace-123", "user-456", "admin", "READ", "USER_LIST", sig, ts, ts);

        assertFalse(valid);
    }

    @Test
    @DisplayName("traceId 不同时验签失败")
    void shouldFailWithDifferentTraceId() {
        long ts = System.currentTimeMillis() / 1000;
        String sig = InternalHeaderSigner.sign(SECRET, "trace-123", "user-456",
                "admin", "READ", "USER_LIST", ts);

        boolean valid = InternalHeaderSigner.verify(SECRET, "trace-DIFFERENT", "user-456",
                "admin", "READ", "USER_LIST", sig, ts, ts);

        assertFalse(valid);
    }

    @Test
    @DisplayName("userId 不同时验签失败")
    void shouldFailWithDifferentUserId() {
        long ts = System.currentTimeMillis() / 1000;
        String sig = InternalHeaderSigner.sign(SECRET, "trace-123", "user-456",
                "admin", "READ", "USER_LIST", ts);

        boolean valid = InternalHeaderSigner.verify(SECRET, "trace-123", "user-999",
                "admin", "READ", "USER_LIST", sig, ts, ts);

        assertFalse(valid);
    }

    @Test
    @DisplayName("null 签名验签失败")
    void shouldFailWithNullSignature() {
        long ts = System.currentTimeMillis() / 1000;
        boolean valid = InternalHeaderSigner.verify(SECRET, "trace-123", "user-456",
                "admin", "READ", "USER_LIST", null, ts, ts);
        assertFalse(valid);
    }

    @Test
    @DisplayName("空白签名验签失败")
    void shouldFailWithBlankSignature() {
        long ts = System.currentTimeMillis() / 1000;
        boolean valid = InternalHeaderSigner.verify(SECRET, "trace-123", "user-456",
                "admin", "READ", "USER_LIST", "  ", ts, ts);
        assertFalse(valid);
    }

    @Test
    @DisplayName("时间戳超出窗口（未来）验签失败")
    void shouldFailWithFutureTimestamp() {
        long ts = System.currentTimeMillis() / 1000;
        String sig = InternalHeaderSigner.sign(SECRET, "trace-123", "user-456",
                "admin", "READ", "USER_LIST", ts);

        // 模拟 10 分钟后的时间
        long futureTs = ts + 600;
        boolean valid = InternalHeaderSigner.verify(SECRET, "trace-123", "user-456",
                "admin", "READ", "USER_LIST", sig, ts, futureTs);

        assertFalse(valid);
    }

    @Test
    @DisplayName("时间戳超出窗口（过期）验签失败")
    void shouldFailWithExpiredTimestamp() {
        long ts = System.currentTimeMillis() / 1000;
        String sig = InternalHeaderSigner.sign(SECRET, "trace-123", "user-456",
                "admin", "READ", "USER_LIST", ts);

        // 模拟 10 分钟前的时间
        long pastTs = ts - 600;
        boolean valid = InternalHeaderSigner.verify(SECRET, "trace-123", "user-456",
                "admin", "READ", "USER_LIST", sig, ts, pastTs);

        assertFalse(valid);
    }

    @Test
    @DisplayName("null 参数安全处理（不抛 NPE）")
    void shouldHandleNullParamsSafely() {
        long ts = System.currentTimeMillis() / 1000;
        // roles 和 permissions 传 null，应等价于空字符串
        String sig = InternalHeaderSigner.sign(SECRET, null, null,
                null, null, null, ts);

        boolean valid = InternalHeaderSigner.verify(SECRET, null, null,
                null, null, null, sig, ts, ts);

        assertTrue(valid);
    }

    @Test
    @DisplayName("同一参数多次签名结果一致")
    void shouldProduceConsistentSignature() {
        long ts = 1700000000L;
        String sig1 = InternalHeaderSigner.sign(SECRET, "trace", "user",
                "role", "perm", "list", ts);
        String sig2 = InternalHeaderSigner.sign(SECRET, "trace", "user",
                "role", "perm", "list", ts);

        assertEquals(sig1, sig2);
    }

    @Test
    @DisplayName("签名结果为 Base64Url 编码")
    void shouldProduceBase64UrlEncodedSignature() {
        long ts = System.currentTimeMillis() / 1000;
        String sig = InternalHeaderSigner.sign(SECRET, "trace", "user",
                "role", "perm", "list", ts);

        assertNotNull(sig);
        assertFalse(sig.isBlank());
        // Base64Url 不包含 + / = 字符
        assertTrue(!sig.contains("+") && !sig.contains("/") && !sig.contains("="));
    }
}
