package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TOTP 工具测试
 *
 * @author ydsz-pmis-team
 */
class TotpUtilTest {

    @Test
    @DisplayName("生成 secret 长度 32（Base32 编码 20 字节）")
    void generateSecret() {
        String secret = TotpUtil.generateSecret();
        assertThat(secret).isNotNull();
        assertThat(secret.length()).isEqualTo(32);
        assertThat(secret).matches("[A-Z2-7]+");
    }

    @Test
    @DisplayName("Base32 编解码往返")
    void base32Roundtrip() {
        byte[] data = {1, 2, 3, 4, 5, (byte) 0xFF, 0, (byte) 0xAA};
        String encoded = TotpUtil.encodeBase32(data);
        byte[] decoded = TotpUtil.decodeBase32(encoded);
        assertThat(decoded).containsExactly(data);
    }

    @Test
    @DisplayName("OTP 生成与校验")
    void generateAndVerify() {
        String secret = TotpUtil.generateSecret();
        String otp = TotpUtil.generate(secret);
        assertThat(otp).hasSize(6);
        assertThat(otp).matches("\\d{6}");
        assertThat(TotpUtil.verify(secret, otp)).isTrue();
    }

    @Test
    @DisplayName("错误 OTP 校验失败")
    void verifyWrongOtp() {
        String secret = TotpUtil.generateSecret();
        assertThat(TotpUtil.verify(secret, "000000")).isFalse();
        assertThat(TotpUtil.verify(secret, null)).isFalse();
        assertThat(TotpUtil.verify(null, "123456")).isFalse();
    }

    @Test
    @DisplayName("错误长度 OTP 校验失败")
    void verifyBadLength() {
        String secret = TotpUtil.generateSecret();
        assertThat(TotpUtil.verify(secret, "12345")).isFalse();
        assertThat(TotpUtil.verify(secret, "1234567")).isFalse();
    }

    @Test
    @DisplayName("otpauth URI 生成")
    void otpAuthUri() {
        String secret = TotpUtil.generateSecret();
        String uri = TotpUtil.otpAuthUri("alice", "PMIS", secret);
        assertThat(uri).startsWith("otpauth://totp/PMIS:alice");
        assertThat(uri).contains("secret=" + secret);
        assertThat(uri).contains("issuer=PMIS");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
    }

    @Test
    @DisplayName("生成 8 个备份码")
    void backupCodes() {
        String[] codes = TotpUtil.generateBackupCodes(8);
        assertThat(codes).hasSize(8);
        for (String c : codes) {
            assertThat(c).hasSize(8);
            assertThat(c).matches("[0-9a-f]+");
        }
    }

    @Test
    @DisplayName("备份码校验")
    void verifyBackup() {
        String[] codes = {"abc12345", "6789abcd"};
        assertThat(TotpUtil.verifyBackupCode("ABC12345", codes)).isTrue();
        assertThat(TotpUtil.verifyBackupCode("xyz99999", codes)).isFalse();
        assertThat(TotpUtil.verifyBackupCode(null, codes)).isFalse();
        assertThat(TotpUtil.verifyBackupCode("any", (String[]) null)).isFalse();
    }

    @Test
    @DisplayName("常量")
    void constants() {
        assertThat(TotpUtil.getDigits()).isEqualTo(6);
        assertThat(TotpUtil.getTimeStep()).isEqualTo(30);
    }
}
