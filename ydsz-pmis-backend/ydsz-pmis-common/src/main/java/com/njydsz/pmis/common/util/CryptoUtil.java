package com.njydsz.pmis.common.util;

import cn.hutool.core.util.StrUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.util.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

/**
 * 加密工具
 *
 * <p>提供 4 类能力:
 * <ul>
 *   <li>摘要: MD5 / SHA-256</li>
 *   <li>密码哈希: 随机盐 MD5 / PBKDF2-HMAC-SHA256</li>
 *   <li>对称加密: AES-256-GCM / SM4-GCM (国密, 需 BouncyCastle)</li>
 *   <li>HMAC 签名: SHA-256</li>
 * </ul>
 *
 * <p>AES/SM4 输出格式: {@code base64(IV(12字节) || ciphertext+tag)}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class CryptoUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SALT_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final String AES_TRANSFORM = "AES/GCM/NoPadding";
    private static final String SM4_TRANSFORM = "SM4/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private static volatile boolean bcRegistered = false;

    static {
        ensureBouncyCastle();
    }

    private CryptoUtil() {
    }

    private static synchronized void ensureBouncyCastle() {
        if (bcRegistered) return;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        bcRegistered = true;
    }

    // ==================== 摘要 ====================

    public static String md5(String input) {
        if (StrUtil.isBlank(input)) {
            return null;
        }
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(String input) {
        if (StrUtil.isBlank(input)) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== 密码哈希 ====================

    /**
     * 密码加盐 (MD5 + 8 位随机盐)
     *
     * <p>兼容历史数据，新系统建议改用 {@link #hashPasswordPBKDF2(String, byte[], int)}。
     *
     * @return [加密密码, 盐]
     */
    public static String[] encryptPassword(String rawPassword) {
        String salt = randomSalt(8);
        String encrypted = md5(rawPassword + salt);
        return new String[]{encrypted, salt};
    }

    public static boolean verifyPassword(String rawPassword, String encrypted, String salt) {
        if (StrUtil.hasBlank(rawPassword, encrypted, salt)) {
            return false;
        }
        return md5(rawPassword + salt).equals(encrypted);
    }

    /**
     * PBKDF2-HMAC-SHA256 密码哈希 (推荐)
     *
     * @param rawPassword  明文密码
     * @param salt         16 字节盐 (新盐用 {@link #randomBytes(int)})
     * @param iterations   迭代次数 (推荐 60000+)
     * @return 32 字节哈希的 Base64
     */
    public static String hashPasswordPBKDF2(String rawPassword, byte[] salt, int iterations) {
        if (StrUtil.isBlank(rawPassword)) return null;
        if (salt == null || salt.length < 8) {
            throw new IllegalArgumentException("salt 至少 8 字节");
        }
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 失败", e);
        }
    }

    public static boolean verifyPasswordPBKDF2(String rawPassword, byte[] salt, int iterations, String expectedHash) {
        if (StrUtil.isBlank(rawPassword) || expectedHash == null) return false;
        String actual = hashPasswordPBKDF2(rawPassword, salt, iterations);
        return constantTimeEquals(expectedHash, actual);
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        return java.security.MessageDigest.isEqual(ab, bb);
    }

    // ==================== 随机 ====================

    public static String randomSalt(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SALT_CHARS.charAt(RANDOM.nextInt(SALT_CHARS.length())));
        }
        return sb.toString();
    }

    public static byte[] randomBytes(int length) {
        byte[] b = new byte[length];
        RANDOM.nextBytes(b);
        return b;
    }

    // ==================== Base64 ====================

    public static String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] base64Decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    public static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    // ==================== AES-256-GCM ====================

    /**
     * AES-256-GCM 加密
     *
     * @param plaintext 明文
     * @param key       32 字节密钥
     * @return base64(IV || ciphertext+tag)
     */
    public static String aesGcmEncrypt(String plaintext, byte[] key) {
        if (plaintext == null) return null;
        validateAesKey(key);
        try {
            byte[] iv = randomBytes(GCM_IV_LEN);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(AES_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 加密失败", e);
        }
    }

    public static String aesGcmDecrypt(String ciphertextB64, byte[] key) {
        if (StrUtil.isBlank(ciphertextB64)) return null;
        validateAesKey(key);
        try {
            byte[] all = Base64.getDecoder().decode(ciphertextB64);
            if (all.length <= GCM_IV_LEN) {
                throw new IllegalArgumentException("密文长度异常");
            }
            byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_LEN);
            byte[] ct = Arrays.copyOfRange(all, GCM_IV_LEN, all.length);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(AES_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 解密失败", e);
        }
    }

    private static void validateAesKey(byte[] key) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("AES-256 要求 32 字节密钥, 实际: "
                    + (key == null ? 0 : key.length));
        }
    }

    // ==================== SM4-GCM (国密) ====================

    /**
     * SM4-GCM 加密 (国密)
     *
     * <p>底层由 BouncyCastle 提供; 输出格式与 AES-GCM 一致: base64(IV || ct+tag)。
     *
     * @param plaintext 明文
     * @param key       16 字节密钥
     */
    public static String sm4GcmEncrypt(String plaintext, byte[] key) {
        if (plaintext == null) return null;
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("SM4 要求 16 字节密钥, 实际: " + (key == null ? 0 : key.length));
        }
        try {
            byte[] iv = randomBytes(GCM_IV_LEN);
            SecretKey keySpec = new SecretKeySpec(key, "SM4");
            Cipher cipher = Cipher.getInstance(SM4_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("SM4-GCM 加密失败", e);
        }
    }

    public static String sm4GcmDecrypt(String ciphertextB64, byte[] key) {
        if (StrUtil.isBlank(ciphertextB64)) return null;
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("SM4 要求 16 字节密钥, 实际: " + (key == null ? 0 : key.length));
        }
        try {
            byte[] all = Base64.getDecoder().decode(ciphertextB64);
            if (all.length <= GCM_IV_LEN) {
                throw new IllegalArgumentException("密文长度异常");
            }
            byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_LEN);
            byte[] ct = Arrays.copyOfRange(all, GCM_IV_LEN, all.length);
            SecretKey keySpec = new SecretKeySpec(key, "SM4");
            Cipher cipher = Cipher.getInstance(SM4_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("SM4-GCM 解密失败", e);
        }
    }

    // ==================== HMAC ====================

    public static String hmacSha256(String data, byte[] key) {
        if (StrUtil.isBlank(data) || key == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 失败", e);
        }
    }
}
