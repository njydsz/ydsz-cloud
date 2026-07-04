package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InternalHeaderSigner 单元测试（P0-C5）
 *
 * @author ydsz-pmis-team
 */
@DisplayName("InternalHeaderSigner 内部头签名测试")
class InternalHeaderSignerTest {

    /** 测试密钥（32+ 字节强随机密钥，非弱标识） */
    private static final String SECRET = "a8f3c2e9b1d4f7a6c0e5b2d8f4a1c7e9b3d6f0a2c8e4b1d5";

    @Test
    @DisplayName("sign + verify - 合法签名应校验通过")
    void signAndVerify_validSig_shouldReturnTrue() {
        long ts = System.currentTimeMillis() / 1000L;
        String sig = InternalHeaderSigner.sign(SECRET, "trace123", "1", "admin",
                "ROLE_ADMIN,ROLE_USER", "user:read,user:write", ts);
        assertTrue(InternalHeaderSigner.verify(SECRET, "trace123", "1", "admin",
                "ROLE_ADMIN,ROLE_USER", "user:read,user:write", sig, ts, ts));
    }

    @Test
    @DisplayName("sign - 相同输入应产生相同签名（确定性）")
    void sign_sameInput_shouldProduceSameSig() {
        long ts = 1700000000L;
        String sig1 = InternalHeaderSigner.sign(SECRET, "trace", "1", "u",
                "r", "p", ts);
        String sig2 = InternalHeaderSigner.sign(SECRET, "trace", "1", "u",
                "r", "p", ts);
        assertEquals(sig1, sig2);
    }

    @Test
    @DisplayName("sign - 不同输入应产生不同签名")
    void sign_differentInput_shouldProduceDifferentSig() {
        long ts = 1700000000L;
        String sig1 = InternalHeaderSigner.sign(SECRET, "trace1", "1", "u", "r", "p", ts);
        String sig2 = InternalHeaderSigner.sign(SECRET, "trace2", "1", "u", "r", "p", ts);
        assertNotEquals(sig1, sig2);
    }

    @Test
    @DisplayName("verify - 篡改 userId 应校验失败")
    void verify_tamperedUserId_shouldReturnFalse() {
        long ts = System.currentTimeMillis() / 1000L;
        String sig = InternalHeaderSigner.sign(SECRET, "trace", "1", "admin", "r", "p", ts);
        assertFalse(InternalHeaderSigner.verify(SECRET, "trace", "2", "admin", "r", "p", sig, ts, ts));
    }

    @Test
    @DisplayName("verify - 篡改 roles 应校验失败（防越权）")
    void verify_tamperedRoles_shouldReturnFalse() {
        long ts = System.currentTimeMillis() / 1000L;
        String sig = InternalHeaderSigner.sign(SECRET, "trace", "1", "u", "ROLE_USER", "p", ts);
        assertFalse(InternalHeaderSigner.verify(SECRET, "trace", "1", "u",
                "ROLE_ADMIN,ROLE_USER", "p", sig, ts, ts));
    }

    @Test
    @DisplayName("verify - 篡改 traceId 应校验失败")
    void verify_tamperedTraceId_shouldReturnFalse() {
        long ts = System.currentTimeMillis() / 1000L;
        String sig = InternalHeaderSigner.sign(SECRET, "trace1", "1", "u", "r", "p", ts);
        assertFalse(InternalHeaderSigner.verify(SECRET, "trace2", "1", "u", "r", "p", sig, ts, ts));
    }

    @Test
    @DisplayName("verify - 不同密钥应校验失败")
    void verify_differentSecret_shouldReturnFalse() {
        long ts = System.currentTimeMillis() / 1000L;
        String sig = InternalHeaderSigner.sign(SECRET, "trace", "1", "u", "r", "p", ts);
        String otherSecret = "b9e4d3f8c2a5b7d1e0f3a6c8b2d4f6a8e0c2b4d6f8a0e2c4b6d8f0";
        assertFalse(InternalHeaderSigner.verify(otherSecret, "trace", "1", "u", "r", "p", sig, ts, ts));
    }

    @Test
    @DisplayName("verify - 签名为空应返回 false")
    void verify_blankSig_shouldReturnFalse() {
        long ts = System.currentTimeMillis() / 1000L;
        assertFalse(InternalHeaderSigner.verify(SECRET, "trace", "1", "u", "r", "p", null, ts, ts));
        assertFalse(InternalHeaderSigner.verify(SECRET, "trace", "1", "u", "r", "p", "", ts, ts));
        assertFalse(InternalHeaderSigner.verify(SECRET, "trace", "1", "u", "r", "p", "   ", ts, ts));
    }

    @Test
    @DisplayName("verify - 时间戳过期应返回 false（防重放）")
    void verify_expiredTs_shouldReturnFalse() {
        long ts = System.currentTimeMillis() / 1000L;
        String sig = InternalHeaderSigner.sign(SECRET, "trace", "1", "u", "r", "p", ts);
        // 模拟超过 60 秒窗口
        long futureTs = ts + 120;
        assertFalse(InternalHeaderSigner.verify(SECRET, "trace", "1", "u", "r", "p", sig, ts, futureTs));
    }

    @Test
    @DisplayName("verify - 时间戳未来签名应返回 false（防预生成）")
    void verify_futureTs_shouldReturnFalse() {
        long ts = System.currentTimeMillis() / 1000L;
        long futureSignTs = ts + 120;
        String sig = InternalHeaderSigner.sign(SECRET, "trace", "1", "u", "r", "p", futureSignTs);
        assertFalse(InternalHeaderSigner.verify(SECRET, "trace", "1", "u", "r", "p", sig, futureSignTs, ts));
    }

    @Test
    @DisplayName("verify - 时间戳窗口边界（60 秒）应通过")
    void verify_tsBoundary_shouldPass() {
        long ts = System.currentTimeMillis() / 1000L;
        String sig = InternalHeaderSigner.sign(SECRET, "trace", "1", "u", "r", "p", ts);
        // 恰好 60 秒前/后应通过（abs <= 60）
        assertTrue(InternalHeaderSigner.verify(SECRET, "trace", "1", "u", "r", "p", sig, ts, ts + 60));
        assertTrue(InternalHeaderSigner.verify(SECRET, "trace", "1", "u", "r", "p", sig, ts, ts - 60));
        // 61 秒应失败
        assertFalse(InternalHeaderSigner.verify(SECRET, "trace", "1", "u", "r", "p", sig, ts, ts + 61));
    }

    @Test
    @DisplayName("sign - null 字段应正常处理（不抛 NPE）")
    void sign_nullFields_shouldNotThrow() {
        long ts = System.currentTimeMillis() / 1000L;
        String sig = assertDoesNotThrow(() ->
                InternalHeaderSigner.sign(SECRET, null, null, null, null, null, ts));
        assertNotNull(sig);
        assertTrue(InternalHeaderSigner.verify(SECRET, "", "", "", "", "", sig, ts, ts));
    }
}
