package com.njydsz.pmis.common.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加密组件 —— 提供认证加密（Authenticated Encryption）。
 * <p>
 * 对标 remi-comm AesGcmCrypto，GCM 模式同时提供机密性和完整性保护，
 * 适用于敏感字段加密（身份证号、手机号等）。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public class AesGcmCrypto {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12;   // bytes

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;

    /**
     * 构造 AES-GCM 加密器。
     *
     * @param key 密钥（16/24/32 字节对应 AES-128/192/256）
     */
    public AesGcmCrypto(byte[] key) {
        this.secretKey = new SecretKeySpec(key, ALGORITHM);
        this.secureRandom = new SecureRandom();
    }

    /**
     * 加密并返回 Base64 编码的密文。
     * <p>
     * 格式：Base64(IV || Ciphertext || AuthTag)
     * </p>
     *
     * @param plaintext 明文
     * @return Base64 编码的密文
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV + Ciphertext (含 AuthTag)
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new SecurityException("AES-GCM encryption failed", e);
        }
    }

    /**
     * 解密 Base64 编码的密文。
     *
     * @param encrypted Base64 编码的密文
     * @return 明文
     */
    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("AES-GCM decryption failed", e);
        }
    }

    /**
     * 加密为字节数组（不 Base64 编码）。
     *
     * @param plaintext 明文字节
     * @return IV + 密文
     */
    public byte[] encryptBytes(byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return combined;
        } catch (Exception e) {
            throw new SecurityException("AES-GCM encryption failed", e);
        }
    }

    /**
     * 解密字节数组。
     *
     * @param encrypted IV + 密文
     * @return 明文字节
     */
    public byte[] decryptBytes(byte[] encrypted) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[encrypted.length - GCM_IV_LENGTH];
            System.arraycopy(encrypted, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encrypted, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new SecurityException("AES-GCM decryption failed", e);
        }
    }
}
