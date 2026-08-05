package com.remisoft.common.util.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * SM4 分组密码算法工具类
 *
 * <p>对称加密算法，分组长度 128 位，密钥长度 128 位（16 字节），安全性对标 AES-128，
 * 符合国密标准 GM/T 0002-2012。纯 BouncyCastle 实现。
 *
 * <h2>支持的工作模式</h2>
 * <ul>
 *   <li><b>GCM（推荐）</b>：认证加密 AEAD，同时保证机密性和完整性，自动 IV</li>
 *   <li><b>CBC</b>：传统分组模式，需外部管理 IV，已有系统兼容</li>
 * </ul>
 *
 * <p>通过 JCA {@link Cipher} 委托给 BouncyCastle Provider，
 * 需要 {@code bcprov-jdk18on} 在 classpath 上。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 生成密钥
 * String hexKey = Sm4Utils.initHexKey();
 *
 * // GCM 加密（推荐，自动 IV）
 * String ciphertext = Sm4Utils.encryptGcm("Hello SM4", hexKey);
 *
 * // GCM 解密
 * String plaintext = Sm4Utils.decryptGcm(ciphertext, hexKey);
 *
 * // CBC 加密（需传入 IV）
 * String iv = Sm4Utils.generateIvHex();
 * String ct = Sm4Utils.encryptCbc("data", hexKey, iv);
 * String pt = Sm4Utils.decryptCbc(ct, hexKey, iv);
 * }</pre>
 *
 * <p><b>密文格式：</b>
 * <ul>
 *   <li>GCM 模式：Base64(IV(12 字节) || ciphertext+GCM tag)</li>
 *   <li>CBC 模式：Base64(ciphertext)，IV 由调用方管理</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.5.0
 */
@Slf4j
public final class Sm4Utils {

    /** SM4 算法名称 */
    private static final String ALGORITHM = "SM4";

    /** GCM 模式转换字符串 */
    private static final String TRANSFORM_GCM = "SM4/GCM/NoPadding";

    /** CBC 模式转换字符串 */
    private static final String TRANSFORM_CBC = "SM4/CBC/PKCS5Padding";

    /** GCM Tag 长度（位） */
    private static final int GCM_TAG_LENGTH = 128;

    /** GCM IV 长度（字节） */
    private static final int GCM_IV_LENGTH = 12;

    /** CBC IV 长度（字节） = 分组长度 */
    private static final int CBC_IV_LENGTH = 16;

    /** SM4 密钥长度（字节） */
    private static final int KEY_LENGTH = 16;

    /** 共享的线程安全 SecureRandom */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Hex 编码器 */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /** GCM Cipher ThreadLocal 池化 */
    private static final ThreadLocal<Cipher> GCM_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(TRANSFORM_GCM, BouncyCastleProvider.PROVIDER_NAME);
        } catch (Exception e) {
            throw new IllegalStateException("SM4/GCM not available, ensure bcprov-jdk18on is on classpath", e);
        }
    });

    /** CBC Cipher ThreadLocal 池化 */
    private static final ThreadLocal<Cipher> CBC_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(TRANSFORM_CBC, BouncyCastleProvider.PROVIDER_NAME);
        } catch (Exception e) {
            throw new IllegalStateException("SM4/CBC not available, ensure bcprov-jdk18on is on classpath", e);
        }
    });

    private Sm4Utils() {
        throw new UnsupportedOperationException("Sm4Utils is a utility class");
    }

    // ==================== 密钥生成 ====================

    /**
     * 生成 SM4 密钥（Hex 编码）
     *
     * <p>使用 {@link KeyGenerator} 生成密码学安全的 128 位密钥。
     *
     * @return 32 字符 Hex 字符串
     */
    public static String initHexKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            kg.init(128, SECURE_RANDOM);
            return HEX_FORMAT.formatHex(kg.generateKey().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate SM4 key", e);
        }
    }

    /**
     * 生成 SM4 密钥（Base64 编码）
     *
     * @return Base64 编码密钥
     */
    public static String initBase64Key() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            kg.init(128, SECURE_RANDOM);
            return Base64.getEncoder().encodeToString(kg.generateKey().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate SM4 key", e);
        }
    }

    /**
     * 生成随机 IV（Hex 编码，CBC 模式使用）
     *
     * @return 32 字符 Hex 字符串
     */
    public static String generateIvHex() {
        byte[] iv = new byte[CBC_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return HEX_FORMAT.formatHex(iv);
    }

    /**
     * 生成随机 IV（字节数组，CBC 模式使用）
     *
     * @return 16 字节 IV
     */
    public static byte[] generateIv() {
        byte[] iv = new byte[CBC_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    // ==================== GCM 模式（推荐） ====================

    /**
     * SM4-GCM 加密（Hex 密钥，Base64 输出）
     *
     * <p>每次加密生成全新的随机 12 字节 IV，确保 GCM 安全性。
     * 密文格式：Base64(IV(12 字节) || ciphertext+GCM tag)。
     *
     * @param content   明文（UTF-8 编码）
     * @param hexKey    Hex 编码密钥（32 个字符）
     * @return Base64 密文
     * @throws IllegalArgumentException 密钥格式错误或 content 为 null
     */
    public static String encryptGcm(String content, String hexKey) {
        Objects.requireNonNull(content, "content must not be null");
        byte[] ciphertext = encryptGcm(content.getBytes(StandardCharsets.UTF_8), hexToBytes(hexKey));
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * SM4-GCM 加密（字节数组）
     *
     * <p>每次加密生成全新的随机 12 字节 IV。
     * 输出格式：IV(12 字节) || ciphertext+GCM tag(16 字节)。
     *
     * @param content 明文字节数组
     * @param key     密钥字节数组（16 字节）
     * @return 密文字节数组（IV + ciphertext + tag）
     */
    public static byte[] encryptGcm(byte[] content, byte[] key) {
        Objects.requireNonNull(content, "content must not be null");
        validateKey(key);
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        try {
            Cipher cipher = GCM_CIPHER.get();
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(content);
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (Exception e) {
            throw new IllegalStateException("SM4-GCM encryption failed", e);
        }
    }

    /**
     * SM4-GCM 解密（Hex 密钥，Base64 密文）
     *
     * @param base64Ciphertext Base64 编码密文
     * @param hexKey           Hex 编码密钥
     * @return 明文字符串（UTF-8）
     */
    public static String decryptGcm(String base64Ciphertext, String hexKey) {
        Objects.requireNonNull(base64Ciphertext, "ciphertext must not be null");
        byte[] plaintext = decryptGcm(Base64.getDecoder().decode(base64Ciphertext), hexToBytes(hexKey));
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * SM4-GCM 解密（字节数组）
     *
     * <p>自动从输入头部提取 IV，剩余部分作为 ciphertext+tag 验证并解密。
     *
     * @param ciphertext IV(12 字节) + ciphertext + tag(16 字节)
     * @param key        密钥字节数组（16 字节）
     * @return 明文字节数组
     */
    public static byte[] decryptGcm(byte[] ciphertext, byte[] key) {
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        validateKey(key);
        if (ciphertext.length < GCM_IV_LENGTH + (GCM_TAG_LENGTH / 8)) {
            throw new IllegalArgumentException("Invalid ciphertext length");
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        int ctLen = ciphertext.length - GCM_IV_LENGTH;
        byte[] ct = new byte[ctLen];
        System.arraycopy(ciphertext, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(ciphertext, GCM_IV_LENGTH, ct, 0, ctLen);
        try {
            Cipher cipher = GCM_CIPHER.get();
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("SM4-GCM decryption failed", e);
        }
    }

    // ==================== CBC 模式 ====================

    /**
     * SM4-CBC 加密（Hex 密钥 + Hex IV，Base64 输出）
     *
     * @param content   明文（UTF-8 编码）
     * @param hexKey    Hex 编码密钥
     * @param hexIv     Hex 编码 IV（CBC 需要）
     * @return Base64 密文
     */
    public static String encryptCbc(String content, String hexKey, String hexIv) {
        Objects.requireNonNull(content, "content must not be null");
        byte[] ciphertext = encryptCbc(
                content.getBytes(StandardCharsets.UTF_8),
                hexToBytes(hexKey),
                hexToBytes(hexIv)
        );
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * SM4-CBC 加密（字节数组）
     *
     * @param content 明文
     * @param key     密钥（16 字节）
     * @param iv      初始化向量（16 字节）
     * @return 密文（PKCS5 填充后结果）
     */
    public static byte[] encryptCbc(byte[] content, byte[] key, byte[] iv) {
        Objects.requireNonNull(content, "content must not be null");
        validateKey(key);
        validateIv(iv);
        try {
            Cipher cipher = CBC_CIPHER.get();
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new IvParameterSpec(iv));
            return cipher.doFinal(content);
        } catch (Exception e) {
            throw new IllegalStateException("SM4-CBC encryption failed", e);
        }
    }

    /**
     * SM4-CBC 解密（Hex 密钥 + Hex IV，Base64 密文）
     *
     * @param base64Ciphertext Base64 编码密文
     * @param hexKey           Hex 编码密钥
     * @param hexIv            Hex 编码 IV
     * @return 明文字符串（UTF-8）
     */
    public static String decryptCbc(String base64Ciphertext, String hexKey, String hexIv) {
        Objects.requireNonNull(base64Ciphertext, "ciphertext must not be null");
        byte[] plaintext = decryptCbc(
                Base64.getDecoder().decode(base64Ciphertext),
                hexToBytes(hexKey),
                hexToBytes(hexIv)
        );
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * SM4-CBC 解密（字节数组）
     *
     * @param ciphertext 密文
     * @param key        密钥（16 字节）
     * @param iv         初始化向量（16 字节）
     * @return 明文
     */
    public static byte[] decryptCbc(byte[] ciphertext, byte[] key, byte[] iv) {
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        validateKey(key);
        validateIv(iv);
        try {
            Cipher cipher = CBC_CIPHER.get();
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new IvParameterSpec(iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("SM4-CBC decryption failed", e);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 校验 SM4 密钥长度
     *
     * @param key 密钥字节数组
     * @throws IllegalArgumentException 密钥为 null 或长度不等于 16 字节
     */
    private static void validateKey(byte[] key) {
        if (key == null || key.length != KEY_LENGTH) {
            throw new IllegalArgumentException("SM4 key must be exactly " + KEY_LENGTH + " bytes (128 bits)");
        }
    }

    /**
     * 校验 IV 长度
     *
     * @param iv IV 字节数组
     * @throws IllegalArgumentException IV 为 null 或长度不等于 16 字节
     */
    private static void validateIv(byte[] iv) {
        if (iv == null || iv.length != CBC_IV_LENGTH) {
            throw new IllegalArgumentException("SM4 IV must be exactly " + CBC_IV_LENGTH + " bytes");
        }
    }

    /**
     * Hex 字符串转字节数组
     *
     * @param hex Hex 字符串（偶数长度）
     * @return 字节数组
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        return HEX_FORMAT.parseHex(hex);
    }

    /**
     * 字节数组转 Hex 字符串
     *
     * @param bytes 字节数组
     * @return Hex 字符串
     */
    public static String bytesToHex(byte[] bytes) {
        return HEX_FORMAT.formatHex(bytes);
    }
}
