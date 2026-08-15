package com.njydsz.common.util.security.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/**
 * 业务加密工具类——项目中所有加密操作的唯一入口。
 *
 * <p>封装 Base64/Hex 编解码 + 算法路由，业务方只需：
 * <pre>{@code
 *   // 加密（Base64 密钥输入 → Base64 密文输出）
 *   String ciphertext = CryptoUtils.encrypt("Hello", Base64.getDecoder().decode(keyB64));
 *   // 解密
 *   String plaintext = CryptoUtils.decrypt(ciphertext, Base64.getDecoder().decode(keyB64));
 * }</pre>
 *
 * <h2>算法选择</h2>
 * <p>通过系统属性 {@code crypto.algorithm} 配置，默认 AES-256-GCM。
 * 国密合规系统可配置为 {@code SM4-GCM}。
 *
 * <h2>扩展能力</h2>
 * <p>支持 AAD（Additional Authenticated Data）的 AEAD 加密，
 * 用于带上下文的加密场景（如用户 ID 绑定密文防串用）。
 *
 * @author ydsz-team
 * @since 3.0.0
 * @see CryptoProvider
 * @see CryptoProviderRegistry
 */
public final class CryptoUtils {

    private static final HexFormat HEX = HexFormat.of();

    private static volatile CryptoProvider defaultProvider;

    private CryptoUtils() {
        throw new UnsupportedOperationException("CryptoUtils is a utility class");
    }

    // ==================== 字符串加密（Base64 编码） ====================

    /**
     * 使用默认算法加密字符串（Base64 密钥输入 → Base64 密文输出）。
     *
     * @param plaintext 明文字符串（UTF-8 编码）；不可为 null
     * @param key       密钥字节数组（Base64 解码后）
     * @return Base64 编码密文
     */
    public static String encrypt(String plaintext, byte[] key) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] ciphertext = provider().encrypt(
                plaintext.getBytes(StandardCharsets.UTF_8), key, null);
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * 使用默认算法解密字符串。
     *
     * @param base64Ciphertext Base64 编码密文；不可为 null
     * @param key              密钥字节数组（Base64 解码后）
     * @return 明文字符串（UTF-8 解码）
     */
    public static String decrypt(String base64Ciphertext, byte[] key) {
        Objects.requireNonNull(base64Ciphertext, "base64Ciphertext must not be null");
        byte[] ciphertext = Base64.getDecoder().decode(base64Ciphertext);
        byte[] plaintext = provider().decrypt(ciphertext, key, null);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    // ==================== 字符串加密（Hex 编码） ====================

    /**
     * 使用默认算法加密字符串（Hex 密钥输入 → Hex 密文输出）。
     *
     * @param plaintext 明文字符串（UTF-8 编码）
     * @param hexKey    Hex 编码密钥
     * @return Hex 编码密文
     */
    public static String encryptHex(String plaintext, String hexKey) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        Objects.requireNonNull(hexKey, "hexKey must not be null");
        byte[] key = HEX.parseHex(hexKey);
        byte[] ciphertext = provider().encrypt(
                plaintext.getBytes(StandardCharsets.UTF_8), key, null);
        return HEX.formatHex(ciphertext);
    }

    /**
     * 使用默认算法解密 Hex 编码密文。
     *
     * @param hexCiphertext Hex 编码密文
     * @param hexKey        Hex 编码密钥
     * @return 明文字符串（UTF-8 解码）
     */
    public static String decryptHex(String hexCiphertext, String hexKey) {
        Objects.requireNonNull(hexCiphertext, "hexCiphertext must not be null");
        Objects.requireNonNull(hexKey, "hexKey must not be null");
        byte[] ciphertext = HEX.parseHex(hexCiphertext);
        byte[] key = HEX.parseHex(hexKey);
        byte[] plaintext = provider().decrypt(ciphertext, key, null);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    // ==================== AEAD 加密（带 AAD） ====================

    /**
     * 使用 AAD 的 AEAD 加密——密文与上下文绑定，防串用。
     *
     * <p>解密时必须使用相同的 aad，否则认证失败。
     *
     * @param plaintext 明文字符串
     * @param key       密钥字节数组
     * @param aad       附加认证数据（如 userId、tenantId），解密时需一致
     * @return Base64 编码密文
     */
    public static String encryptWithAad(String plaintext, byte[] key, byte[] aad) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] ciphertext = provider().encrypt(
                plaintext.getBytes(StandardCharsets.UTF_8), key, aad);
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * 使用 AAD 的 AEAD 解密。
     *
     * @param base64Ciphertext Base64 编码密文
     * @param key              密钥字节数组
     * @param aad              附加认证数据（必须与加密时一致）
     * @return 明文字符串
     */
    public static String decryptWithAad(String base64Ciphertext, byte[] key, byte[] aad) {
        Objects.requireNonNull(base64Ciphertext, "base64Ciphertext must not be null");
        byte[] ciphertext = Base64.getDecoder().decode(base64Ciphertext);
        byte[] plaintext = provider().decrypt(ciphertext, key, aad);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    // ==================== 原始字节数组操作 ====================

    /**
     * 加密字节数组（高级场景，业务方自行处理编解码）。
     *
     * @param plaintext 明文字节数组
     * @param key       密钥字节数组
     * @param aad       可选 AAD
     * @return 密文字节数组（IV + ciphertext + tag）
     */
    public static byte[] encryptBytes(byte[] plaintext, byte[] key, byte[] aad) {
        return provider().encrypt(plaintext, key, aad);
    }

    /**
     * 解密字节数组（高级场景）。
     *
     * @param ciphertext 密文字节数组（IV + ciphertext + tag）
     * @param key        密钥字节数组
     * @param aad        可选 AAD
     * @return 明文字节数组
     */
    public static byte[] decryptBytes(byte[] ciphertext, byte[] key, byte[] aad) {
        return provider().decrypt(ciphertext, key, aad);
    }

    // ==================== 算法路由 ====================

    /**
     * 获取当前默认算法提供者。
     *
     * @return 默认 CryptoProvider
     */
    public static CryptoProvider provider() {
        if (defaultProvider == null) {
            String algo = System.getProperty("crypto.algorithm", "AES-256-GCM");
            defaultProvider = CryptoProviderRegistry.get(algo);
        }
        return defaultProvider;
    }

    /**
     * 获取指定算法的提供者（显式控制，不依赖系统属性）。
     *
     * @param algorithm 算法标识
     * @return 对应的 CryptoProvider
     */
    public static CryptoProvider provider(String algorithm) {
        return CryptoProviderRegistry.get(algorithm);
    }

    /**
     * 获取所有可用算法。
     *
     * @return 已注册的算法标识集合
     */
    public static Set<String> availableAlgorithms() {
        return CryptoProviderRegistry.availableAlgorithms();
    }
}

















