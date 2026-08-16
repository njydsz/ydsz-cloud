package com.njydsz.common.util.security.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>优先级由低到高：</p>
 * <ol>
 *   <li>注册表默认值：{@code AES-256-GCM}</li>
 *   <li>Spring 配置：{@code ydsz.util.crypto.default-algorithm}（通过 {@link CryptoAutoConfiguration} 注入）</li>
 *   <li>系统属性：{@code crypto.algorithm}（最高优先级，用于覆盖）</li>
 * </ol>
 *
 * <h2>扩展能力</h2>
 * <p>支持 AAD（Additional Authenticated Data）的 AEAD 加密，
 * 用于带上下文的加密场景（如用户 ID 绑定密文防串用）。
 *
 * @author ydsz-team
 * @since 3.0.0
 * @see CryptoProvider
 * @see CryptoProviderRegistry
 * @see CryptoAutoConfiguration
 */
public final class CryptoUtils {

    private static final Logger log = LoggerFactory.getLogger(CryptoUtils.class);

    private static final HexFormat HEX = HexFormat.of();

    /**
     * 系统属性 key：通过 {@code -Dcrypto.algorithm=SM4-GCM} 指定默认算法。
     */
    public static final String ALGORITHM_SYSTEM_PROPERTY = "crypto.algorithm";

    /**
     * Spring 配置前缀：{@code ydsz.util.crypto}。
     */
    public static final String CONFIG_PREFIX = "ydsz.util.crypto";

    private static volatile CryptoProvider defaultProvider;

    /** 由 CryptoAutoConfiguration 注入的算法标识（Spring 配置桥接） */
    private static volatile String injectedAlgorithm;

    private CryptoUtils() {
        throw new UnsupportedOperationException("CryptoUtils is a utility class");
    }

    /**
     * 设置默认算法标识（由 {@link CryptoAutoConfiguration} 在容器初始化时调用）。
     *
     * <p>注入后会清空已有缓存，下次 {@link #provider()} 重新解析。仅允许注入一次，
     * 重复调用将被忽略并打印 warn 日志。</p>
     *
     * @param algorithm 算法标识（如 {@code "SM4-GCM"}）
     */
    public static void setDefaultAlgorithm(String algorithm) {
        if (injectedAlgorithm != null) {
            log.warn("CryptoUtils.setDefaultAlgorithm 已被调用过，忽略重复注入，保持原值: {}", injectedAlgorithm);
            return;
        }
        injectedAlgorithm = algorithm;
        defaultProvider = null;
        log.info("CryptoUtils 默认算法已设置为: {}", algorithm);
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
     * <p>解析优先级：系统属性 {@code crypto.algorithm} 优先，未设置时使用 {@link CryptoAutoConfiguration} 注入值。</p>
     *
     * @return 默认 CryptoProvider
     */
    public static CryptoProvider provider() {
        if (defaultProvider != null) {
            return defaultProvider;
        }
        String algo = System.getProperty(ALGORITHM_SYSTEM_PROPERTY);
        if (algo == null) {
            algo = injectedAlgorithm;
        }
        if (algo == null) {
            algo = "AES-256-GCM";
        }
        defaultProvider = CryptoProviderRegistry.get(algo);
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

    /**
     * 安全擦除密钥字节数组，防止内存中的密钥被 swap 或 dump 泄露。
     *
     * <p>使用完密钥后应主动调用此方法清零。示例：
     * <pre>{@code
     *   byte[] key = Base64.getDecoder().decode(keyB64);
     *   try {
     *       String cipher = CryptoUtils.encrypt("data", key);
     *   } finally {
     *       CryptoUtils.destroyKey(key);
     *   }
     * }</pre>
     *
     * @param key 待擦除的密钥字节数组；为 null 时无操作
     * @since 4.1.0
     */
    public static void destroyKey(byte[] key) {
        if (key != null) {
            Arrays.fill(key, (byte) 0);
        }
    }
}
