package com.njydsz.common.util.security;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.KeyGenerator;

import java.util.HexFormat;
import com.njydsz.common.util.string.StringUtils;

/**
 * AES 加密解密工具类
 *
 * <p>提供全面的 AES 对称加密解密功能，纯 JDK 实现，零第三方依赖。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li><b>GCM 模式（默认推荐）</b>：encrypt/decrypt 默认使用 AES-GCM，提供认证加密（AEAD）</li>
 *   <li><b>自动 IV 生成</b>：使用 SecureRandom 生成 12 字节随机 IV</li>
 *   <li><b>256 位密钥</b>：默认生成 256 位强密钥</li>
 *   <li><b>Hex/Base64 编码</b>：支持两种输出格式</li>
 * </ul>
 *
 * <p><b>安全说明：</b>
 * <ul>
 *   <li>GCM 模式同时保证机密性和完整性，推荐用于生产环境</li>
 *   <li>密钥请使用安全的方式存储和传输，切勿硬编码在代码中</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 生成密钥（256 位）
 * String hexKey = AesUtils.initHexKey();
 *
 * // 加密（GCM 模式，自动生成随机 IV）
 * String ciphertext = AesUtils.encrypt("Hello World", hexKey);
 *
 * // 解密（GCM 模式）
 * String plaintext = AesUtils.decrypt(ciphertext, hexKey);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class AesUtils {

    /**
     * 私有构造函数，防止外部实例化
     */
    private AesUtils() {
        throw new UnsupportedOperationException("AesUtils 是工具类，不允许被实例化");
    }

    /**
     * AES 密钥算法类型
     */
    public static final String KEY_ALGORITHM = "AES";

    /**
     * 默认密钥位长度（256 位，AES-256）
     */
    public static final int DEFAULT_KEY_SIZE = 256;

    /**
     * 共享的线程安全 SecureRandom 实例（SecureRandom 本身是线程安全的）
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 可配置的 AES 密钥（Hex 格式），未配置时为 null。
     *
     * <p>fail-fast 策略：未显式配置时 {@link #getConfiguredKey()} 将抛出
     * {@link IllegalStateException}，避免静默生成临时密钥导致重启后已加密数据不可解密。
     */
    private static volatile String configuredKey;

    /**
     * AesGcmCrypto 实例缓存，按 Hex 密钥字符串索引。
     *
     * <p>AesGcmCrypto 内部持有的 SecretKeySpec 与共享 SecureRandom 均可复用，
     * 仅 Cipher 非线程安全需在每次 encrypt/decrypt 内部新建。
     * 缓存避免每次加解密都重新执行 hex 解码 + SecretKeySpec 构造。
     */
    private static final ConcurrentHashMap<String, AesGcmCrypto> CRYPTO_CACHE = new ConcurrentHashMap<>();

    /**
     * 注入配置的 AES 密钥（Hex 格式）
     *
     * <p>密钥变更时会清空 {@link #CRYPTO_CACHE}，确保旧密钥对应的缓存实例不会残留。
     *
     * @param hexKey Hex 格式的 AES 密钥，最小 32 字节（64 个 Hex 字符）推荐，兼容 16 字节
     */
    public static void setConfiguredKey(String hexKey) {
        if (StringUtils.isBlank(hexKey)) {
            throw new IllegalArgumentException("AES 密钥不能为空");
        }
        validateKey(hexKey);
        configuredKey = hexKey;
        CRYPTO_CACHE.clear();
    }

    /**
     * 获取配置的密钥。
     *
     * <p>fail-fast：若未通过 {@link #setConfiguredKey(String)} 配置密钥，直接抛出
     * {@link IllegalStateException}，避免静默生成临时随机密钥——临时密钥在应用重启后会丢失，
     * 导致所有已加密数据不可解密。
     *
     * @return 已配置的 Hex 格式 AES 密钥
     * @throws IllegalStateException 未配置密钥时抛出
     */
    public static String getConfiguredKey() {
        if (configuredKey == null) {
            throw new IllegalStateException("AES 密钥未配置，请通过 ydsz.util.aes.key 配置或调用 setConfiguredKey");
        }
        return configuredKey;
    }

    /**
     * 校验密钥强度，必须为 AES 标准长度（16/24/32 字节 = 32/48/64 个 Hex 字符）。
     *
     * <p>统一委托 {@link AesGcmCrypto#validateKey(byte[])} 进行字节级校验，
     * 避免本类与 AesGcmCrypto 维护两套长度校验口径。
     *
     * @param hexKey Hex 格式密钥
     * @throws IllegalArgumentException hex 为 null/空白或长度非法时抛出
     */
    private static void validateKey(String hexKey) {
        if (StringUtils.isBlank(hexKey)) {
            throw new IllegalArgumentException("AES 密钥不能为空");
        }
        AesGcmCrypto.validateKey(hexToBytes(hexKey));
    }

    /**
     * 获取（必要时创建并缓存）指定 Hex 密钥对应的 AesGcmCrypto 实例。
     *
     * <p>同一 Hex 密钥复用同一 AesGcmCrypto 实例，避免重复构造 SecretKeySpec。
     *
     * @param hexAesKey Hex 格式 AES 密钥
     * @return 缓存的 AesGcmCrypto 实例
     */
    private static AesGcmCrypto getCrypto(String hexAesKey) {
        return CRYPTO_CACHE.computeIfAbsent(hexAesKey, k -> new AesGcmCrypto(hexToBytes(k)));
    }

    /**
     * 生成安全的随机 AES 密钥（256 位，推荐用于生产环境）
     *
     * @return Base64 编码的安全随机密钥
     */
    public static String generateSecureKey() {
        return Base64.getEncoder().encodeToString(initKey(DEFAULT_KEY_SIZE));
    }

    /**
     * 字节数组 Base64 编码
     */
    public static String base64Encode(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Base64 字符串解码为字节数组
     */
    public static byte[] base64Decode(String base64Code) {
        if (base64Code == null) {
            return null;
        }
        return Base64.getDecoder().decode(base64Code);
    }

    /**
     * 字节数组转十六进制字符串
     */
    public static String bytesToHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 十六进制字符串转字节数组
     *
     * @throws IllegalArgumentException 当 hex 为 null 或长度为奇数时
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must not be null and must have even length");
        }
        return HexFormat.of().parseHex(hex);
    }

    // ==================== GCM 模式（默认推荐） ====================

    /**
     * AES 加密（默认 GCM 模式）
     *
     * <p>使用 AES-256-GCM 模式，自动生成 12 字节随机 IV。
     * 密文格式：Base64(IV(12字节) + 密文 + GCM 认证标签)。</p>
     *
     * <p>实现委托给 {@link AesGcmCrypto}，消除重复的 GCM 加密逻辑。</p>
     *
     * @param content   明文内容
     * @param hexAesKey Hex 格式的 AES 密钥
     * @return Base64 编码的密文
     * @throws GeneralSecurityException 加密异常
     */
    public static String encrypt(String content, String hexAesKey) throws GeneralSecurityException {
        validateKey(hexAesKey);
        AesGcmCrypto crypto = getCrypto(hexAesKey);
        return crypto.encrypt(content);
    }

    /**
     * AES 解密（默认 GCM 模式）
     *
     * <p>自动从密文中提取 IV 进行解密。
     * GCM 模式提供认证，若密文被篡改将抛出异常。</p>
     *
     * <p>实现委托给 {@link AesGcmCrypto}，消除重复的 GCM 解密逻辑。</p>
     *
     * @param encryptedBase64 Base64 编码的密文
     * @param hexAesKey       Hex 格式的 AES 密钥
     * @return 解密后的明文
     * @throws GeneralSecurityException 解密异常（含认证失败）
     */
    public static String decrypt(String encryptedBase64, String hexAesKey) throws GeneralSecurityException {
        validateKey(hexAesKey);
        AesGcmCrypto crypto = getCrypto(hexAesKey);
        return crypto.decrypt(encryptedBase64);
    }

    // ==================== 密钥生成 ====================

    /**
     * 生成 Hex 格式默认长度（256 位）的随机密钥
     */
    public static String initHexKey() {
        return bytesToHex(initKey(DEFAULT_KEY_SIZE));
    }

    /**
     * 生成默认长度（256 位）的随机密钥
     */
    public static byte[] initKey() {
        return initKey(DEFAULT_KEY_SIZE);
    }

    /**
     * 生成指定长度的密钥
     *
     * @param keySize 密钥位数，支持 128/192/256
     */
    public static byte[] initKey(int keySize) {
        if (keySize != 128 && keySize != 192 && keySize != 256) {
            throw new IllegalArgumentException("error keySize: " + keySize + ", must be 128, 192, or 256");
        }
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGen.init(keySize, SECURE_RANDOM);
            return keyGen.generateKey().getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate AES key", e);
        }
    }

    /**
     * 生成指定长度的 Hex 格式随机密钥
     */
    public static String initHexKey(int keySize) {
        return bytesToHex(initKey(keySize));
    }
}
