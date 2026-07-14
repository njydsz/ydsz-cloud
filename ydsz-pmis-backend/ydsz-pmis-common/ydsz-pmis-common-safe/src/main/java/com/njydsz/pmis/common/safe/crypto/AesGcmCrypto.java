package com.njydsz.pmis.common.safe.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM 加解密工具。
 *
 * <p>提供基于 AES-GCM 模式的加解密能力，支持：
 * <ul>
 *   <li>标准 AES-GCM 加密/解密（无 AAD）</li>
 *   <li>带 AAD（Associated Auth Data）的 AES-GCM 加密/解密</li>
 *   <li>AAD 用于防止密文被篡改后重放，AAD 参与认证但不被加密</li>
 *   <li>密文格式：[IV(12字节)][密文][AuthTag(16字节)]，全部 Base64 编码</li>
 * </ul>
 *
 * <p><b>密文结构：</b>
 * <pre>
 * Base64( IV(12 bytes) || ciphertext || auth_tag(16 bytes) )
 * </pre>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 无 AAD 的加密/解密
 * byte[] ciphertext = AesGcmCrypto.encrypt(plaintext, key);
 * byte[] decrypted = AesGcmCrypto.decrypt(ciphertext, key);
 *
 * // 带 AAD 的加密/解密（防止密文被篡改后重放）
 * String aad = "userId=123&timestamp=1234567890";
 * byte[] ciphertext = AesGcmCrypto.encryptWithAAD(plaintext, key, aad);
 * byte[] decrypted = AesGcmCrypto.decryptWithAAD(ciphertext, key, aad);
 * }</pre>
 *
 * <p><b>安全建议：</b>
 * <ul>
 *   <li>密钥长度推荐使用 256 位</li>
 *   <li>每次加密使用随机 IV，确保同一明文产生不同密文</li>
 *   <li>AAD 应包含请求的上下文信息（如 userId、timestamp 等）</li>
 *   <li>密钥应安全存储，建议使用密钥管理服务（KMS）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public final class AesGcmCrypto {

    /**
     * 算法名称
     */
    private static final String ALGORITHM = "AES";

    /**
     * GCM 模式
     */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * IV 长度（字节），推荐 12 字节（96 位）
     */
    private static final int IV_LENGTH_BYTES = 12;

    /**
     * GCM 认证标签长度（位），推荐 128 位
     */
    private static final int TAG_LENGTH_BITS = 128;

    /**
     * 安全随机数生成器
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCrypto() {
    }

    /**
     * 生成 AES 密钥。
     *
     * @param keyLengthBits 密钥长度（位），推荐 256
     * @return Base64 编码的密钥字符串
     */
    public static String generateKey(int keyLengthBits) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(keyLengthBits, SECURE_RANDOM);
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new CryptoException("Failed to generate AES key", e);
        }
    }

    /**
     * AES-GCM 加密（无 AAD）。
     *
     * @param plaintext 明文
     * @param keyBase64 Base64 编码的密钥
     * @return Base64 编码的密文（包含 IV + 密文 + AuthTag）
     */
    public static String encrypt(String plaintext, String keyBase64) {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), keyBase64);
    }

    /**
     * AES-GCM 加密（无 AAD）。
     *
     * @param plaintext 明文字节
     * @param keyBase64 Base64 编码的密钥
     * @return Base64 编码的密文（包含 IV + 密文 + AuthTag）
     */
    public static String encrypt(byte[] plaintext, String keyBase64) {
        Objects.requireNonNull(plaintext, "plaintext cannot be null");
        Objects.requireNonNull(keyBase64, "key cannot be null");

        try {
            SecretKey key = loadKey(keyBase64);
            byte[] iv = generateIV();

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] authTag = new byte[TAG_LENGTH_BITS / 8];
            System.arraycopy(ciphertext, ciphertext.length - authTag.length, authTag, 0, authTag.length);
            byte[] actualCiphertext = new byte[ciphertext.length - authTag.length];
            System.arraycopy(ciphertext, 0, actualCiphertext, 0, actualCiphertext.length);

            // Combine IV + ciphertext + authTag
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + actualCiphertext.length + authTag.length);
            buffer.put(iv);
            buffer.put(actualCiphertext);
            buffer.put(authTag);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new CryptoException("AES-GCM encryption failed", e);
        }
    }

    /**
     * AES-GCM 解密（无 AAD）。
     *
     * @param ciphertextBase64 Base64 编码的密文（包含 IV + 密文 + AuthTag）
     * @param keyBase64        Base64 编码的密钥
     * @return 明文字符串
     */
    public static String decrypt(String ciphertextBase64, String keyBase64) {
        byte[] decrypted = decryptToBytes(ciphertextBase64, keyBase64);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * AES-GCM 解密（无 AAD）。
     *
     * @param ciphertextBase64 Base64 编码的密文
     * @param keyBase64        Base64 编码的密钥
     * @return 明文字节
     */
    public static byte[] decryptToBytes(String ciphertextBase64, String keyBase64) {
        Objects.requireNonNull(ciphertextBase64, "ciphertext cannot be null");
        Objects.requireNonNull(keyBase64, "key cannot be null");

        try {
            SecretKey key = loadKey(keyBase64);
            byte[] combined = Base64.getDecoder().decode(ciphertextBase64);

            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] ciphertextAndTag = new byte[buffer.remaining()];
            buffer.get(ciphertextAndTag);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            return cipher.doFinal(ciphertextAndTag);
        } catch (Exception e) {
            throw new CryptoException("AES-GCM decryption failed", e);
        }
    }

    /**
     * AES-GCM 加密（带 AAD）。
     *
     * <p>AAD（Associated Auth Data）参与认证但不被加密，
     * 可用于绑定上下文信息（如 userId、timestamp 等），防止密文被篡改后重放。
     * 解密时必须提供相同的 AAD，否则认证失败。
     *
     * @param plaintext 明文
     * @param keyBase64 Base64 编码的密钥
     * @param aad       关联认证数据（不会被加密，但会参与认证）
     * @return Base64 编码的密文（包含 IV + 密文 + AuthTag）
     */
    public static String encryptWithAAD(String plaintext, String keyBase64, String aad) {
        Objects.requireNonNull(aad, "aad cannot be null");
        return encryptWithAAD(plaintext.getBytes(StandardCharsets.UTF_8), keyBase64, aad.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * AES-GCM 加密（带 AAD）。
     *
     * @param plaintext 明文字节
     * @param keyBase64 Base64 编码的密钥
     * @param aad       关联认证数据字节
     * @return Base64 编码的密文
     */
    public static String encryptWithAAD(byte[] plaintext, String keyBase64, byte[] aad) {
        Objects.requireNonNull(plaintext, "plaintext cannot be null");
        Objects.requireNonNull(keyBase64, "key cannot be null");
        Objects.requireNonNull(aad, "aad cannot be null");

        try {
            SecretKey key = loadKey(keyBase64);
            byte[] iv = generateIV();

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            // Update AAD before encryption
            cipher.updateAAD(aad);

            byte[] ciphertextWithTag = cipher.doFinal(plaintext);
            // Extract ciphertext and auth tag
            int tagLength = TAG_LENGTH_BITS / 8;
            byte[] ciphertext = new byte[ciphertextWithTag.length - tagLength];
            byte[] authTag = new byte[tagLength];
            System.arraycopy(ciphertextWithTag, 0, ciphertext, 0, ciphertext.length);
            System.arraycopy(ciphertextWithTag, ciphertext.length, authTag, 0, authTag.length);

            // Combine IV + ciphertext + authTag
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length + authTag.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            buffer.put(authTag);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new CryptoException("AES-GCM encryption with AAD failed", e);
        }
    }

    /**
     * AES-GCM 解密（带 AAD）。
     *
     * <p>必须提供与加密时相同的 AAD，否则认证失败。
     *
     * @param ciphertextBase64 Base64 编码的密文
     * @param keyBase64        Base64 编码的密钥
     * @param aad              关联认证数据
     * @return 明文字符串
     */
    public static String decryptWithAAD(String ciphertextBase64, String keyBase64, String aad) {
        Objects.requireNonNull(aad, "aad cannot be null");
        byte[] decrypted = decryptToBytesWithAAD(ciphertextBase64, keyBase64, aad.getBytes(StandardCharsets.UTF_8));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * AES-GCM 解密（带 AAD）。
     *
     * @param ciphertextBase64 Base64 编码的密文
     * @param keyBase64        Base64 编码的密钥
     * @param aad              关联认证数据字节
     * @return 明文字节
     */
    public static byte[] decryptToBytesWithAAD(String ciphertextBase64, String keyBase64, byte[] aad) {
        Objects.requireNonNull(ciphertextBase64, "ciphertext cannot be null");
        Objects.requireNonNull(keyBase64, "key cannot be null");
        Objects.requireNonNull(aad, "aad cannot be null");

        try {
            SecretKey key = loadKey(keyBase64);
            byte[] combined = Base64.getDecoder().decode(ciphertextBase64);

            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] ciphertextAndTag = new byte[buffer.remaining()];
            buffer.get(ciphertextAndTag);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            // Update AAD before decryption
            cipher.updateAAD(aad);

            return cipher.doFinal(ciphertextAndTag);
        } catch (AEADBadTagException e) {
            throw new CryptoException("AES-GCM decryption with AAD failed: authentication tag mismatch (可能密文被篡改或 AAD 不匹配)", e);
        } catch (Exception e) {
            throw new CryptoException("AES-GCM decryption with AAD failed", e);
        }
    }

    /**
     * 从 Base64 字符串加载密钥。
     *
     * @param keyBase64 Base64 编码的密钥
     * @return SecretKey 对象
     */
    private static SecretKey loadKey(String keyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, ALGORITHM);
    }

    /**
     * 生成随机 IV。
     *
     * @return IV 字节数组
     */
    private static byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * 加密异常。
     */
    public static class CryptoException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }

        public CryptoException(String message) {
            super(message);
        }
    }
}
