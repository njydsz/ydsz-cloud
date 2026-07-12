package com.njydsz.pmis.common.util.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * AES-GCM 加密器（带 Nonce 持久化）
 *
 * <p>提供认证加密（AEAD）能力：除机密性外，还能检测密文被篡改。
 * 12 字节 IV 持久化到外部存储（Redis/文件），避免 Nonce 重用导致的安全漏洞。</p>
 *
 * <p><b>密文格式：</b>{@code <12 bytes IV> || <ciphertext+16 bytes GCM tag>}</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 1. 创建加密器（Key 长度必须为 16/24/32 字节）
 * byte[] key = new byte[32];
 * new SecureRandom().nextBytes(key);
 * AesGcmCrypto crypto = new AesGcmCrypto(key, iv -> redis.set("nonce:" + keyId, iv));
 *
 * // 2. 加密
 * String ct = crypto.encrypt("plaintext", "key-1");
 *
 * // 3. 解密
 * String pt = crypto.decrypt(ct, "key-1");
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public class AesGcmCrypto {

    /** GCM 认证 Tag 长度（位） */
    public static final int GCM_TAG_LENGTH = 128;
    /** IV 长度（字节） */
    public static final int IV_LENGTH = 12;

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final String KEY_ALG = "AES";

    private final SecretKeySpec keySpec;
    private final NoncePersistor persistor;
    private final SecureRandom random = new SecureRandom();

    /**
     * 创建 AES-GCM 加密器
     *
     * @param key       主密钥（16/24/32 字节）
     * @param persistor Nonce 持久化器
     */
    public AesGcmCrypto(byte[] key, NoncePersistor persistor) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException("AES key length must be 16/24/32 bytes");
        }
        this.keySpec = new SecretKeySpec(key, KEY_ALG);
        this.persistor = Objects.requireNonNull(persistor, "NoncePersistor must not be null");
    }

    /**
     * 加密并返回 Base64 字符串
     *
     * @param plaintext 明文
     * @param keyId     业务 keyId（用于 Nonce 持久化）
     * @return Base64 编码的密文（IV || ciphertext+tag）
     */
    public String encrypt(String plaintext, String keyId) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] iv = loadOrGenerateIv(keyId);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
     * @param keyId            业务 keyId
     * @return 明文
     */
    public String decrypt(String base64Ciphertext, String keyId) {
        Objects.requireNonNull(base64Ciphertext, "ciphertext must not be null");
        byte[] combined = Base64.getDecoder().decode(base64Ciphertext);
        if (combined.length < IV_LENGTH + 16) {
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
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    private byte[] loadOrGenerateIv(String keyId) {
        if (persistor != null) {
            byte[] stored = persistor.load(keyId);
            if (stored != null && stored.length == IV_LENGTH) {
                return stored;
            }
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        if (persistor != null) {
            persistor.save(keyId, iv);
        }
        return iv;
    }

    /**
     * Nonce 持久化器
     *
     * <p>实现可对接 Redis / Database / ZK 等存储。
     * 注意：Nonce 永不复用至关重要，需保证 persistor 的写入是原子的（SETNX）。</p>
     */
    public interface NoncePersistor {
        /**
         * 加载已存在的 Nonce
         *
         * @param keyId 业务 keyId
         * @return 已存在的 Nonce；不存在时返回 null
         */
        byte[] load(String keyId);

        /**
         * 持久化 Nonce
         *
         * @param keyId 业务 keyId
         * @param iv    12 字节 Nonce
         */
        void save(String keyId, byte[] iv);
    }
}
