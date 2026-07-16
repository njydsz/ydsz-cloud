package com.njydsz.pmis.common.util.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM 加密器（认证加密 AEAD）
 *
 * <p>提供认证加密（AEAD）能力：除机密性外，还能检测密文被篡改。
 * 每次加密都生成全新的随机 12 字节 IV，确保 GCM 安全性。</p>
 *
 * <p><b>密文格式：</b>{@code <12 bytes IV> || <ciphertext+16 bytes GCM tag>}</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 1. 创建加密器（Key 长度必须为 16/24/32 字节）
 * byte[] key = new byte[32];
 * new SecureRandom().nextBytes(key);
 * AesGcmCrypto crypto = new AesGcmCrypto(key);
 *
 * // 2. 加密
 * String ct = crypto.encrypt("plaintext");
 *
 * // 3. 解密
 * String pt = crypto.decrypt(ct);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class AesGcmCrypto {

    /** GCM 认证 Tag 长度（位） */
    public static final int GCM_TAG_LENGTH = 128;
    /** IV 长度（字节） */
    public static final int IV_LENGTH = 12;

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final String KEY_ALG = "AES";

    private static final int GCM_TAG_BYTES = GCM_TAG_LENGTH / 8;

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    /**
     * 创建 AES-GCM 加密器
     *
     * @param key 主密钥（16/24/32 字节）
     */
    public AesGcmCrypto(byte[] key) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException("AES key length must be 16/24/32 bytes");
        }
        this.keySpec = new SecretKeySpec(key, KEY_ALG);
    }

    /**
     * 加密并返回 Base64 字符串
     *
     * <p>每次加密生成全新的随机 IV，确保 GCM 安全性。
     * IV 拼接在密文头部，解密时自动提取。</p>
     *
     * @param plaintext 明文
     * @return Base64 编码的密文（IV || ciphertext+tag）
     */
    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] iv = generateRandomIv();
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /**
     * 加密并返回 Base64 字符串（带 keyId 参数，向后兼容）
     *
     * @param plaintext 明文
     * @param keyId     业务 keyId（当前实现忽略此参数）
     * @return Base64 编码的密文（IV || ciphertext+tag）
     * @deprecated 使用 {@link #encrypt(String)} 替代
     */
    @Deprecated(since = "1.3.0", forRemoval = true)
    public String encrypt(String plaintext, String keyId) {
        return encrypt(plaintext);
    }

    /**
     * 解密 Base64 密文
     *
     * @param base64Ciphertext Base64 编码的密文
     * @return 明文
     */
    public String decrypt(String base64Ciphertext) {
        Objects.requireNonNull(base64Ciphertext, "ciphertext must not be null");
        byte[] combined = Base64.getDecoder().decode(base64Ciphertext);
        if (combined.length < IV_LENGTH + GCM_TAG_BYTES) {
            throw new IllegalArgumentException("Invalid ciphertext length");
        }
        byte[] iv = new byte[IV_LENGTH];
        byte[] ct = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        System.arraycopy(combined, IV_LENGTH, ct, 0, ct.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    /**
     * 解密 Base64 密文（带 keyId 参数，向后兼容）
     *
     * @param base64Ciphertext Base64 编码的密文
     * @param keyId            业务 keyId（当前实现忽略此参数）
     * @return 明文
     * @deprecated 使用 {@link #decrypt(String)} 替代
     */
    @Deprecated(since = "1.3.0", forRemoval = true)
    public String decrypt(String base64Ciphertext, String keyId) {
        return decrypt(base64Ciphertext);
    }

    /**
     * 生成随机 IV
     *
     * <p>GCM 模式下 IV 必须唯一（不可复用），使用 SecureRandom 生成 12 字节随机值。
     * 12 字节 IV 空间为 2^96，在合理的使用周期内碰撞概率可忽略。</p>
     *
     * @return 12 字节随机 IV
     */
    private byte[] generateRandomIv() {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        return iv;
    }
}
