package com.njydsz.common.util.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.njydsz.common.util.bytes.HexUtils;
import com.njydsz.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

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
 *   <li><b>兼容解密</b>：提供 decryptECBCompat/decryptCBCCompat 兼容旧密文</li>
 *   <li><b>Hex/Base64 编码</b>：支持两种输出格式</li>
 * </ul>
 *
 * <p><b>安全说明：</b>
 * <ul>
 *   <li>GCM 模式同时保证机密性和完整性，推荐用于生产环境</li>
 *   <li>ECB/CBC 模式已标记废弃，仅提供兼容解密方法</li>
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
 *
 * // 兼容旧 ECB 密文解密
 * String oldPlaintext = AesUtils.decryptECBCompat(oldCiphertext, hexKey);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Slf4j
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
     * GCM 模式完整转换模式
     */
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * GCM IV 长度（推荐 12 字节）
     */
    private static final int GCM_IV_LENGTH = 12;

    /**
     * GCM 认证标签长度（128 bit）
     */
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * CBC 模式转换（已废弃，仅兼容）
     */
    private static final String CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding";

    /**
     * ECB 模式转换（已废弃，仅兼容）
     */
    private static final String ECB_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * 可配置的 AES 密钥（Hex 格式），为空时使用 initKey() 生成临时密钥
     */
    private static volatile String configuredKey;

    /**
     * 注入配置的 AES 密钥（Hex 格式）
     *
     * @param hexKey Hex 格式的 AES 密钥，最小 32 字节（64 个 Hex 字符）推荐，兼容 16 字节
     */
    public static void setConfiguredKey(String hexKey) {
        if (StringUtils.isBlank(hexKey)) {
            throw new IllegalArgumentException("AES 密钥不能为空");
        }
        validateKey(hexKey);
        configuredKey = hexKey;
    }

    /**
     * 获取配置的密钥，若未配置则自动生成 256 位安全随机密钥
     */
    public static String getConfiguredKey() {
        if (configuredKey == null) {
            synchronized (AesUtils.class) {
                if (configuredKey == null) {
                    configuredKey = initHexKey(DEFAULT_KEY_SIZE);
                }
            }
        }
        return configuredKey;
    }

    /**
     * 校验密钥强度，最小支持 AES-128（16 字节 = 32 Hex 字符）
     */
    private static void validateKey(String hexKey) {
        if (hexKey == null || hexKey.length() < 32) {
            throw new IllegalArgumentException("AES 密钥长度不足，最小需要 16 字节（32 个 Hex 字符）");
        }
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
     * 字节数组转十六进制字符串（委托给 HexUtils 实现）
     */
    public static String bytesToHex(byte[] bytes) {
        return HexUtils.bytesToHex(bytes);
    }

    /**
     * 十六进制字符串转字节数组（委托给 HexUtils 实现）
     */
    public static byte[] hexToBytes(String hex) {
        return HexUtils.hexToBytes(hex);
    }

    // ==================== GCM 模式（默认推荐） ====================

    /**
     * AES 加密（默认 GCM 模式）
     *
     * <p>使用 AES-256-GCM 模式，自动生成 12 字节随机 IV。
     * 密文格式：Base64(IV(12字节) + 密文 + GCM 认证标签)。</p>
     *
     * @param content   明文内容
     * @param hexAesKey Hex 格式的 AES 密钥
     * @return Base64 编码的密文
     * @throws GeneralSecurityException 加密异常
     */
    public static String encrypt(String content, String hexAesKey) throws GeneralSecurityException {
        validateKey(hexAesKey);
        byte[] keyBytes = hexToBytes(hexAesKey);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, KEY_ALGORITHM);

        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

        byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));

        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
        buffer.put(iv);
        buffer.put(encrypted);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /**
     * AES 解密（默认 GCM 模式）
     *
     * <p>自动从密文中提取 IV 进行解密。
     * GCM 模式提供认证，若密文被篡改将抛出异常。</p>
     *
     * @param encryptedBase64 Base64 编码的密文
     * @param hexAesKey       Hex 格式的 AES 密钥
     * @return 解密后的明文
     * @throws GeneralSecurityException 解密异常（含认证失败）
     */
    public static String decrypt(String encryptedBase64, String hexAesKey) throws GeneralSecurityException {
        validateKey(hexAesKey);
        byte[] decoded = Base64.getDecoder().decode(encryptedBase64);

        if (decoded.length < GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted data, too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);

        byte[] keyBytes = hexToBytes(hexAesKey);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, KEY_ALGORITHM);

        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        byte[] decrypted = cipher.doFinal(cipherText);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * AES 加密（GCM 模式，显式方法别名）
     */
    public static String encryptGcm(String content, String hexAesKey) throws GeneralSecurityException {
        return encrypt(content, hexAesKey);
    }

    /**
     * AES 解密（GCM 模式，显式方法别名）
     */
    public static String decryptGcm(String encryptedBase64, String hexAesKey) throws GeneralSecurityException {
        return decrypt(encryptedBase64, hexAesKey);
    }

    // ==================== 兼容旧密文解密 ====================

    /**
     * 兼容解密旧 ECB 模式密文
     *
     * <p>用于迁移期解密历史数据，新数据请使用 GCM 模式。</p>
     *
     * @param ecbBase64 Base64 编码的 ECB 密文
     * @param hexAesKey Hex 格式的 AES 密钥
     * @return 解密后的明文
     * @throws GeneralSecurityException 解密异常
     */
    public static String decryptECBCompat(String ecbBase64, String hexAesKey) throws GeneralSecurityException {
        log.warn("Decrypting legacy ECB ciphertext, please migrate to GCM");
        byte[] keyBytes = hexToBytes(hexAesKey);
        byte[] encrypted = Base64.getDecoder().decode(ecbBase64);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, KEY_ALGORITHM);

        Cipher cipher = Cipher.getInstance(ECB_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 兼容解密旧 CBC 模式密文（IV:CIPHERTEXT 格式）
     *
     * <p>用于迁移期解密历史数据，新数据请使用 GCM 模式。</p>
     *
     * @param cbcResult IV:CIPHERTEXT 格式的 CBC 密文
     * @param hexAesKey Hex 格式的 AES 密钥
     * @return 解密后的明文
     * @throws GeneralSecurityException 解密异常
     */
    public static String decryptCBCCompat(String cbcResult, String hexAesKey) throws GeneralSecurityException {
        log.warn("Decrypting legacy CBC ciphertext, please migrate to GCM");
        int colonIndex = cbcResult.indexOf(':');
        if (colonIndex < 0) {
            throw new IllegalArgumentException("Invalid CBC encrypted format, expected IV:CIPHERTEXT");
        }

        String ivBase64 = cbcResult.substring(0, colonIndex);
        String encryptedBase64 = cbcResult.substring(colonIndex + 1);

        byte[] keyBytes = hexToBytes(hexAesKey);
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(CBC_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
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
        try {
            if (keySize != 128 && keySize != 192 && keySize != 256) {
                throw new IllegalArgumentException("error keySize: " + keySize + ", must be 128, 192, or 256");
            }
            KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGen.init(keySize, SecureRandom.getInstanceStrong());
            SecretKey secretKey = keyGen.generateKey();
            return secretKey.getEncoded();
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
