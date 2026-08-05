package com.remisoft.common.util.security;

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
 * @author remi-team
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

    /**
     * 获取本线程的 Cipher 实例。
     *
     * <p>委托至统一 {@link JcaCipherPool} 池化逻辑，消除 ThreadLocal 重复代码。
     *
     * @return 本线程的 AES-GCM Cipher 实例
     * @since 2.0.0 迁移至统一的 JcaCipherPool
     */
    private static Cipher acquireCipher() {
        return JcaCipherPool.acquireAesGcmCipher();
    }

    /**
     * 共享的线程安全 SecureRandom 实例（SecureRandom 本身是线程安全的）。
     *
     * <p>避免每个 AesGcmCrypto 实例都构造一个 SecureRandom，降低创建开销。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec keySpec;

    /**
     * 校验 AES 密钥长度，必须为 16/24/32 字节。
     *
     * <p>该方法为 {@link AesUtils} 等上层调用方提供统一的字节级密钥校验入口，
     * 避免出现 Hex 字符长度与字节长度两套校验口径不一致的问题。
     *
     * @param key 待校验密钥
     * @throws IllegalArgumentException 当 key 为 null 或长度非 16/24/32 字节
     */
    public static void validateKey(byte[] key) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException("AES key length must be 16/24/32 bytes");
        }
    }

    /**
     * 创建 AES-GCM 加密器
     *
     * @param key 主密钥（16/24/32 字节）
     */
    public AesGcmCrypto(byte[] key) {
        validateKey(key);
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
        return encrypt(plaintext, null);
    }

    /**
     * 加密并返回 Base64 字符串（支持 AAD）
     *
     * <p>每次加密生成全新的随机 IV，确保 GCM 安全性。
     * IV 拼接在密文头部，解密时自动提取。</p>
     *
     * <p>AAD（Additional Authenticated Data）用于将上下文（如用户 ID、请求 ID）
     * 绑定到密文中，防止密文在不同上下文中被重放。
     * AAD 不加密仅认证，解密时需传入相同的 AAD。</p>
     *
     * @param plaintext 明文
     * @param aad       附加认证数据（可为 null，表示无 AAD）
     * @return Base64 编码的密文（IV || ciphertext+tag）
     */
    public String encrypt(String plaintext, byte[] aad) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] iv = generateRandomIv();
        try {
            Cipher cipher = acquireCipher();
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }
    /**
     * 解密 Base64 密文
     *
     * @param base64Ciphertext Base64 编码的密文
     * @return 明文
     */
    public String decrypt(String base64Ciphertext) {
        return decrypt(base64Ciphertext, null);
    }

    /**
     * 解密 Base64 密文（支持 AAD）
     *
     * @param base64Ciphertext Base64 编码的密文
     * @param aad              附加认证数据（需与加密时传入的一致，可为 null）
     * @return 明文
     */
    public String decrypt(String base64Ciphertext, byte[] aad) {
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
            Cipher cipher = acquireCipher();
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
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
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }
}
