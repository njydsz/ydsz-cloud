package com.njydsz.pmis.message.channel.push;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 个推（GeTui）V2 API 签名工具单元测试。
 *
 * <p>验证 SHA-256(appKey + timestamp + masterSecret) 签名算法的正确性、
 * 确定性、字节转十六进制等行为。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class GetuiPushSignerTest {

    @Test
    void sign_returnsNonEmptyHexString() {
        String signature = GetuiPushSigner.sign("appKey123", "1700000000000", "masterSecret456");
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        // SHA-256 输出固定 64 位十六进制小写
        assertEquals(64, signature.length());
        assertTrue(signature.matches("^[0-9a-f]{64}$"), "应为 64 位十六进制小写串");
    }

    @Test
    void sign_deterministicForSameInput() {
        String s1 = GetuiPushSigner.sign("appKey", "1700000000000", "secret");
        String s2 = GetuiPushSigner.sign("appKey", "1700000000000", "secret");
        assertEquals(s1, s2);
    }

    @Test
    void sign_differsForDifferentAppKey() {
        String s1 = GetuiPushSigner.sign("appKey-A", "1700000000000", "secret");
        String s2 = GetuiPushSigner.sign("appKey-B", "1700000000000", "secret");
        assertFalse(s1.equals(s2), "不同 appKey 应产生不同签名");
    }

    @Test
    void sign_differsForDifferentTimestamp() {
        String s1 = GetuiPushSigner.sign("appKey", "1700000000000", "secret");
        String s2 = GetuiPushSigner.sign("appKey", "1700000000001", "secret");
        assertFalse(s1.equals(s2), "不同 timestamp 应产生不同签名");
    }

    @Test
    void sign_differsForDifferentMasterSecret() {
        String s1 = GetuiPushSigner.sign("appKey", "1700000000000", "secret-A");
        String s2 = GetuiPushSigner.sign("appKey", "1700000000000", "secret-B");
        assertFalse(s1.equals(s2), "不同 masterSecret 应产生不同签名");
    }

    @Test
    void sign_matchesKnownVector() {
        // 已知向量：使用 JDK 标准 SHA-256 计算确认结果一致
        // 原始串："appKey1700000000000masterSecret"
        String expected = sha256Hex("appKey1700000000000masterSecret");
        String actual = GetuiPushSigner.sign("appKey", "1700000000000", "masterSecret");
        assertEquals(expected, actual);
    }

    @Test
    void bytesToHex_returnsLowercaseHex() {
        byte[] bytes = {(byte) 0x00, (byte) 0xFF, (byte) 0x0A, (byte) 0x10};
        String hex = GetuiPushSigner.bytesToHex(bytes);
        assertEquals("00ff0a10", hex);
    }

    @Test
    void bytesToHex_emptyArrayReturnsEmpty() {
        assertEquals("", GetuiPushSigner.bytesToHex(new byte[0]));
    }

    /**
     * 使用 JDK MessageDigest 计算 SHA-256 十六进制小写串，作为对照基准。
     *
     * @param input 原始输入
     * @return SHA-256 十六进制小写
     */
    private static String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
