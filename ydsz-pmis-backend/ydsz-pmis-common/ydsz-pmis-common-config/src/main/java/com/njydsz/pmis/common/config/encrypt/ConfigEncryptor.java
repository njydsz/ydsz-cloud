package com.njydsz.pmis.common.config.encrypt;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 配置加密器
 *
 * <p>使用 AES-256-GCM 算法对敏感配置进行加解密。
 * 密钥通过 SHA-256 派生，密文格式为 Base64(iv + ciphertext + tag)。
 *
 * <p>密文前缀为 {@code ENC(...)}，便于在配置文件中识别。
 *
 * <p>使用示例：
 * <pre>{@code
 * # application.yml
 * spring:
 *   datasource:
 *     password: ENC(xJ8kL2mN3pQ5rS7tU9vWxYz...)
 *
 * # 环境变量提供密钥
 * PMIS_CONFIG_ENCRYPT_KEY=my-secret-key
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class ConfigEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final String ENCRYPTED_PREFIX = "ENC(";
    private static final String ENCRYPTED_SUFFIX = ")";

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;

    /**
     * @param secretKeyStr 密钥字符串（将通过 SHA-256 派生为 AES-256 密钥）
     */
    public ConfigEncryptor(String secretKeyStr) {
        this.secretKey = deriveKey(secretKeyStr);
        this.secureRandom = new SecureRandom();
    }

    /**
     * 加密明文
     *
     * @param plaintext 明文
     * @return 加密后的密文，格式为 {@code ENC(Base64)}
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined) + ENCRYPTED_SUFFIX;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt config value", e);
        }
    }

    /**
     * 解密密文
     *
     * @param ciphertext 密文，格式为 {@code ENC(Base64)} 或纯 Base64
     * @return 解密后的明文
     */
    public String decrypt(String ciphertext) {
        String base64Value = stripEncWrapper(ciphertext);
        try {
            byte[] combined = Base64.getDecoder().decode(base64Value);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt config value", e);
        }
    }

    /**
     * 判断字符串是否为加密格式
     *
     * @param value 待检查的字符串
     * @return true 如果是 ENC(...) 格式
     */
    public boolean isEncrypted(String value) {
        return value != null
                && value.startsWith(ENCRYPTED_PREFIX)
                && value.endsWith(ENCRYPTED_SUFFIX);
    }

    private String stripEncWrapper(String value) {
        if (isEncrypted(value)) {
            return value.substring(ENCRYPTED_PREFIX.length(), value.length() - ENCRYPTED_SUFFIX.length());
        }
        return value;
    }

    private static SecretKeySpec deriveKey(String keyStr) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(keyStr.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }
}
