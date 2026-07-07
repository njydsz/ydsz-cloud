package com.njydsz.pmis.message.channel.sms;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阿里云 SMS 签名工具单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class AliyunSmsSignerTest {

    @Test
    void percentEncode_encodesSpecialChars() {
        assertEquals("Hello%20World", AliyunSmsSigner.percentEncode("Hello World"));
        assertEquals("a%2Ab", AliyunSmsSigner.percentEncode("a*b"));
        assertEquals("~", AliyunSmsSigner.percentEncode("~"));
    }

    @Test
    void percentEncode_nullReturnsEmpty() {
        assertEquals("", AliyunSmsSigner.percentEncode(null));
    }

    @Test
    void buildCanonicalQuery_sortsByKey() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Zeta", "1");
        params.put("Alpha", "2");
        params.put("Mike", "3");
        String canonical = AliyunSmsSigner.buildCanonicalQuery(params);
        assertTrue(canonical.startsWith("Alpha=2"), "应按字典序排序");
        assertTrue(canonical.contains("Mike=3"));
        assertTrue(canonical.endsWith("Zeta=1"));
    }

    @Test
    void sign_returnsNonEmptyBase64() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("AccessKeyId", "test-ak");
        params.put("Action", "SendSms");
        String signature = AliyunSmsSigner.sign(params, "test-sk");
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        assertTrue(signature.matches("^[A-Za-z0-9+/=]+$"), "应为 Base64 字符集");
    }

    @Test
    void sign_deterministicForSameInput() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Action", "SendSms");
        params.put("PhoneNumbers", "13800000000");
        String s1 = AliyunSmsSigner.sign(params, "secret");
        String s2 = AliyunSmsSigner.sign(params, "secret");
        assertEquals(s1, s2);
    }

    @Test
    void buildQuery_preservesOrder() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Signature", "abc");
        params.put("Action", "SendSms");
        String query = AliyunSmsSigner.buildQuery(params);
        assertEquals("Signature=abc&Action=SendSms", query);
    }
}
