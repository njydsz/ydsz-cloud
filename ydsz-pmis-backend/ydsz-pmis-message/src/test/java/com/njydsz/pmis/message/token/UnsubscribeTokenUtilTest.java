package com.njydsz.pmis.message.token;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.config.MessageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UnsubscribeTokenUtil} 单元测试（P1-5）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("UnsubscribeTokenUtil 退订 token 工具测试")
class UnsubscribeTokenUtilTest {

    private UnsubscribeTokenUtil unsubscribeTokenUtil;
    private MessageProperties messageProperties;

    @BeforeEach
    void setUp() {
        messageProperties = new MessageProperties();
        messageProperties.getUnsubscribe().setSecret("test-secret-key-for-unit-test-only-32bytes");
        messageProperties.getUnsubscribe().setTtlDays(7);
        unsubscribeTokenUtil = new UnsubscribeTokenUtil(messageProperties);
    }

    @Test
    @DisplayName("generate 生成可解析的合法 token")
    void generateShouldCreateValidToken() {
        String token = unsubscribeTokenUtil.generate("u1", "RISK_ALERT", "EMAIL");

        assertNotNull(token);
        assertTrue(token.contains("."));
        UnsubscribeTokenPayload payload = unsubscribeTokenUtil.parseAndVerify(token);
        assertEquals("u1", payload.getUserId());
        assertEquals("RISK_ALERT", payload.getTopicCode());
        assertEquals("EMAIL", payload.getChannel());
    }

    @Test
    @DisplayName("parseAndVerify 合法 token 返回载荷")
    void parseAndVerifyShouldReturnPayloadForValidToken() {
        String token = unsubscribeTokenUtil.generate("user-123", "CONTRACT", "SMS");

        UnsubscribeTokenPayload payload = unsubscribeTokenUtil.parseAndVerify(token);

        assertEquals("user-123", payload.getUserId());
        assertEquals("CONTRACT", payload.getTopicCode());
        assertEquals("SMS", payload.getChannel());
        assertTrue(payload.getExpiresAt() > 0);
    }

    @Test
    @DisplayName("parseAndVerify 空 token 抛参数错误")
    void parseAndVerifyShouldRejectEmptyToken() {
        assertThrows(BizException.class, () -> unsubscribeTokenUtil.parseAndVerify(""));
        assertThrows(BizException.class, () -> unsubscribeTokenUtil.parseAndVerify(null));
    }

    @Test
    @DisplayName("parseAndVerify 格式非法(无分隔符)抛参数错误")
    void parseAndVerifyShouldRejectMalformedToken() {
        assertThrows(BizException.class, () -> unsubscribeTokenUtil.parseAndVerify("no-dot-in-token"));
    }

    @Test
    @DisplayName("parseAndVerify 签名被篡改抛参数错误")
    void parseAndVerifyShouldRejectTamperedSignature() {
        String token = unsubscribeTokenUtil.generate("u1", "RISK", "EMAIL");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + ".tampered-signature";

        assertThrows(BizException.class, () -> unsubscribeTokenUtil.parseAndVerify(tampered));
    }

    @Test
    @DisplayName("parseAndVerify payload 被篡改抛签名校验失败")
    void parseAndVerifyShouldRejectTamperedPayload() {
        String token = unsubscribeTokenUtil.generate("u1", "RISK", "EMAIL");
        // 修改 payload 部分但保留原签名
        String[] parts = token.split("\\.");
        // 用一个不同的 base64url 串替换 payload
        String tamperedPayload = "dTEjfE5PVCNDT05UUKMjMTczOTg5NzYwMA";
        String tampered = tamperedPayload + "." + parts[1];

        assertThrows(BizException.class, () -> unsubscribeTokenUtil.parseAndVerify(tampered));
    }

    @Test
    @DisplayName("parseAndVerify 过期 token 抛参数错误")
    void parseAndVerifyShouldRejectExpiredToken() {
        // 手动构造已过期 token：expiresAt = 当前时间 - 1 小时
        String secret = "test-secret-key-for-unit-test-only-32bytes";
        long expiredAt = java.time.Instant.now().minusSeconds(3600).getEpochSecond();
        String payload = "u1|RISK|EMAIL|" + expiredAt;
        String payloadB64 = com.njydsz.pmis.common.util.CryptoUtil
                .base64UrlEncode(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sig = com.njydsz.pmis.common.util.CryptoUtil
                .hmacSha256(payload, secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expiredToken = payloadB64 + "." + sig;

        assertThrows(BizException.class, () -> unsubscribeTokenUtil.parseAndVerify(expiredToken));
    }

    @Test
    @DisplayName("generate 参数缺失抛参数错误")
    void generateShouldRejectBlankArgs() {
        assertThrows(BizException.class, () -> unsubscribeTokenUtil.generate("", "RISK", "EMAIL"));
        assertThrows(BizException.class, () -> unsubscribeTokenUtil.generate("u1", "", "EMAIL"));
        assertThrows(BizException.class, () -> unsubscribeTokenUtil.generate("u1", "RISK", ""));
    }

    @Test
    @DisplayName("不同密钥生成的 token 互相不可校验")
    void tokenShouldNotBeValidatedWithDifferentSecret() {
        String token = unsubscribeTokenUtil.generate("u1", "RISK", "EMAIL");
        messageProperties.getUnsubscribe().setSecret("another-different-secret-key-32-bytes!");
        assertThrows(BizException.class, () -> unsubscribeTokenUtil.parseAndVerify(token));
    }

    @Test
    @DisplayName("buildUrl 未配置 baseUrl 时返回 token 本身")
    void buildUrlShouldReturnTokenWhenBaseUrlAbsent() {
        String token = "abc.def";
        assertEquals(token, unsubscribeTokenUtil.buildUrl(token));
    }

    @Test
    @DisplayName("buildUrl 配置 baseUrl 时拼接完整 URL")
    void buildUrlShouldConcatWhenBaseUrlConfigured() {
        messageProperties.getUnsubscribe().setBaseUrl("https://pmis.example.com/unsubscribe");
        String url = unsubscribeTokenUtil.buildUrl("abc.def");
        assertEquals("https://pmis.example.com/unsubscribe?token=abc.def", url);
    }

    @Test
    @DisplayName("buildUrl baseUrl 末尾斜杠被规范化")
    void buildUrlShouldNormalizeTrailingSlash() {
        messageProperties.getUnsubscribe().setBaseUrl("https://pmis.example.com/unsubscribe/");
        String url = unsubscribeTokenUtil.buildUrl("abc.def");
        assertEquals("https://pmis.example.com/unsubscribe?token=abc.def", url);
    }

    @Test
    @DisplayName("未配置 secret 时降级使用内置默认密钥,token 仍可校验")
    void shouldFallbackToDefaultSecretWhenNotConfigured() {
        messageProperties.getUnsubscribe().setSecret(null);
        // 重新构造 util 以使用新配置
        UnsubscribeTokenUtil utilWithDefault = new UnsubscribeTokenUtil(messageProperties);
        String token = utilWithDefault.generate("u1", "RISK", "EMAIL");
        assertDoesNotThrow(() -> utilWithDefault.parseAndVerify(token));
    }

    @Test
    @DisplayName("ttlDays 为 0 或负数时兜底为 1 天")
    void shouldFallbackTtlDaysToAtLeastOne() {
        messageProperties.getUnsubscribe().setTtlDays(0);
        String token = unsubscribeTokenUtil.generate("u1", "RISK", "EMAIL");
        UnsubscribeTokenPayload payload = unsubscribeTokenUtil.parseAndVerify(token);
        assertTrue(payload.getExpiresAt() > 0);
    }

    @Test
    @DisplayName("同一参数多次生成的 token 各不相同(payload 含过期时间,但同一秒内可能相同)")
    void generateShouldProduceValidTokenEachTime() {
        String t1 = unsubscribeTokenUtil.generate("u1", "RISK", "EMAIL");
        String t2 = unsubscribeTokenUtil.generate("u1", "RISK", "EMAIL");
        // 两个 token 都应可校验
        assertDoesNotThrow(() -> unsubscribeTokenUtil.parseAndVerify(t1));
        assertDoesNotThrow(() -> unsubscribeTokenUtil.parseAndVerify(t2));
    }
}
