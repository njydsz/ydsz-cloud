package com.njydsz.common.util.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import com.njydsz.common.util.security.crypto.CryptoProvider;
import com.njydsz.common.util.security.crypto.CryptoProviderRegistry;
import com.njydsz.common.util.security.HexUtils;

/**
 * AES 加密工具类（轻量静态工具）
 *
 * <p>提供 AES-GCM 对称加密解密、密钥生成、Base64/Hex 编解码能力。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li><b>GCM 模式</b>：encrypt/decrypt 使用 AES-GCM，提供认证加密（AEAD）</li>
 *   <li><b>自动生成 IV</b>：每次加密生成全新的 12 字节随机 IV</li>
 *   <li><b>密钥长度</b>：128/192/256 位，按密钥长度自动路由对应 AES-GCM Provider</li>
 *   <li><b>Hex/Base64 编码</b>：支持两种输出格式</li>
 * </ul>
 *
 * <p><b>实现说明：</b>本类为兼容入口，加解密内部委派
 * {@link com.njydsz.common.util.security.crypto.CryptoProviderRegistry}
 * （密文格式与旧版一致：IV(12B) || ciphertext+tag，Base64 输出，历史密文可平滑解密）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 生成密钥（256 位 Hex）
 * String hexKey = AesUtils.initHexKey();
 *
 * // 加密（GCM 模式）
 * String ciphertext = AesUtils.encrypt("Hello World", hexKey);
 *
 * // 解密（GCM 模式）
 * String plaintext = AesUtils.decrypt(ciphertext, hexKey);
 * }</pre>
 *
 * <p><b>安全说明：</b>
 * <ul>
 *   <li>GCM 模式同时保证机密性和完整性，推荐用于生产环境</li>
 *   <li>密钥请使用安全的方式存储和传输，切勿硬编码在代码中</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 3.0.0 起替换为 {@link com.njydsz.common.util.security.crypto.CryptoUtils}。
 *             新 API 通过 {@link com.njydsz.common.util.security.crypto.CryptoProviderRegistry} 支持算法路由，
 *             可一键切换 AES/SM4。迁移示例：
 *             {@code AesUtils.encrypt(text, hexKey) → CryptoUtils.encryptHex(text, hexKey)}
 *             <p><b>移除时间表：</b>
 *             <ul>
 *               <li>3.0.0（已发布）：标记废弃，内部委派新 API 实现</li>
 *               <li>4.0.0（计划）：移除本类，新代码必须使用 {@code CryptoUtils}</li>
 *             </ul>
 *             历史密文兼容：新 API 密文格式与旧版一致（IV(12B) || ciphertext+tag，Base64），
 *             直接切换 API 无需数据迁移。
 */
@Deprecated(since = "3.0.0", forRemoval = true)
public final class AesUtils {

    /** AES 密钥算法类型 */
    public static final String KEY_ALGORITHM = "AES";

    /** 默认密钥位长度（256 位，AES-256） */
    public static final int DEFAULT_KEY_SIZE = 256;

    /** 共享的线程安全 SecureRandom 实例 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesUtils() {
        throw new UnsupportedOperationException("AesUtils is a utility class");
    }

    // ==================== 加解密 ====================

    /**
     * 按密钥字节长度路由到对应 AES-GCM Provider。
     *
     * @param keyBytes 密钥字节数（16/24/32）
     * @return 对应位数的 AES-GCM Provider
     */
    private static CryptoProvider providerFor(int keyBytes) {
        String algorithm = switch (keyBytes) {
            case 16 -> "AES-128-GCM";
            case 24 -> "AES-192-GCM";
            case 32 -> "AES-256-GCM";
            default -> throw new IllegalArgumentException("AES key length must be 16/24/32 bytes");
        };
        return CryptoProviderRegistry.get(algorithm);
    }

    /**
     * AES-GCM 加密
     *
     * <p>使用 AES-GCM 模式，自动生成 12 字节随机 IV。
     * 密文格式：Base64(IV(12 字节) + 密文 + GCM 认证标签)。
     *
     * @param content   明文内容（不可为 null）
     * @param hexAesKey Hex 格式的 AES 密钥（32/48/64 个 Hex 字符）
     * @return Base64 编码的密文
     * @throws IllegalArgumentException 密钥格式非法
     * @throws IllegalStateException    加密失败
     */
    public static String encrypt(String content, String hexAesKey) {
        Objects.requireNonNull(content, "content must not be null");
        byte[] key = HexUtils.decode(hexAesKey);
        byte[] ciphertext = providerFor(key.length)
                .encrypt(content.getBytes(StandardCharsets.UTF_8), key, null);
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * AES-GCM 解密
     *
     * <p>自动从密文中提取 IV 进行解密。
     * GCM 模式提供认证，若密文被篡改将抛出异常。
     *
     * @param encryptedBase64 Base64 编码的密文
     * @param hexAesKey       Hex 格式的 AES 密钥
     * @return 解密后的明文
     * @throws IllegalArgumentException 密钥格式非法或密文格式非法
     * @throws IllegalStateException    解密失败或密文被篡改
     */
    public static String decrypt(String encryptedBase64, String hexAesKey) {
        Objects.requireNonNull(encryptedBase64, "ciphertext must not be null");
        byte[] key = HexUtils.decode(hexAesKey);
        byte[] plaintext = providerFor(key.length)
                .decrypt(Base64.getDecoder().decode(encryptedBase64), key, null);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    // ==================== 密钥生成 ====================

    /**
     * 生成安全的随机 AES 密钥（Base64 编码）
     *
     * @return Base64 编码的安全随机密钥
     */
    public static String generateSecureKey() {
        return Base64.getEncoder().encodeToString(initKey(DEFAULT_KEY_SIZE));
    }

    /**
     * 生成 Hex 格式默认长度（256 位）的随机密钥
     *
     * @return Hex 格式密钥
     */
    public static String initHexKey() {
        return bytesToHex(initKey(DEFAULT_KEY_SIZE));
    }

    /**
     * 生成指定长度的 Hex 格式随机密钥
     *
     * @param keySize 密钥位数，支持 128/192/256
     * @return Hex 格式密钥
     */
    public static String initHexKey(int keySize) {
        return bytesToHex(initKey(keySize));
    }

    /**
     * 生成默认长度（256 位）的随机密钥
     *
     * @return 密钥字节数组
     */
    public static byte[] initKey() {
        return initKey(DEFAULT_KEY_SIZE);
    }

    /**
     * 生成指定长度的密钥
     *
     * @param keySize 密钥位数，支持 128/192/256
     * @return 密钥字节数组
     * @throws IllegalArgumentException keySize 非法时抛出
     */
    public static byte[] initKey(int keySize) {
        if (keySize != 128 && keySize != 192 && keySize != 256) {
            throw new IllegalArgumentException("error keySize: " + keySize + ", must be 128, 192, or 256");
        }
        try {
            javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGen.init(keySize, SECURE_RANDOM);
            return keyGen.generateKey().getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate AES key", e);
        }
    }

    // ==================== 编码转换 ====================

    /**
     * 字节数组 Base64 编码
     *
     * @param bytes 字节数组
     * @return Base64 字符串，输入 null 返回 null
     */
    public static String base64Encode(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Base64 字符串解码为字节数组
     *
     * @param base64Code Base64 字符串
     * @return 字节数组，输入 null 返回 null
     */
    public static byte[] base64Decode(String base64Code) {
        if (base64Code == null) {
            return null;
        }
        return Base64.getDecoder().decode(base64Code);
    }

    // bytesToHex / hexToBytes 已统一至 {@link HexUtils}，本类保留以下委派方法以向后兼容。

    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 字节数组
     * @return Hex 字符串
     * @deprecated 使用 {@link HexUtils#encode(byte[])} 替代
     */
    @Deprecated
    public static String bytesToHex(byte[] bytes) {
        return HexUtils.encode(bytes);
    }

    /**
     * 十六进制字符串转字节数组
     *
     * @param hex Hex 字符串
     * @return 字节数组
     * @throws IllegalArgumentException 当 hex 为 null 或长度为奇数
     * @deprecated 使用 {@link HexUtils#decode(String)} 替代
     */
    @Deprecated
    public static byte[] hexToBytes(String hex) {
        return HexUtils.decode(hex);
    }
}
