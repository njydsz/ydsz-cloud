package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TotpUtil 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("TotpUtil 测试")
class TotpUtilTest {

    @Test
    @DisplayName("生成密钥 - 应返回非空 Base32 字符串")
    void generateSecret_shouldReturnNonEmptyBase32String() {
        String secret = TotpUtil.generateSecret();
        assertNotNull(secret);
        assertFalse(secret.isBlank());
        assertTrue(secret.matches("[A-Z2-7]+"), "密钥应是 Base32 字符");
    }

    @Test
    @DisplayName("生成 OTP - 相同密钥和相同时间戳应产生相同 OTP")
    void generate_shouldProduceSameOtpForSameInput() {
        String secret = TotpUtil.generateSecret();
        long timestamp = Instant.now().getEpochSecond();

        String otp1 = TotpUtil.generate(secret, timestamp);
        String otp2 = TotpUtil.generate(secret, timestamp);

        assertEquals(otp1, otp2);
        assertEquals(6, otp1.length(), "OTP 长度应为 6 位");
    }

    @Test
    @DisplayName("校验 OTP - 当前时间生成的 OTP 应通过校验")
    void verify_shouldPassForCurrentOtp() {
        String secret = TotpUtil.generateSecret();
        String otp = TotpUtil.generate(secret);

        assertTrue(TotpUtil.verify(secret, otp));
    }

    @Test
    @DisplayName("校验 OTP - 空或 null 输入应返回 false")
    void verify_shouldReturnFalseForNullOrEmpty() {
        String secret = TotpUtil.generateSecret();

        assertFalse(TotpUtil.verify(null, "123456"));
        assertFalse(TotpUtil.verify(secret, null));
        assertFalse(TotpUtil.verify(secret, "12345"));
        assertFalse(TotpUtil.verify(secret, "1234567"));
    }

    @Test
    @DisplayName("生成备份码 - 应返回指定数量且长度正确的备份码")
    void generateBackupCodes_shouldReturnCorrectCountAndLength() {
        int count = 5;
        String[] codes = TotpUtil.generateBackupCodes(count);

        assertEquals(count, codes.length);
        for (String code : codes) {
            assertNotNull(code);
            assertEquals(8, code.length(), "备份码应为 8 位十六进制");
        }
    }

    @Test
    @DisplayName("校验备份码 - 应正确匹配（不区分大小写）")
    void verifyBackupCode_shouldMatchCaseInsensitive() {
        String[] codes = {"ABCD1234", "5678EFGH"};
        assertTrue(TotpUtil.verifyBackupCode("abcd1234", codes));
        assertTrue(TotpUtil.verifyBackupCode("5678efgh", codes));
        assertFalse(TotpUtil.verifyBackupCode("00000000", codes));
    }

    @Test
    @DisplayName("校验备份码 - null 输入应返回 false")
    void verifyBackupCode_shouldReturnFalseForNull() {
        assertFalse(TotpUtil.verifyBackupCode(null, new String[]{"ABC"}));
        assertFalse(TotpUtil.verifyBackupCode("ABC", null));
    }

    @Test
    @DisplayName("Base32 编解码 - 编码后解码应还原原始数据")
    void encodeDecodeBase32_shouldRoundtrip() {
        byte[] original = new byte[]{1, 2, 3, 4, 5, 10, 20, 30, 40, 50};
        String encoded = TotpUtil.encodeBase32(original);
        byte[] decoded = TotpUtil.decodeBase32(encoded);

        assertArrayEquals(original, decoded);
    }

    @Test
    @DisplayName("Base32 编码 - null 或空数组应返回空字符串")
    void encodeBase32_shouldReturnEmptyForNullOrEmpty() {
        assertEquals("", TotpUtil.encodeBase32(null));
        assertEquals("", TotpUtil.encodeBase32(new byte[0]));
    }

    @Test
    @DisplayName("Base32 解码 - null 输入应返回空数组")
    void decodeBase32_shouldReturnEmptyForNull() {
        assertEquals(0, TotpUtil.decodeBase32(null).length);
    }

    @Test
    @DisplayName("生成 otpauth URI - 应包含必要参数")
    void otpAuthUri_shouldContainRequiredParams() {
        String uri = TotpUtil.otpAuthUri("test@example.com", "MyApp", "SECRETKEY");

        assertNotNull(uri);
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("test@example.com"));
        assertTrue(uri.contains("MyApp"));
        assertTrue(uri.contains("SECRETKEY"));
        assertTrue(uri.contains("algorithm=SHA1"));
        assertTrue(uri.contains("digits=6"));
        assertTrue(uri.contains("period=30"));
    }

    @Test
    @DisplayName("getDigits - 应返回 6")
    void getDigits_shouldReturn6() {
        assertEquals(6, TotpUtil.getDigits());
    }

    @Test
    @DisplayName("getTimeStep - 应返回 30")
    void getTimeStep_shouldReturn30() {
        assertEquals(30, TotpUtil.getTimeStep());
    }
}