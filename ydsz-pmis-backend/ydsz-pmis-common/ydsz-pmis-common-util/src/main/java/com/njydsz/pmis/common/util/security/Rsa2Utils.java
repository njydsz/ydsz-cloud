package com.njydsz.pmis.common.util.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;

import lombok.extern.slf4j.Slf4j;
/**
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @desc Rsa2Utils - RSA (SHA256withRSA) 加解密与签名工具类（纯 JDK 实现，零第三方依赖）
 */
@Slf4j
public class Rsa2Utils {

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
     * 建议密钥长度
     */
    public static final int KEY_SIZE_2048 = 2048;

    /**
     * 默认密钥长度
     */
    public static final int DEFAULT_KEY_SIZE = 2048;

    /**
     * RSA 最大加密字节数（OAEPWithSHA-256: 密钥长度/8 - 2*32 - 2）
     */
    private static final int MAX_ENCRYPT_BLOCK_2048 = 190;

    /**
     * RSA 最大解密字节数（密钥长度/8）
     */
    private static final int MAX_DECRYPT_BLOCK_2048 = 256;

    /**
     * 生成 RSA 密钥对
     *
     * @param keySize 密钥长度，建议 2048 或更高
     * @return 包含 publicKey 和 privateKey 的 Map (Base64 编码)
     */
    public static Map<String, String> initRSAKey(int keySize) {
        try {
            if (keySize < 1024) {
                throw new IllegalArgumentException("Key size must be at least 1024 bits");
            }
            
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
            keyPairGen.initialize(keySize);
            
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
     */
    public static String encryptByPublicKey(String data, String publicKey) {
        try {
            PublicKey pubKey = loadPublicKey(publicKey);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);
            
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            int maxBlock = MAX_ENCRYPT_BLOCK_2048;
            
            byte[] encryptedBytes = doFinal(cipher, dataBytes, maxBlock);
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("RSA 公钥加密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 私钥解密（支持分段解密）
     */
    public static String decryptByPrivateKey(String data, String privateKey) {
        try {
            PrivateKey priKey = loadPrivateKey(privateKey);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, priKey);
            
            byte[] dataBytes = Base64.getDecoder().decode(data);
            int maxBlock = MAX_DECRYPT_BLOCK_2048;
            
            byte[] decryptedBytes = doFinal(cipher, dataBytes, maxBlock);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("RSA 私钥解密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 私钥加密（用于签名场景）
     */
    public static String encryptByPrivateKey(String data, String privateKey) {
        try {
            PrivateKey priKey = loadPrivateKey(privateKey);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, priKey);
            
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            int maxBlock = MAX_ENCRYPT_BLOCK_2048;
            
            byte[] encryptedBytes = doFinal(cipher, dataBytes, maxBlock);
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("RSA 私钥加密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 公钥解密（用于验证签名场景）
     */
    public static String decryptByPublicKey(String data, String publicKey) {
        try {
            PublicKey pubKey = loadPublicKey(publicKey);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, pubKey);
            
            byte[] dataBytes = Base64.getDecoder().decode(data);
            int maxBlock = MAX_DECRYPT_BLOCK_2048;
            
            byte[] decryptedBytes = doFinal(cipher, dataBytes, maxBlock);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("RSA 公钥解密失败：" + e.getMessage(), e);
        }
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
     */
    public static boolean verify(String data, String publicKey, String signStr) {
        try {
            PublicKey pubKey = loadPublicKey(publicKey);
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(pubKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = Base64.getDecoder().decode(signStr);
            return signature.verify(signBytes);
        } catch (Exception e) {
            throw new RuntimeException("RSA验签异常: " + e.getMessage(), e);
        }
    }

    /**
     * 生成 RSA 密钥对（带注释）
     * @param keySize 密钥长度
     * @param comment 注释（可选）
     * @return 包含 publicKey、privateKey 和 comment 的 Map
     */
    public static Map<String, Object> initRSAKeyWithComment(int keySize, String comment) {
        Map<String, String> keyPair = initRSAKey(keySize);
        Map<String, Object> result = new HashMap<>(3);
        result.put("publicKey", keyPair.get("publicKey"));
        result.put("privateKey", keyPair.get("privateKey"));
        if (comment != null) {
            result.put("comment", comment);
        }
        return result;
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
