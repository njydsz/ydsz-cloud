package com.njydsz.pmis.common.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenProvider 单元测试
 *
 * <p>P0-C4 重点验证：
 * <ul>
 *   <li>弱密钥检测（isWeakSecret）</li>
 *   <li>生产环境拒绝启动（isProductionProfile + buildKey）</li>
 *   <li>非生产环境弱密钥允许启动但打 WARN（defaultKeyUsed=true）</li>
 *   <li>强密钥正常通过</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 */
@DisplayName("JwtTokenProvider 测试")
class JwtTokenProviderTest {

    /** 强随机密钥（32 字节，非弱标识），用于正常路径测试 */
    private static final String STRONG_SECRET = "a8f3c2e9b1d4f7a6c0e5b2d8f4a1c7e9b3d6f0a2c8e4b1d5";

    @Test
    @DisplayName("isWeakSecret - 含 default 标识应返回 true")
    void isWeakSecret_defaultMarker_shouldReturnTrue() {
        assertTrue(JwtTokenProvider.isWeakSecret("default-jwt-secret-key-xxxxxxxxxxxxxx"));
        assertTrue(JwtTokenProvider.isWeakSecret("Default-Secret-xxxxxxxxxxxxxxxxxxxxx"));
        assertTrue(JwtTokenProvider.isWeakSecret("DEFAULT-KEY-xxxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
    }

    @Test
    @DisplayName("isWeakSecret - 含 change-me / your-secret 标识应返回 true")
    void isWeakSecret_changeMeMarker_shouldReturnTrue() {
        assertTrue(JwtTokenProvider.isWeakSecret("change-me-please-xxxxxxxxxxxxxxxxxx"));
        assertTrue(JwtTokenProvider.isWeakSecret("your-secret-here-xxxxxxxxxxxxxxxxxxx"));
        assertTrue(JwtTokenProvider.isWeakSecret("changeme-xxxxxxxxxxxxxxxxxxxxxxxxxx"));
        assertTrue(JwtTokenProvider.isWeakSecret("your_secret-xxxxxxxxxxxxxxxxxxxxxxx"));
    }

    @Test
    @DisplayName("isWeakSecret - 含 test / demo / example / placeholder 标识应返回 true")
    void isWeakSecret_testMarkers_shouldReturnTrue() {
        assertTrue(JwtTokenProvider.isWeakSecret("test-secret-xxxxxxxxxxxxxxxxxxxxxxxxxx"));
        assertTrue(JwtTokenProvider.isWeakSecret("demo-key-xxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
        assertTrue(JwtTokenProvider.isWeakSecret("example-secret-xxxxxxxxxxxxxxxxxxxxxx"));
        assertTrue(JwtTokenProvider.isWeakSecret("placeholder-jwt-xxxxxxxxxxxxxxxxxxxxx"));
    }

    @Test
    @DisplayName("isWeakSecret - 含 pmis-user-module-jwt-secret 标识应返回 true")
    void isWeakSecret_pmisLegacyMarker_shouldReturnTrue() {
        assertTrue(JwtTokenProvider.isWeakSecret("pmis-user-module-jwt-secret-2026-xxxxx"));
    }

    @Test
    @DisplayName("isWeakSecret - 强随机密钥应返回 false")
    void isWeakSecret_strongSecret_shouldReturnFalse() {
        assertFalse(JwtTokenProvider.isWeakSecret(STRONG_SECRET));
        assertFalse(JwtTokenProvider.isWeakSecret("a8f3c2e9b1d4f7a6c0e5b2d8f4a1c7e9b3d6f0a2c8e4b1d5"));
    }

    @Test
    @DisplayName("isWeakSecret - null/空应返回 true")
    void isWeakSecret_blank_shouldReturnTrue() {
        assertTrue(JwtTokenProvider.isWeakSecret(null));
        assertTrue(JwtTokenProvider.isWeakSecret(""));
        assertTrue(JwtTokenProvider.isWeakSecret("   "));
    }

    @Test
    @DisplayName("isProductionProfile - prod / production 应返回 true")
    void isProductionProfile_prodVariants_shouldReturnTrue() {
        assertTrue(JwtTokenProvider.isProductionProfile("prod"));
        assertTrue(JwtTokenProvider.isProductionProfile("production"));
        assertTrue(JwtTokenProvider.isProductionProfile("PROD"));
        assertTrue(JwtTokenProvider.isProductionProfile("Production"));
    }

    @Test
    @DisplayName("isProductionProfile - dev/test/default 应返回 false")
    void isProductionProfile_nonProd_shouldReturnFalse() {
        assertFalse(JwtTokenProvider.isProductionProfile("dev"));
        assertFalse(JwtTokenProvider.isProductionProfile("test"));
        assertFalse(JwtTokenProvider.isProductionProfile("default"));
        assertFalse(JwtTokenProvider.isProductionProfile("staging"));
    }

    @Test
    @DisplayName("isProductionProfile - null/空应返回 false")
    void isProductionProfile_blank_shouldReturnFalse() {
        assertFalse(JwtTokenProvider.isProductionProfile(null));
        assertFalse(JwtTokenProvider.isProductionProfile(""));
    }

    @Test
    @DisplayName("buildKey - 密钥为空应抛出 IllegalStateException")
    void buildKey_emptySecret_shouldThrow() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", "");
        ReflectionTestUtils.setField(provider, "activeProfile", "dev");
        IllegalStateException ex = assertThrows(IllegalStateException.class, provider::buildKey);
        assertTrue(ex.getMessage().contains("未配置"));
    }

    @Test
    @DisplayName("buildKey - 密钥长度不足 32 字节应抛出 IllegalStateException")
    void buildKey_shortSecret_shouldThrow() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", "tooshort");
        ReflectionTestUtils.setField(provider, "activeProfile", "dev");
        IllegalStateException ex = assertThrows(IllegalStateException.class, provider::buildKey);
        assertTrue(ex.getMessage().contains("至少 32 字节"));
    }

    @Test
    @DisplayName("buildKey - 生产环境 + 弱密钥应抛出 IllegalStateException 拒绝启动")
    void buildKey_prodWithWeakSecret_shouldThrow() {
        JwtTokenProvider provider = new JwtTokenProvider();
        // 32+ 字节但含 "default" 弱标识
        ReflectionTestUtils.setField(provider, "secret", "default-jwt-secret-key-xxxxxxxxxxxx");
        ReflectionTestUtils.setField(provider, "activeProfile", "prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, provider::buildKey);
        assertTrue(ex.getMessage().contains("生产环境禁止使用默认/弱密钥"),
                "异常消息应包含生产环境禁止提示，实际: " + ex.getMessage());
        // defaultKeyUsed 应在抛异常前已标记
        assertTrue((boolean) ReflectionTestUtils.getField(provider, "defaultKeyUsed"));
    }

    @Test
    @DisplayName("buildKey - 生产环境 + 强密钥应正常通过")
    void buildKey_prodWithStrongSecret_shouldSucceed() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", STRONG_SECRET);
        ReflectionTestUtils.setField(provider, "activeProfile", "prod");

        assertNotNull(provider.buildKey());
        assertFalse((boolean) ReflectionTestUtils.getField(provider, "defaultKeyUsed"));
    }

    @Test
    @DisplayName("buildKey - 非生产环境 + 弱密钥应允许启动但标记 defaultKeyUsed=true")
    void buildKey_devWithWeakSecret_shouldAllowButMarkDefault() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", "default-jwt-secret-key-xxxxxxxxxxxx");
        ReflectionTestUtils.setField(provider, "activeProfile", "dev");

        assertNotNull(provider.buildKey());
        assertTrue((boolean) ReflectionTestUtils.getField(provider, "defaultKeyUsed"));
    }

    @Test
    @DisplayName("buildKey - 非生产环境 + 强密钥应正常通过")
    void buildKey_devWithStrongSecret_shouldSucceed() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", STRONG_SECRET);
        ReflectionTestUtils.setField(provider, "activeProfile", "dev");

        assertNotNull(provider.buildKey());
        assertFalse((boolean) ReflectionTestUtils.getField(provider, "defaultKeyUsed"));
    }

    @Test
    @DisplayName("buildKey - base64: 前缀 + 强密钥应正常通过")
    void buildKey_base64StrongSecret_shouldSucceed() {
        JwtTokenProvider provider = new JwtTokenProvider();
        // 32 字节随机数据的 Base64 编码（44 字符）
        String base64Secret = "base64:YSQkIzEyMzQ1Njc4OWBxcnN0dXZ3eHl6MTIzNDU2Nzg5MA==";
        ReflectionTestUtils.setField(provider, "secret", base64Secret);
        ReflectionTestUtils.setField(provider, "activeProfile", "prod");

        assertNotNull(provider.buildKey());
        assertFalse((boolean) ReflectionTestUtils.getField(provider, "defaultKeyUsed"));
    }

    @Test
    @DisplayName("buildKey - base64: 前缀但内容非法应抛出异常")
    void buildKey_invalidBase64_shouldThrow() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", "base64:not-valid-base64!!!@@@");
        ReflectionTestUtils.setField(provider, "activeProfile", "dev");

        IllegalStateException ex = assertThrows(IllegalStateException.class, provider::buildKey);
        assertTrue(ex.getMessage().contains("base64 部分无法解析"));
    }
}
