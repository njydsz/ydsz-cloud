package com.njydsz.pmis.message.server.channel.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * AliyunSmsSigner 阿里云短信签名工具单元测试
 */
@DisplayName("AliyunSmsSigner 阿里云短信签名工具测试")
class AliyunSmsSignerTest {

    @Nested
    @DisplayName("sign() 签名计算")
    class SignTest {

        @Test
        @DisplayName("已知参数和密钥生成确定性签名")
        void shouldGenerateDeterministicSignature() {
            // 准备已知参数
            Map<String, String> params = new LinkedHashMap<>();
            params.put("AccessKeyId", "testkey");
            params.put("Action", "SendSms");
            params.put("SignName", "验证码");
            String accessKeySecret = "testsecret";

            // 同样的输入应产生相同的签名
            String signature1 = AliyunSmsSigner.sign(params, accessKeySecret);
            String signature2 = AliyunSmsSigner.sign(params, accessKeySecret);

            assertThat(signature1).isEqualTo(signature2);
            assertThat(signature1).isNotBlank();
        }

        @Test
        @DisplayName("签名结果与独立计算的 HMAC-SHA1 结果一致")
        void shouldBeConsistentWithIndependentComputation() throws Exception {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("Action", "SendSms");
            String accessKeySecret = "mysecret";

            // 手工构造签名字符串，不依赖被测类的 percentEncode/buildCanonicalQuery
            // canonical = "Action=SendSms"
            // percentEncode("Action=SendSms") = "Action%3DSendSms"
            // percentEncode("/") = "%2F"
            // stringToSign = "GET&%2F&Action%3DSendSms"
            String stringToSign = "GET&%2F&Action%3DSendSms";
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(
                    (accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(digest);

            String actual = AliyunSmsSigner.sign(params, accessKeySecret);
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("不同密钥生成不同签名")
        void shouldGenerateDifferentSignatureWithDifferentSecret() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("Action", "SendSms");

            String sig1 = AliyunSmsSigner.sign(params, "secret1");
            String sig2 = AliyunSmsSigner.sign(params, "secret2");

            assertThat(sig1).isNotEqualTo(sig2);
        }

        @Test
        @DisplayName("空参数生成有效签名")
        void shouldGenerateValidSignatureWithEmptyParams() {
            Map<String, String> params = new LinkedHashMap<>();
            String signature = AliyunSmsSigner.sign(params, "secret");
            assertThat(signature).isNotBlank();
        }

        @Test
        @DisplayName("null 参数抛出空指针异常")
        void shouldThrowWhenParamsIsNull() {
            assertThatThrownBy(() -> AliyunSmsSigner.sign(null, "secret"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("percentEncode() 百分号编码")
    class PercentEncodeTest {

        @Test
        @DisplayName("空格编码为 %20")
        void shouldEncodeSpaceAsPercent20() {
            assertThat(AliyunSmsSigner.percentEncode("hello world")).isEqualTo("hello%20world");
        }

        @Test
        @DisplayName("加号编码为 %2B")
        void shouldEncodePlusAsPercent2B() {
            assertThat(AliyunSmsSigner.percentEncode("a+b")).isEqualTo("a%2Bb");
        }

        @Test
        @DisplayName("星号编码为 %2A")
        void shouldEncodeAsteriskAsPercent2A() {
            assertThat(AliyunSmsSigner.percentEncode("a*b")).isEqualTo("a%2Ab");
        }

        @Test
        @DisplayName("波浪号保持不变")
        void shouldKeepTildeUnchanged() {
            assertThat(AliyunSmsSigner.percentEncode("~")).isEqualTo("~");
        }

        @Test
        @DisplayName("斜杠编码为 %2F")
        void shouldEncodeSlashAsPercent2F() {
            assertThat(AliyunSmsSigner.percentEncode("/")).isEqualTo("%2F");
        }

        @Test
        @DisplayName("中文字符正确编码")
        void shouldEncodeChineseCharacters() {
            assertThat(AliyunSmsSigner.percentEncode("验证码"))
                    .isEqualTo("%E9%AA%8C%E8%AF%81%E7%A0%81");
        }

        @Test
        @DisplayName("组合特殊字符正确编码")
        void shouldEncodeCombinedSpecialCharacters() {
            // "a b~c*d" → URLEncoder → "a+b%7Ec*d" → 替换后 "a%20b~c%2Ad"
            assertThat(AliyunSmsSigner.percentEncode("a b~c*d"))
                    .isEqualTo("a%20b~c%2Ad");
        }

        @Test
        @DisplayName("null 返回空字符串")
        void shouldReturnEmptyStringForNull() {
            assertThat(AliyunSmsSigner.percentEncode(null)).isEqualTo("");
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void shouldReturnEmptyStringForEmptyString() {
            assertThat(AliyunSmsSigner.percentEncode("")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("buildCanonicalQuery() 规范化查询串")
    class BuildCanonicalQueryTest {

        @Test
        @DisplayName("参数按 key 字典序排序")
        void shouldSortParamsByKey() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("Action", "SendSms");
            params.put("AccessKeyId", "key");
            params.put("Format", "JSON");

            String result = AliyunSmsSigner.buildCanonicalQuery(params);

            assertThat(result).isEqualTo("AccessKeyId=key&Action=SendSms&Format=JSON");
        }

        @Test
        @DisplayName("空参数返回空字符串")
        void shouldReturnEmptyStringForEmptyParams() {
            Map<String, String> params = new LinkedHashMap<>();
            assertThat(AliyunSmsSigner.buildCanonicalQuery(params)).isEmpty();
        }

        @Test
        @DisplayName("null 值被当作空字符串处理")
        void shouldTreatNullValueAsEmptyString() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("key", null);

            assertThat(AliyunSmsSigner.buildCanonicalQuery(params)).isEqualTo("key=");
        }

        @Test
        @DisplayName("null 参数抛出空指针异常")
        void shouldThrowWhenParamsIsNull() {
            assertThatThrownBy(() -> AliyunSmsSigner.buildCanonicalQuery(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("含特殊字符的值被正确编码")
        void shouldEncodeSpecialCharValues() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("SignName", "测试 签名");

            String result = AliyunSmsSigner.buildCanonicalQuery(params);
            assertThat(result).isEqualTo("SignName=%E6%B5%8B%E8%AF%95%20%E7%AD%BE%E5%90%8D");
        }
    }

    @Nested
    @DisplayName("buildQuery() 查询字符串拼接")
    class BuildQueryTest {

        @Test
        @DisplayName("按插入顺序拼接查询串（不排序）")
        void shouldBuildQueryInInsertionOrder() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("b", "2");
            params.put("a", "1");
            params.put("c", "3");

            // buildQuery 不排序，保持原始插入顺序
            assertThat(AliyunSmsSigner.buildQuery(params)).isEqualTo("b=2&a=1&c=3");
        }

        @Test
        @DisplayName("空参数返回空字符串")
        void shouldReturnEmptyStringForEmptyParams() {
            Map<String, String> params = new LinkedHashMap<>();
            assertThat(AliyunSmsSigner.buildQuery(params)).isEmpty();
        }

        @Test
        @DisplayName("null 值被当作空字符串处理")
        void shouldTreatNullValueAsEmptyString() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("key", null);

            assertThat(AliyunSmsSigner.buildQuery(params)).isEqualTo("key=");
        }

        @Test
        @DisplayName("null 参数抛出空指针异常")
        void shouldThrowWhenParamsIsNull() {
            assertThatThrownBy(() -> AliyunSmsSigner.buildQuery(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("含特殊字符的键值被正确编码")
        void shouldEncodeSpecialCharacters() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("a b", "c+d");

            // 键中的空格 → %20，值中的 + → %2B
            assertThat(AliyunSmsSigner.buildQuery(params)).isEqualTo("a%20b=c%2Bd");
        }
    }
}
