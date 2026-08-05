package com.remisoft.common.util.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;

/**
 * ChaCha20-Poly1305 认证加密工具类（AEAD）。
 *
 * <p>基于 ISO/IEC 18033-4 和 RFC 8439 定义的 ChaCha20-Poly1305 组合算法，
 * 提供带关联数据（AEAD）认证的加密能力。
 *
 * <h2>核心能力</h2>
 * <ul>
 *   <li>密钥长度：256 位（32 字节，JDK 要求固定 256 位）</li>
 *   <li>Nonce 长度：96 位（12 字节，每次加密必须唯一）</li>
 *   <li>算法模式：ChaCha20-Poly1305（JDK 12+ 内置支持）</li>
 *   <li>可选：关联数据（AAD）认证</li>
 * </ul>
 *
 * <h2>适用场景</h2>
 * <ul>
 *   <li>TLS 1.3 软件友好模式（无 AES-NI 硬件加速时优于 AES-GCM）</li>
 *   <li>移动端/嵌入式设备（纯软件实现性能优异）</li>
 *   <li>VPN / WireGuard 协议族（现代加密协议首选）</li>
 *   <li>替代 AES-GCM 的场景（如在旧硬件无 AES 指令集时）</li>
 * </ul>
 *
 * <h2>安全说明</h2>
 * <ul>
 *   <li>密钥必须为 32 字节（256 位），使用 {@link #generateKeyHex()} 生成</li>
 *   <li>Nonce 必须每次加密唯一（{@code encrypt} 方法自动生成随机 12 字节 Nonce）</li>
 *   <li>Nonce 泄露不会破坏机密性，但会破坏完整性认证</li>
 *   <li>JDK 要求 JDK 12+（推荐 JDK 17+ 以获得完整能力）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.4.0
 */
@Slf4j
public class ChaCha20Utils {

    /**
     * 算法名称（JDK 标准名称）。
     */
    private static final String ALGORITHM = "ChaCha20-Poly1305";

    /**
     * 密钥长度（32 字节 = 256 位）。
     */
    private static final int KEY_LENGTH = 32;

    /**
     * Nonce 长度（12 字节 = 96 位）。
     */
    private static final int NONCE_LENGTH = 12;

    /**
     * Poly1305 认证标签长度（16 字节 = 128 位）。
     */
    private static final int TAG_LENGTH = 16;

    /**
     * 共享的 SecureRandom 实例（线程安全）。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Cipher 实例缓存（ThreadLocal 池化，委托 {@link JcaCipherPool} 统一管理）。
     *
     * <p>统一池化后消除与 AesGcmCrypto、Sm2Utils 等类的重复 ThreadLocal 代码。
     *
     * @since 2.0.0
     */
    private static Cipher acquireCipher() {
        return JcaCipherPool.acquireChaCha20Cipher();
    }

    /**
     * 私有构造器，工具类不允许实例化。
     */
    private ChaCha20Utils() {
        throw new UnsupportedOperationException("ChaCha20Utils is a utility class and cannot be instantiated");
    }

    /**
     * 生成随机 256 位密钥（Hex 格式）。
     *
     * @return Hex 编码的密钥字符串（64 字符）
     */
    public static String generateKeyHex() {
        byte[] key = new byte[KEY_LENGTH];
        SECURE_RANDOM.nextBytes(key);
        return HexFormat.of().formatHex(key);
    }

    /**
     * 生成随机 Nonce（Hex 格式，12 字节）。
     *
     * @return Hex 编码的 Nonce 字符串（24 字符）
     */
    public static String generateNonceHex() {
        byte[] nonce = new byte[NONCE_LENGTH];
        SECURE_RANDOM.nextBytes(nonce);
        return HexFormat.of().formatHex(nonce);
    }

    /**
     * 使用 Hex 格式密钥初始化 SecretKeySpec。
     *
     * @param keyHex Hex 编码的密钥（64 字符 = 32 字节）
     * @return SecretKeySpec 实例
     * @throws IllegalArgumentException 密钥长度不合法时抛出
     */
    public static SecretKeySpec keyFromHex(String keyHex) {
        byte[] keyBytes = validateAndDecodeKey(HexFormat.of().parseHex(keyHex));
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 使用 Base64 格式密钥初始化 SecretKeySpec。
     *
     * @param keyBase64 Base64 编码的密钥
     * @return SecretKeySpec 实例
     * @throws IllegalArgumentException 密钥长度不合法时抛出
     */
    public static SecretKeySpec keyFromBase64(String keyBase64) {
        byte[] keyBytes = validateAndDecodeKey(java.util.Base64.getDecoder().decode(keyBase64));
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 使用 Hex 格式 Nonce 初始化 IvParameterSpec。
     *
     * @param nonceHex Hex 编码的 Nonce（24 字符 = 12 字节）
     * @return IvParameterSpec 实例
     * @throws IllegalArgumentException Nonce 长度不合法时抛出
     */
    public static IvParameterSpec nonceFromHex(String nonceHex) {
        byte[] nonce = validateNonce(HexFormat.of().parseHex(nonceHex));
        return new IvParameterSpec(nonce);
    }

    /**
     * 使用 Base64 格式 Nonce 初始化 IvParameterSpec。
     *
     * @param nonceBase64 Base64 编码的 Nonce
     * @return IvParameterSpec 实例
     * @throws IllegalArgumentException Nonce 长度不合法时抛出
     */
    public static IvParameterSpec nonceFromBase64(String nonceBase64) {
        byte[] nonce = validateNonce(java.util.Base64.getDecoder().decode(nonceBase64));
        return new IvParameterSpec(nonce);
    }

    /**
     * 加密字符串（自动生成随机 Nonce，Hex 输出）。
     *
     * <p>输出格式：{@code nonceHex + ciphertextHex}，其中 Nonce 长度为 24 字符（12 字节）。
     * 解密时使用 {@link #decryptHex(String, String)} 即可自动提取 Nonce。
     *
     * @param plaintext 明文字符串（UTF-8 编码）
     * @param keyHex    Hex 编码的密钥（64 字符）
     * @return Nonce + 密文的 Hex 拼接字符串
     */
    public static String encryptHex(String plaintext, String keyHex) {
        return encryptHex(plaintext, keyHex, null);
    }

    /**
     * 加密字符串（支持 AAD，自动生成随机 Nonce，Hex 输出）。
     *
     * @param plaintext 明文字符串（UTF-8 编码）
     * @param keyHex    Hex 编码的密钥（64 字符）
     * @param aad       关联数据（可为 null），仅用于完整性认证，不加密
     * @return Nonce + 密文的 Hex 拼接字符串
     */
    public static String encryptHex(String plaintext, String keyHex, byte[] aad) {
        String nonceHex = generateNonceHex();
        IvParameterSpec nonce = nonceFromHex(nonceHex);
        byte[] ciphertext = encrypt(
            plaintext.getBytes(StandardCharsets.UTF_8),
            keyFromHex(keyHex),
            nonce,
            aad
        );
        return nonceHex + HexFormat.of().formatHex(ciphertext);
    }

    /**
     * 加密字节数组（使用指定 Nonce、Hex 密钥）。
     *
     * @param plaintext 明文字节数组
     * @param keyHex    Hex 编码的密钥
     * @param nonceHex  Hex 编码的 Nonce
     * @return 密文字节数组
     */
    public static byte[] encryptHex(byte[] plaintext, String keyHex, String nonceHex) {
        return encrypt(plaintext, keyFromHex(keyHex), nonceFromHex(nonceHex), null);
    }

    /**
     * 加密字节数组（核心方法）。
     *
     * <p>调用者需确保同一密钥下 Nonce 唯一。
     *
     * @param plaintext 明文
     * @param key       密钥（256 位）
     * @param nonce     Nonce（96 位，必须唯一）
     * @param aad       关联数据（可为 null）
     * @return 密文（含 16 字节认证标签）
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws IllegalStateException    加密失败时抛出
     */
    public static byte[] encrypt(byte[] plaintext, Key key, IvParameterSpec nonce, byte[] aad) {
        try {
            Cipher cipher = acquireCipher();
            cipher.init(Cipher.ENCRYPT_MODE, key, nonce);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(plaintext);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid ChaCha20 key: must be 32 bytes", e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new IllegalArgumentException("Invalid ChaCha20 nonce: must be 12 bytes", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException("ChaCha20 encryption failed", e);
        }
    }

    /**
     * 解密字符串（从 Hex 输入中提取 Nonce）。
     *
     * <p>输入格式：{@code nonceHex + ciphertextHex}，其中 Nonce 长度为 24 字符。
     *
     * @param ciphertextWithNonceHex Nonce + 密文的 Hex 拼接字符串
     * @param keyHex                Hex 编码的密钥
     * @return 解密后的明文字符串
     * @throws AEADBadTagException 认证失败（数据被篡改）时抛出
     */
    public static String decryptHex(String ciphertextWithNonceHex, String keyHex) {
        return decryptHex(ciphertextWithNonceHex, keyHex, null);
    }

    /**
     * 解密字符串（支持 AAD，从 Hex 输入中提取 Nonce）。
     *
     * @param ciphertextWithNonceHex Nonce + 密文的 Hex 拼接字符串
     * @param keyHex                Hex 编码的密钥
     * @param aad                  关联数据（与加密时一致）
     * @return 解密后的明文字符串
     * @throws AEADBadTagException 认证失败时抛出
     */
    public static String decryptHex(String ciphertextWithNonceHex, String keyHex, byte[] aad) {
        if (ciphertextWithNonceHex == null || ciphertextWithNonceHex.length() < NONCE_LENGTH * 2) {
            throw new IllegalArgumentException("Invalid input: too short to contain nonce");
        }
        String nonceHex = ciphertextWithNonceHex.substring(0, NONCE_LENGTH * 2);
        String ciphertextHex = ciphertextWithNonceHex.substring(NONCE_LENGTH * 2);
        byte[] ciphertext = HexFormat.of().parseHex(ciphertextHex);
        byte[] plaintext = decrypt(ciphertext, keyFromHex(keyHex), nonceFromHex(nonceHex), aad);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * 解密字节数组（使用指定 Nonce、Hex 密钥）。
     *
     * @param ciphertext 密文字节数组
     * @param keyHex     Hex 编码的密钥
     * @param nonceHex   Hex 编码的 Nonce
     * @return 明文字节数组
     */
    public static byte[] decryptHex(byte[] ciphertext, String keyHex, String nonceHex) {
        return decrypt(ciphertext, keyFromHex(keyHex), nonceFromHex(nonceHex), null);
    }

    /**
     * 解密字节数组（核心方法）。
     *
     * @param ciphertext 密文（含认证标签）
     * @param key        密钥（256 位）
     * @param nonce      Nonce（96 位）
     * @param aad        关联数据（可为 null）
     * @return 明文
     * @throws AEADBadTagException 认证失败时抛出
     */
    public static byte[] decrypt(byte[] ciphertext, Key key, IvParameterSpec nonce, byte[] aad) {
        try {
            Cipher cipher = acquireCipher();
            cipher.init(Cipher.DECRYPT_MODE, key, nonce);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(ciphertext);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid ChaCha20 key: must be 32 bytes", e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new IllegalArgumentException("Invalid ChaCha20 nonce: must be 12 bytes", e);
        } catch (AEADBadTagException e) {
            // 认证失败（数据被篡改），抛出原始异常
            throw e;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException("ChaCha20 decryption failed", e);
        }
    }

    /**
     * 加密字节数组（使用指定 Nonce、字节数组密钥）。
     *
     * <p>最底层方法，不进行任何编码转换，供高级调用方使用。
     *
     * @param plaintext      明文
     * @param key            密钥字节数组（32 字节）
     * @param nonce           Nonce 字节数组（12 字节）
     * @param aad            关联数据（可为 null）
     * @param prependNonce   是否在输出前拼接 Nonce
     * @return 密文字节数组（可选含前缀 Nonce）
     */
    public static byte[] encrypt(byte[] plaintext, byte[] key, byte[] nonce, byte[] aad, boolean prependNonce) {
        SecretKeySpec keySpec = new SecretKeySpec(validateAndDecodeKey(key), ALGORITHM);
        IvParameterSpec nonceSpec = new IvParameterSpec(validateNonce(nonce));
        byte[] ciphertext = encrypt(plaintext, keySpec, nonceSpec, aad);
        if (prependNonce) {
            ByteBuffer buffer = ByteBuffer.allocate(nonce.length + ciphertext.length);
            buffer.put(nonce);
            buffer.put(ciphertext);
            return buffer.array();
        }
        return ciphertext;
    }

    /**
     * 解密字节数组（使用指定 Nonce、字节数组密钥）。
     *
     * @param ciphertext    密文（如果 prependNonce=true，前 12 字节为 Nonce）
     * @param key           密钥字节数组（32 字节）
     * @param nonce         Nonce 字节数组（12 字节，prependNonce=true 时传 null）
     * @param aad           关联数据（可为 null）
     * @param prependNonce  是否在输入前包含 Nonce
     * @return 明文字节数组
     */
    public static byte[] decrypt(byte[] ciphertext, byte[] key, byte[] nonce, byte[] aad, boolean prependNonce) {
        if (prependNonce && ciphertext.length >= NONCE_LENGTH) {
            ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
            byte[] extractedNonce = new byte[NONCE_LENGTH];
            buffer.get(extractedNonce);
            byte[] actualCiphertext = new byte[buffer.remaining()];
            buffer.get(actualCiphertext);
            return decrypt(actualCiphertext,
                new SecretKeySpec(validateAndDecodeKey(key), ALGORITHM),
                new IvParameterSpec(extractedNonce),
                aad);
        }
        SecretKeySpec keySpec = new SecretKeySpec(validateAndDecodeKey(key), ALGORITHM);
        IvParameterSpec nonceSpec = new IvParameterSpec(validateNonce(nonce));
        return decrypt(ciphertext, keySpec, nonceSpec, aad);
    }

    /**
     * 校验并返回密钥字节数组。
     */
    private static byte[] validateAndDecodeKey(byte[] key) {
        if (key == null || key.length != KEY_LENGTH) {
            throw new IllegalArgumentException(
                "ChaCha20 key must be exactly " + KEY_LENGTH + " bytes (256 bits), got "
                    + (key == null ? "null" : key.length + " bytes")
            );
        }
        return key;
    }

    /**
     * 校验 Nonce 长度。
     */
    private static byte[] validateNonce(byte[] nonce) {
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException(
                "ChaCha20 nonce must be exactly " + NONCE_LENGTH + " bytes (96 bits), got "
                    + (nonce == null ? "null" : nonce.length + " bytes")
            );
        }
        return nonce;
    }

    /**
     * 返回密钥长度（32 字节 = 256 位）。
     */
    public static int getKeyLength() {
        return KEY_LENGTH;
    }

    /**
     * 返回 Nonce 长度（12 字节 = 96 位）。
     */
    public static int getNonceLength() {
        return NONCE_LENGTH;
    }

    /**
     * 返回认证标签长度（16 字节 = 128 位）。
     */
    public static int getTagLength() {
        return TAG_LENGTH;
    }
}
