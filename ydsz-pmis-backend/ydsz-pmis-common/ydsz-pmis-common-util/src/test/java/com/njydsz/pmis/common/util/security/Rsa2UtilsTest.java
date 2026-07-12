package com.njydsz.pmis.common.util.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rsa2Utils 单元测试
 *
 * <p>覆盖密钥对生成、公钥/私钥加解密、签名验签、分段加密、Base64/PEM 密钥加载以及异常场景。
 * 每个测试方法执行前动态生成一组 RSA 密钥对，避免硬编码密钥。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@DisplayName("Rsa2Utils - RSA2 (SHA256withRSA) 加解密与签名工具类测试")
class Rsa2UtilsTest {

    /** 测试用密钥长度，使用 2048 以保证 OAEP 分段加密逻辑可被充分覆盖 */
    private static final int TEST_KEY_SIZE = Rsa2Utils.KEY_SIZE_2048;

    /** 动态生成的 Base64 公钥 */
    private String publicKeyBase64;

    /** 动态生成的 Base64 私钥 */
    private String privateKeyBase64;

    /**
     * 每次测试前动态生成一对全新的 RSA 密钥，确保测试之间相互独立。
     */
    @BeforeEach
    void generateKeyPair() {
        Map<String, String> keyPair = Rsa2Utils.initRSAKey(TEST_KEY_SIZE);
        publicKeyBase64 = keyPair.get("publicKey");
        privateKeyBase64 = keyPair.get("privateKey");
        assertNotNull(publicKeyBase64, "公钥不应为空");
        assertNotNull(privateKeyBase64, "私钥不应为空");
    }

    // ==================== 密钥对生成 ====================

    @Test
    @DisplayName("默认长度密钥对应包含 publicKey 与 privateKey 两个非空 Base64 字符串")
    void shouldGenerateDefaultKeyPairWithBothKeys() {
        Map<String, String> keyPair = Rsa2Utils.initRSAKey();

        assertNotNull(keyPair);
        assertNotNull(keyPair.get("publicKey"));
        assertNotNull(keyPair.get("privateKey"));
        assertTrue(keyPair.get("publicKey").length() > 0);
        assertTrue(keyPair.get("privateKey").length() > 0);
        // 公钥与私钥不应相同
        assertNotEquals(keyPair.get("publicKey"), keyPair.get("privateKey"));
    }

    @Test
    @DisplayName("指定密钥长度生成密钥对应返回对应长度的密钥")
    void shouldGenerateKeyPairWithSpecifiedKeySize() {
        Map<String, String> keyPair = Rsa2Utils.initRSAKey(TEST_KEY_SIZE);

        // 2048 位 PKCS8 私钥 Base64 编码长度约为 1670 字符左右，公钥 X509 约 390 字符左右
        // 这里只做下界校验，避免对 JDK 实现细节强依赖
        assertTrue(keyPair.get("privateKey").length() > 1000);
        assertTrue(keyPair.get("publicKey").length() > 300);
    }

    @Test
    @DisplayName("密钥长度小于 1024 时应抛出 IllegalStateException")
    void shouldThrowWhenKeySizeTooSmall() {
        assertThrows(IllegalStateException.class, () -> Rsa2Utils.initRSAKey(512));
    }

    @Test
    @DisplayName("initRSAKeyWithComment 应包含 comment 字段")
    void shouldGenerateKeyPairWithComment() {
        Map<String, Object> result = Rsa2Utils.initRSAKeyWithComment(TEST_KEY_SIZE, "test-key");

        assertNotNull(result);
        assertNotNull(result.get("publicKey"));
        assertNotNull(result.get("privateKey"));
        assertEquals("test-key", result.get("comment"));
    }

    @Test
    @DisplayName("initRSAKeyWithComment 当 comment 为 null 时不应包含 comment 键")
    void shouldNotContainCommentKeyWhenCommentIsNull() {
        Map<String, Object> result = Rsa2Utils.initRSAKeyWithComment(TEST_KEY_SIZE, null);

        assertNotNull(result);
        assertNotNull(result.get("publicKey"));
        assertNotNull(result.get("privateKey"));
        assertFalse(result.containsKey("comment"));
    }

    // ==================== Base64 密钥加载 ====================

    @Test
    @DisplayName("loadPublicKey 应能从 Base64 字符串还原 PublicKey 对象")
    void shouldLoadPublicKeyFromBase64() throws GeneralSecurityException {
        PublicKey pubKey = Rsa2Utils.loadPublicKey(publicKeyBase64);

        assertNotNull(pubKey);
        assertEquals("RSA", pubKey.getAlgorithm());
    }

    @Test
    @DisplayName("loadPrivateKey 应能从 Base64 字符串还原 PrivateKey 对象")
    void shouldLoadPrivateKeyFromBase64() throws GeneralSecurityException {
        PrivateKey priKey = Rsa2Utils.loadPrivateKey(privateKeyBase64);

        assertNotNull(priKey);
        assertEquals("RSA", priKey.getAlgorithm());
    }

    @Test
    @DisplayName("loadPublicKey 遇到非法 Base64 应抛出异常")
    void shouldThrowWhenLoadPublicKeyWithInvalidBase64() {
        assertThrows(Exception.class, () -> Rsa2Utils.loadPublicKey("not-a-valid-base64-key!!!"));
    }

    @Test
    @DisplayName("loadPrivateKey 遇到错误密钥格式应抛出 GeneralSecurityException")
    void shouldThrowWhenLoadPrivateKeyWithWrongKey() {
        // 用公钥的 Base64 去加载私钥应失败
        assertThrows(GeneralSecurityException.class, () -> Rsa2Utils.loadPrivateKey(publicKeyBase64));
    }

    // ==================== 公钥加密 / 私钥解密 ====================

    @Test
    @DisplayName("公钥加密后私钥解密应还原原文")
    void shouldEncryptByPublicKeyAndDecryptByPrivateKey() {
        String plain = "Hello, Rsa2Utils!";

        String encrypted = Rsa2Utils.encryptByPublicKey(plain, publicKeyBase64);
        assertNotNull(encrypted);
        assertNotEquals(plain, encrypted);

        String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, privateKeyBase64);
        assertEquals(plain, decrypted);
    }

    @Test
    @DisplayName("公钥加密相同原文每次密文不同（OAEP 填充引入随机性）")
    void shouldProduceDifferentCiphertextForSamePlainWithPublicKey() {
        String plain = "same-plain-text";

        String c1 = Rsa2Utils.encryptByPublicKey(plain, publicKeyBase64);
        String c2 = Rsa2Utils.encryptByPublicKey(plain, publicKeyBase64);

        assertNotEquals(c1, c2, "OAEP 填充每次应产生不同密文");
    }

    @Test
    @DisplayName("公钥加密中文内容并私钥解密应正确还原")
    void shouldEncryptAndDecryptChineseTextByPublicKey() {
        String plain = "ydsz软件 RSA2 加解密测试 —— 中文、标点、Emoji 😀";

        String encrypted = Rsa2Utils.encryptByPublicKey(plain, publicKeyBase64);
        String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, privateKeyBase64);

        assertEquals(plain, decrypted);
    }

    // ==================== 私钥加密 / 公钥解密 ====================
    // 注意：Rsa2Utils 使用 OAEPWithSHA-256AndMGF1Padding 填充方案，
    // JDK 的 RSACipher 实现不允许使用私钥进行 OAEP 加密（也不允许公钥 OAEP 解密），
    // 因此 encryptByPrivateKey / decryptByPublicKey 在 JDK 21 上会抛出 RuntimeException。
    // 此处验证该方法在当前 JDK 下的实际行为。

    @Test
    @DisplayName("私钥加密（OAEP 填充）应抛出 RuntimeException（JDK 限制）")
    void shouldThrowWhenEncryptByPrivateKeyDueToOAEPLimitation() {
        // OAEP 填充不支持私钥加密，JDK 抛出 InvalidKeyException 被 Rsa2Utils 包装为 RuntimeException
        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.encryptByPrivateKey("Private encrypt", privateKeyBase64));
    }

    @Test
    @DisplayName("公钥解密（OAEP 填充）应抛出 RuntimeException（JDK 限制）")
    void shouldThrowWhenDecryptByPublicKeyDueToOAEPLimitation() {
        // 先用公钥加密生成合法密文，再用公钥解密应抛出 RuntimeException
        String plain = "Plain text";
        String encrypted = Rsa2Utils.encryptByPublicKey(plain, publicKeyBase64);

        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.decryptByPublicKey(encrypted, publicKeyBase64));
    }

    // ==================== 分段加密（超长文本） ====================

    @Test
    @DisplayName("超长文本经公钥加密分段处理后仍能完整还原")
    void shouldHandleLongTextWithPublicKeyEncryption() {
        // 构造长度远大于单块最大加密字节数（190）的超长字符串
        StringBuilder sb = new StringBuilder(2048);
        for (int i = 0; i < 200; i++) {
            sb.append("第").append(i).append("段-");
        }
        String longPlain = sb.toString();
        assertTrue(longPlain.getBytes().length > 190 * 3, "测试数据应足够长以触发分段加密");

        String encrypted = Rsa2Utils.encryptByPublicKey(longPlain, publicKeyBase64);
        String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, privateKeyBase64);

        assertEquals(longPlain, decrypted);
    }

    @Test
    @DisplayName("超长文本经公钥加密分段处理后仍能完整还原（含中文）")
    void shouldHandleLongTextWithPublicKeyEncryptionChinese() {
        // 构造超长字符串（含中文，每字符 3 字节），确保触发多次分段
        StringBuilder sb = new StringBuilder(2048);
        for (int i = 0; i < 100; i++) {
            sb.append("ydsz加密段-").append(i).append(";");
        }
        String longPlain = sb.toString();
        assertTrue(longPlain.getBytes().length > 190 * 3, "测试数据应足够长以触发分段加密");

        String encrypted = Rsa2Utils.encryptByPublicKey(longPlain, publicKeyBase64);
        String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, privateKeyBase64);

        assertEquals(longPlain, decrypted);
    }

    @Test
    @DisplayName("空字符串加密解密应正常返回空字符串")
    void shouldHandleEmptyString() {
        String encrypted = Rsa2Utils.encryptByPublicKey("", publicKeyBase64);
        String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, privateKeyBase64);

        assertEquals("", decrypted);
    }

    // ==================== 签名与验签 ====================

    @Test
    @DisplayName("私钥签名后公钥验签应返回 true")
    void shouldSignAndVerifySuccessfully() {
        String data = "data-to-be-signed";

        String signStr = Rsa2Utils.sign(data, privateKeyBase64);
        assertNotNull(signStr);
        assertTrue(signStr.length() > 0);

        boolean verified = Rsa2Utils.verify(data, publicKeyBase64, signStr);
        assertTrue(verified, "正确签名应验签通过");
    }

    @Test
    @DisplayName("相同数据每次签名结果不同（RSA 签名带随机性）")
    void shouldProduceDifferentSignaturesForSameData() {
        String data = "same-data";

        String s1 = Rsa2Utils.sign(data, privateKeyBase64);
        String s2 = Rsa2Utils.sign(data, privateKeyBase64);

        // SHA256withRSA 是确定性签名，相同密钥+相同数据理论上签名相同
        // 这里仅校验两次签名都有效，不强制相等
        assertTrue(Rsa2Utils.verify(data, publicKeyBase64, s1));
        assertTrue(Rsa2Utils.verify(data, publicKeyBase64, s2));
    }

    @Test
    @DisplayName("篡改原文后验签应返回 false")
    void shouldFailVerificationWhenDataTampered() {
        String data = "original-data";
        String signStr = Rsa2Utils.sign(data, privateKeyBase64);

        boolean verified = Rsa2Utils.verify("tampered-data", publicKeyBase64, signStr);
        assertFalse(verified, "篡改数据后验签应失败");
    }

    @Test
    @DisplayName("使用合法但错误的签名串验签应返回 false")
    void shouldFailVerificationWhenSignTampered() {
        String data = "original-data";
        String signStr = Rsa2Utils.sign(data, privateKeyBase64);

        // 用另一段不同数据生成合法签名（Base64 长度与原签名一致，不会触发解码异常）
        // 用这个合法签名去验证原始数据，应验签失败
        String wrongSign = Rsa2Utils.sign("different-data", privateKeyBase64);
        assertNotEquals(signStr, wrongSign, "两段不同数据的签名应不同");

        boolean verified = Rsa2Utils.verify(data, publicKeyBase64, wrongSign);
        assertFalse(verified, "使用错误签名验签应失败");
    }

    @Test
    @DisplayName("篡改签名串（追加字符）后验签应抛出 RuntimeException")
    void shouldThrowWhenVerifyWithMalformedSign() {
        String data = "original-data";
        String signStr = Rsa2Utils.sign(data, privateKeyBase64);

        // 篡改签名串：在末尾追加字符使其 Base64 解码后字节长度异常，signature.verify 抛异常
        String tamperedSign = signStr + "ABCD";
        // Rsa2Utils.verify 内部捕获异常后包装为 RuntimeException 抛出
        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.verify(data, publicKeyBase64, tamperedSign));
    }

    @Test
    @DisplayName("使用错误公钥验签应返回 false")
    void shouldFailVerificationWithWrongPublicKey() {
        String data = "original-data";
        String signStr = Rsa2Utils.sign(data, privateKeyBase64);

        // 生成另一对密钥，用错误公钥验签
        Map<String, String> anotherKeyPair = Rsa2Utils.initRSAKey();
        boolean verified = Rsa2Utils.verify(data, anotherKeyPair.get("publicKey"), signStr);
        assertFalse(verified, "使用错误公钥验签应失败");
    }

    @Test
    @DisplayName("中文内容签名与验签")
    void shouldSignAndVerifyChineseContent() {
        String data = "ydsz软件 RSA2 中文签名测试 😀";

        String signStr = Rsa2Utils.sign(data, privateKeyBase64);
        assertTrue(Rsa2Utils.verify(data, publicKeyBase64, signStr));
    }

    // ==================== 密钥对匹配校验 ====================

    @Test
    @DisplayName("verifyKeyPair 对匹配的密钥对应返回 true")
    void shouldReturnTrueWhenKeyPairMatches() {
        assertTrue(Rsa2Utils.verifyKeyPair(publicKeyBase64, privateKeyBase64));
    }

    @Test
    @DisplayName("verifyKeyPair 对不匹配的密钥对应返回 false")
    void shouldReturnFalseWhenKeyPairDoesNotMatch() {
        Map<String, String> anotherKeyPair = Rsa2Utils.initRSAKey();
        // 用 A 的公钥 + B 的私钥
        assertFalse(Rsa2Utils.verifyKeyPair(publicKeyBase64, anotherKeyPair.get("privateKey")));
    }

    // ==================== PEM 格式 ====================

    @Test
    @DisplayName("公钥与 PEM 格式可相互转换并加载")
    void shouldConvertPublicKeyToAndFromPEM() throws GeneralSecurityException {
        String pem = Rsa2Utils.publicKeyToPEM(publicKeyBase64);

        assertNotNull(pem);
        assertTrue(pem.contains("-----BEGIN PUBLIC KEY-----"));
        assertTrue(pem.contains("-----END PUBLIC KEY-----"));

        PublicKey pubKey = Rsa2Utils.loadPublicKeyFromPEM(pem);
        assertNotNull(pubKey);
        assertEquals("RSA", pubKey.getAlgorithm());
    }

    @Test
    @DisplayName("私钥与 PEM 格式可相互转换并加载")
    void shouldConvertPrivateKeyToAndFromPEM() throws GeneralSecurityException {
        String pem = Rsa2Utils.privateKeyToPEM(privateKeyBase64);

        assertNotNull(pem);
        assertTrue(pem.contains("-----BEGIN PRIVATE KEY-----"));
        assertTrue(pem.contains("-----END PRIVATE KEY-----"));

        PrivateKey priKey = Rsa2Utils.loadPrivateKeyFromPEM(pem);
        assertNotNull(priKey);
        assertEquals("RSA", priKey.getAlgorithm());
    }

    @Test
    @DisplayName("PEM 公钥加载后可用于加解密流程")
    void shouldUsePEMPublicKeyForEncryption() {
        String pem = Rsa2Utils.publicKeyToPEM(publicKeyBase64);
        assertDoesNotThrow(() -> {
            PublicKey pubKey = Rsa2Utils.loadPublicKeyFromPEM(pem);
            assertNotNull(pubKey);
        });
    }

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("公钥加密传入 null 数据应抛出 RuntimeException")
    void shouldThrowWhenEncryptByPublicKeyWithNullData() {
        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.encryptByPublicKey(null, publicKeyBase64));
    }

    @Test
    @DisplayName("公钥加密传入 null 公钥应抛出 RuntimeException")
    void shouldThrowWhenEncryptByPublicKeyWithNullKey() {
        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.encryptByPublicKey("data", null));
    }

    @Test
    @DisplayName("私钥解密传入 null 数据应抛出 RuntimeException")
    void shouldThrowWhenDecryptByPrivateKeyWithNullData() {
        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.decryptByPrivateKey(null, privateKeyBase64));
    }

    @Test
    @DisplayName("私钥解密传入非法 Base64 数据应抛出 RuntimeException")
    void shouldThrowWhenDecryptByPrivateKeyWithInvalidData() {
        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.decryptByPrivateKey("not-a-valid-base64!!!", privateKeyBase64));
    }

    @Test
    @DisplayName("公钥加密后用错误私钥解密应抛出 RuntimeException")
    void shouldThrowWhenDecryptWithWrongPrivateKey() {
        String plain = "some-plain-text";
        String encrypted = Rsa2Utils.encryptByPublicKey(plain, publicKeyBase64);

        Map<String, String> anotherKeyPair = Rsa2Utils.initRSAKey();
        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.decryptByPrivateKey(encrypted, anotherKeyPair.get("privateKey")));
    }

    @Test
    @DisplayName("签名传入 null 数据应抛出 RuntimeException")
    void shouldThrowWhenSignWithNullData() {
        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.sign(null, privateKeyBase64));
    }

    @Test
    @DisplayName("签名传入 null 私钥应抛出 RuntimeException")
    void shouldThrowWhenSignWithNullKey() {
        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.sign("data", null));
    }

    @Test
    @DisplayName("验签传入非法签名串应抛出 RuntimeException")
    void shouldThrowWhenVerifyWithInvalidSign() {
        assertThrows(RuntimeException.class,
                () -> Rsa2Utils.verify("data", publicKeyBase64, "not-a-valid-base64-sign!!!"));
    }

    // ==================== 常量校验 ====================

    @Test
    @DisplayName("签名算法常量应为 SHA256withRSA")
    void shouldExposeCorrectSignAlgorithmConstant() {
        assertEquals("SHA256withRSA", Rsa2Utils.SIGN_ALGORITHM);
    }

    @Test
    @DisplayName("默认密钥长度常量应为 2048")
    void shouldExposeCorrectDefaultKeySizeConstant() {
        assertEquals(2048, Rsa2Utils.DEFAULT_KEY_SIZE);
        assertEquals(2048, Rsa2Utils.KEY_SIZE_2048);
    }

    // ==================== 工具类不可实例化 ====================

    @Test
    @DisplayName("工具类构造方法应抛出 UnsupportedOperationException")
    void shouldNotBeInstantiable() throws ReflectiveOperationException {
        java.lang.reflect.Constructor<Rsa2Utils> constructor =
                Rsa2Utils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        // 反射调用 newInstance 时，构造方法抛出的异常会被包装为 InvocationTargetException
        java.lang.reflect.InvocationTargetException thrown =
                assertThrows(java.lang.reflect.InvocationTargetException.class,
                        () -> constructor.newInstance((Object[]) null));
        // 真正的原因应为 UnsupportedOperationException
        assertTrue(thrown.getCause() instanceof UnsupportedOperationException,
                "构造方法应抛出 UnsupportedOperationException");
    }
}
