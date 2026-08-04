package com.njydsz.common.util.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;

import lombok.extern.slf4j.Slf4j;
/**
 * RSA2 非对称加密/签名工具类。
 *
 * <p>基于 RSA + SHA-256 提供非对称加密、解密、数字签名与验签能力，纯 JDK 实现。
 *
 * <h2>核心能力</h2>
 * <ul>
 *   <li>密钥对生成：生成 2048/4096 位 RSA 密钥对</li>
 *   <li>公钥加密/私钥解密：使用 OAEP 填充方案，安全等级高于 PKCS1Padding</li>
 *   <li>私钥签名/公钥验签：SHA256withRSA 签名算法（RSA2）</li>
 *   <li>分段加解密：支持超长文本加解密，自动分块处理</li>
 * </ul>
 *
 * <h2>安全说明</h2>
 * <ul>
 *   <li>填充模式使用 OAEPWithSHA-256AndMGF1Padding，防止填充攻击</li>
 *   <li>建议密钥长度 ≥ 2048 位</li>
 *   <li>私钥应安全存储，切勿硬编码或明文传输</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class Rsa2Utils {

    /**
     * 私有构造器，工具类不允许实例化。
     */
    private Rsa2Utils() {
        throw new UnsupportedOperationException("Rsa2Utils is a utility class and cannot be instantiated");
    }

    /**
     * RSA 签名算法：SHA256withRSA (RSA2)
     */
    public static final String SIGN_ALGORITHM = "SHA256withRSA";

    /**
     * RSA 加解密算法 transformation：OAEPWithSHA-256AndMGF1Padding
     */
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * 最小允许密钥长度（NIST SP 800-57：1024 位 RSA 已废弃，2030 年前至少使用 2048 位）。
     */
    public static final int MIN_KEY_SIZE = 2048;

    /**
     * 建议密钥长度
     */
    public static final int KEY_SIZE_2048 = 2048;

    /**
     * 默认密钥长度
     */
    public static final int DEFAULT_KEY_SIZE = 2048;

    /**
     * OAEP 填充开销字节数：2 * SHA-256 哈希长度(32) + 2 = 66
     * <p>最大加密块 = keySizeBytes - 66
     */
    private static final int OAEP_OVERHEAD_BYTES = 66;

    /**
     * 默认最大加密块（2048 位密钥），用于无密钥对象时的回退
     */
    private static final int DEFAULT_MAX_ENCRYPT_BLOCK = 190;

    /**
     * 默认最大解密块（2048 位密钥），用于无密钥对象时的回退
     */
    private static final int DEFAULT_MAX_DECRYPT_BLOCK = 256;

    /**
     * 共享的线程安全 SecureRandom 实例（KeyPairGenerator 显式传入，避免使用 JDK 默认 StrongRandom）
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成 RSA 密钥对
     *
     * @param keySize 密钥长度，最小 2048（NIST SP 800-57 强制要求）
     * @return 包含 publicKey 和 privateKey 的 Map (Base64 编码)
     */
    public static Map<String, String> initRSAKey(int keySize) {
        try {
            if (keySize < MIN_KEY_SIZE) {
                throw new IllegalArgumentException(
                        "Key size must be at least " + MIN_KEY_SIZE + " bits (NIST SP 800-57)");
            }

            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
            keyPairGen.initialize(keySize, SECURE_RANDOM);

            KeyPair keyPair = keyPairGen.generateKeyPair();

            Map<String, String> keyPairMap = new HashMap<>(2);
            keyPairMap.put("publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            keyPairMap.put("privateKey", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
            return keyPairMap;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    /**
     * 生成默认长度的 RSA 密钥对
     */
    public static Map<String, String> initRSAKey() {
        return initRSAKey(DEFAULT_KEY_SIZE);
    }

    /**
     * 从 Base64 编码的公钥字符串加载 PublicKey
     */
    public static PublicKey loadPublicKey(String publicKeyBase64) throws GeneralSecurityException {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * 从 Base64 编码的私钥字符串加载 PrivateKey
     */
    public static PrivateKey loadPrivateKey(String privateKeyBase64) throws GeneralSecurityException {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * 公钥加密（支持分段加密，突破长度限制）
     *
     * <p>分段大小根据实际密钥长度动态计算，兼容 2048/3072/4096 位密钥。
     */
    public static String encryptByPublicKey(String data, String publicKey) {
        try {
            PublicKey pubKey = loadPublicKey(publicKey);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);

            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            int maxBlock = getMaxEncryptBlock(pubKey);

            byte[] encryptedBytes = doFinal(cipher, dataBytes, maxBlock);
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("RSA 公钥加密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 私钥解密（支持分段解密）
     *
     * <p>分段大小根据实际密钥长度动态计算，兼容 2048/3072/4096 位密钥。
     */
    public static String decryptByPrivateKey(String data, String privateKey) {
        try {
            PrivateKey priKey = loadPrivateKey(privateKey);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, priKey);

            byte[] dataBytes = Base64.getDecoder().decode(data);
            int maxBlock = getMaxDecryptBlock(priKey);

            byte[] decryptedBytes = doFinal(cipher, dataBytes, maxBlock);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("RSA 私钥解密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 私钥加密
     *
     * <p><b>已废弃</b>：使用 RSA 私钥加密在密码学上不正确，混淆了「加密」与「签名」语义。
     * RSA 私钥操作的正确用途是签名，请使用 {@link #sign(String, String)} 代替。
     *
     * <p>保留该方法仅为兼容旧版调用方，后续版本将移除。
     *
     * @param data       原始数据
     * @param privateKey 私钥（Base64 编码）
     * @return 加密后的 Base64 字符串
     * @deprecated 使用 {@link #sign(String, String)} 进行签名，不应使用私钥加密
     */
    @Deprecated
    public static String encryptByPrivateKey(String data, String privateKey) {
        try {
            PrivateKey priKey = loadPrivateKey(privateKey);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, priKey);

            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            int maxBlock = getMaxEncryptBlock(priKey);

            byte[] encryptedBytes = doFinal(cipher, dataBytes, maxBlock);
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("RSA 私钥加密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 公钥解密
     *
     * <p><b>已废弃</b>：与 {@link #encryptByPrivateKey(String, String)} 配套，
     * 用于解密私钥「加密」的数据。由于私钥加密本身不正确，此方法同样不推荐使用。
     * 验签请使用 {@link #verify(String, String, String)}。
     *
     * @param data      加密数据（Base64 编码）
     * @param publicKey 公钥（Base64 编码）
     * @return 解密后的原始字符串
     * @deprecated 使用 {@link #verify(String, String, String)} 进行验签
     */
    @Deprecated
    public static String decryptByPublicKey(String data, String publicKey) {
        try {
            PublicKey pubKey = loadPublicKey(publicKey);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, pubKey);

            byte[] dataBytes = Base64.getDecoder().decode(data);
            int maxBlock = getMaxDecryptBlock(pubKey);

            byte[] decryptedBytes = doFinal(cipher, dataBytes, maxBlock);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("RSA 公钥解密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据密钥长度动态计算最大加密块
     *
     * <p>OAEPWithSHA-256AndMGF1Padding 填充开销 = 2 * 32 + 2 = 66 字节。
     * 最大加密块 = keySizeBytes - 66。
     *
     * @param key RSA 密钥
     * @return 最大加密块字节数
     */
    private static int getMaxEncryptBlock(Key key) {
        if (key instanceof RSAKey rsaKey) {
            int keySizeBytes = rsaKey.getModulus().bitLength() / 8;
            return keySizeBytes - OAEP_OVERHEAD_BYTES;
        }
        return DEFAULT_MAX_ENCRYPT_BLOCK;
    }

    /**
     * 根据密钥长度动态计算最大解密块
     *
     * <p>最大解密块 = keySizeBytes（密文块长度等于密钥长度字节）。
     *
     * @param key RSA 密钥
     * @return 最大解密块字节数
     */
    private static int getMaxDecryptBlock(Key key) {
        if (key instanceof RSAKey rsaKey) {
            return rsaKey.getModulus().bitLength() / 8;
        }
        return DEFAULT_MAX_DECRYPT_BLOCK;
    }

    /**
     * 分段加密/解密处理
     */
    private static byte[] doFinal(Cipher cipher, byte[] data, int maxBlock) throws GeneralSecurityException, IOException {
        int inputLen = data.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offSet = 0;
        byte[] cache;
        int i = 0;
        
        while (inputLen - offSet > 0) {
            if (inputLen - offSet > maxBlock) {
                cache = cipher.doFinal(data, offSet, maxBlock);
            } else {
                cache = cipher.doFinal(data, offSet, inputLen - offSet);
            }
            out.write(cache, 0, cache.length);
            i++;
            offSet = i * maxBlock;
        }
        
        byte[] resultBytes = out.toByteArray();
        out.close();
        return resultBytes;
    }

    /**
     * 私钥签名 (RSA2 / SHA256withRSA)
     */
    public static String sign(String data, String privateKey) {
        try {
            PrivateKey priKey = loadPrivateKey(privateKey);
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initSign(priKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();
            return Base64.getEncoder().encodeToString(signed);
        } catch (Exception e) {
            throw new RuntimeException("RSA 私钥签名失败：" + e.getMessage(), e);
        }
    }

    /**
     * 公钥验签 (RSA2 / SHA256withRSA)
     *
     * @return 签名匹配返回 true，签名不匹配返回 false；仅当密钥加载/算法初始化等异常时抛 RuntimeException
     */
    public static boolean verify(String data, String publicKey, String signStr) {
        PublicKey pubKey;
        Signature signature;
        try {
            pubKey = loadPublicKey(publicKey);
            signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(pubKey);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("RSA 验签初始化失败: " + e.getMessage(), e);
        }
        try {
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = Base64.getDecoder().decode(signStr);
            return signature.verify(signBytes);
        } catch (SignatureException e) {
            // 签名数据格式异常，按验签失败处理
            return false;
        } catch (Exception e) {
            throw new RuntimeException("RSA 验签异常: " + e.getMessage(), e);
        }
    }

    /**
     * 从 PEM 格式字符串加载公钥
     */
    public static PublicKey loadPublicKeyFromPEM(String pem) throws GeneralSecurityException {
        String publicKeyPEM = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                                  .replace("-----END PUBLIC KEY-----", "")
                                  .replaceAll("\\s+", "");
        return loadPublicKey(publicKeyPEM);
    }

    /**
     * 从 PEM 格式字符串加载私钥
     */
    public static PrivateKey loadPrivateKeyFromPEM(String pem) throws GeneralSecurityException {
        String privateKeyPEM = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                                   .replace("-----END PRIVATE KEY-----", "")
                                   .replaceAll("\\s+", "");
        return loadPrivateKey(privateKeyPEM);
    }

    /**
     * 将公钥转换为 PEM 格式
     */
    public static String publicKeyToPEM(String publicKeyBase64) {
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PUBLIC KEY-----\n");
        pem.append(formatBase64(publicKeyBase64));
        pem.append("\n-----END PUBLIC KEY-----");
        return pem.toString();
    }

    /**
     * 将私钥转换为 PEM 格式
     */
    public static String privateKeyToPEM(String privateKeyBase64) {
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PRIVATE KEY-----\n");
        pem.append(formatBase64(privateKeyBase64));
        pem.append("\n-----END PRIVATE KEY-----");
        return pem.toString();
    }

    /**
     * 格式化 Base64 字符串（每行 64 个字符）
     */
    private static String formatBase64(String base64) {
        StringBuilder formatted = new StringBuilder();
        int len = base64.length();
        for (int i = 0; i < len; i += 64) {
            if (i > 0) {
                formatted.append("\n");
            }
            formatted.append(base64.substring(i, Math.min(i + 64, len)));
        }
        return formatted.toString();
    }

    /**
     * 验证密钥对是否匹配
     */
    public static boolean verifyKeyPair(String publicKey, String privateKey) {
        try {
            String testData = "test_data_for_key_verification";
            String encrypted = encryptByPublicKey(testData, publicKey);
            String decrypted = decryptByPrivateKey(encrypted, privateKey);
            return testData.equals(decrypted);
        } catch (Exception e) {
            return false;
        }
    }
}
