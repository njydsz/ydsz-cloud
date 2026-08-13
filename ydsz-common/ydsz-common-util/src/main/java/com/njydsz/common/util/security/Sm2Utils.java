package com.njydsz.common.util.security;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

import javax.crypto.Cipher;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * SM2 椭圆曲线公钥密码算法工具类
 *
 * <p>基于 256 位素数域椭圆曲线，安全性对标 RSA-3072，加解密/签名性能优于 RSA，
 * 符合国密标准 GM/T 0003-2012。纯 BouncyCastle 实现。
 *
 * <h2>核心能力</h2>
 * <ul>
 *   <li>密钥对生成：生成 SM2 密钥对（sm2p256v1 曲线）</li>
 *   <li>公钥加密/私钥解密：C1C3C2 密文格式（国密标准推荐）</li>
 *   <li>私钥签名/公钥验签：SM3withSM2 签名算法</li>
 *   <li>支持 Hex/Base64 编码的密钥导入导出</li>
 * </ul>
 *
 * <p>通过 JCA 委托给 BouncyCastle Provider，需要 {@code bcprov-jdk18on} 在 classpath 上。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 1. 生成密钥对
 * KeyPair keyPair = Sm2Utils.generateKeyPair();
 * String publicKeyHex = Sm2Utils.encodePublicKey(keyPair.getPublic());
 * String privateKeyHex = Sm2Utils.encodePrivateKey(keyPair.getPrivate());
 *
 * // 2. 公钥加密
 * String ciphertext = Sm2Utils.encrypt("Hello SM2", keyPair.getPublic());
 *
 * // 3. 私钥解密
 * String plaintext = Sm2Utils.decrypt(ciphertext, keyPair.getPrivate());
 *
 * // 4. 签名
 * String signature = Sm2Utils.sign("data", keyPair.getPrivate());
 *
 * // 5. 验签
 * boolean valid = Sm2Utils.verify("data", signature, keyPair.getPublic());
 * }</pre>
 *
 * <p><b>安全说明：</b>
 * <ul>
 *   <li>算法强度 256 位，相当于 RSA-3072</li>
 *   <li>私钥长度远小于 RSA，签名速度更快</li>
 *   <li>国密合规场景下优先使用 SM2 替代 RSA</li>
 *   <li>签名默认使用 SM3 摘要算法</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@Slf4j
public final class Sm2Utils {

    /** 椭圆曲线名称（国密标准曲线） */
    private static final String EC_CURVE_NAME = "sm2p256v1";

    /** 密钥算法 */
    private static final String KEY_ALGORITHM = "EC";

    /** 签名算法 */
    private static final String SIGN_ALGORITHM = "SM3withSM2";

    /** 加密算法（BouncyCastle SM2 JCA 支持） */
    private static final String ENCRYPT_ALGORITHM = "SM2";

    /**
     * SM2 加密 Cipher 的 ThreadLocal 池。
     *
     * <p>Cipher 实例非线程安全，按线程独享并复用，避免每次调用都执行
     * {@code Cipher.getInstance("SM2", "BC")} 的 Provider 查找开销。</p>
     */
    private static final ThreadLocal<Cipher> ENCRYPT_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            BcProvider.ensure();
            return Cipher.getInstance("SM2", BouncyCastleProvider.PROVIDER_NAME);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Failed to initialize SM2 Cipher (ensure bcprov-jdk18on is on classpath)", e);
        }
    });

    /**
     * SM3withSM2 签名 Signature 的 ThreadLocal 池。
     */
    private static final ThreadLocal<Signature> SIGNATURE = ThreadLocal.withInitial(() -> {
        try {
            BcProvider.ensure();
            return Signature.getInstance("SM3withSM2", BouncyCastleProvider.PROVIDER_NAME);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Failed to initialize SM3withSM2 Signature (ensure bcprov-jdk18on is on classpath)", e);
        }
    });

    /**
     * 获取本线程的 SM2 加密 Cipher 实例。
     *
     * @return 本线程的 SM2 Cipher 实例
     */
    private static Cipher acquireEncryptCipher() {
        return ENCRYPT_CIPHER.get();
    }

    /**
     * 获取本线程的 SM3withSM2 签名 Signature 实例。
     *
     * @return 本线程的 SM3withSM2 Signature 实例
     */
    private static Signature acquireSignature() {
        return SIGNATURE.get();
    }

    /** 共享的 SecureRandom */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Hex 编码器 */
    private static final HexFormat HEX = HexFormat.of();

    private Sm2Utils() {
        throw new UnsupportedOperationException("Sm2Utils is a utility class");
    }

    // ==================== 密钥对生成 ====================

    /**
     * 生成 SM2 密钥对（sm2p256v1 曲线）
     *
     * <p>使用密码学安全的随机数生成器，密钥强度 256 位。
     *
     * @return SM2 密钥对
     * @throws IllegalStateException 密钥生成失败时抛出
     */
    public static KeyPair generateKeyPair() {
        BcProvider.ensure(); // 幂等注册 BC Provider，避免首次调用抛 NoSuchProviderException
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(new ECGenParameterSpec(EC_CURVE_NAME), SECURE_RANDOM);
            return kpg.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to generate SM2 key pair", e);
        }
    }

    /**
     * 从 Base64 编码的公钥还原 {@link PublicKey}
     *
     * @param base64PublicKey Base64 X.509 编码公钥
     * @return 公钥对象
     */
    public static PublicKey loadPublicKey(String base64PublicKey) {
        BcProvider.ensure(); // 幂等注册 BC Provider
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            return kf.generatePublic(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid SM2 public key", e);
        }
    }

    /**
     * 从 Base64 编码的私钥还原 {@link PrivateKey}
     *
     * @param base64PrivateKey Base64 PKCS8 编码私钥
     * @return 私钥对象
     */
    public static PrivateKey loadPrivateKey(String base64PrivateKey) {
        BcProvider.ensure(); // 幂等注册 BC Provider
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            return kf.generatePrivate(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid SM2 private key", e);
        }
    }

    /**
     * 从字节数组还原公钥
     *
     * @param keyBytes X.509 SubjectPublicKeyInfo 编码
     * @return 公钥对象
     */
    public static PublicKey decodePublicKey(byte[] keyBytes) {
        BcProvider.ensure(); // 幂等注册 BC Provider
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            return kf.generatePublic(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid SM2 public key bytes", e);
        }
    }

    /**
     * 从字节数组还原私钥
     *
     * @param keyBytes PKCS8 编码私钥
     * @return 私钥对象
     */
    public static PrivateKey decodePrivateKey(byte[] keyBytes) {
        BcProvider.ensure(); // 幂等注册 BC Provider
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            return kf.generatePrivate(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid SM2 private key bytes", e);
        }
    }

    // ==================== 密钥编码 ====================

    /**
     * 将公钥编码为 Hex 字符串（X.509 格式）
     *
     * @param publicKey 公钥
     * @return Hex 编码字符串；publicKey 为 null 时返回 null
     */
    public static String encodePublicKeyHex(PublicKey publicKey) {
        if (publicKey == null) {
            return null;
        }
        return HEX.formatHex(publicKey.getEncoded());
    }

    /**
     * 将公钥编码为 Base64 字符串（X.509 格式）
     *
     * @param publicKey 公钥
     * @return Base64 编码字符串；publicKey 为 null 时返回 null
     */
    public static String encodePublicKeyBase64(PublicKey publicKey) {
        if (publicKey == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 将私钥编码为 Hex 字符串（PKCS8 格式）
     *
     * @param privateKey 私钥
     * @return Hex 编码字符串；privateKey 为 null 时返回 null
     */
    public static String encodePrivateKeyHex(PrivateKey privateKey) {
        if (privateKey == null) {
            return null;
        }
        return HEX.formatHex(privateKey.getEncoded());
    }

    /**
     * 将私钥编码为 Base64 字符串（PKCS8 格式）
     *
     * @param privateKey 私钥
     * @return Base64 编码字符串；privateKey 为 null 时返回 null
     */
    public static String encodePrivateKeyBase64(PrivateKey privateKey) {
        if (privateKey == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    // ==================== 加密 / 解密 ====================

    /**
     * 公钥加密（字符串输入，Base64 输出）
     *
     * <p>密文格式为 C1C3C2（国密标准推荐），C1 为随机公钥点，
     * C3 为 SM3 摘要，C2 为密文数据。
     *
     * @param content   明文（UTF-8 编码）；不可为 null
     * @param publicKey SM2 公钥
     * @return Base64 编码密文
     */
    public static String encrypt(String content, PublicKey publicKey) {
        Objects.requireNonNull(content, "content must not be null");
        byte[] ciphertext = encrypt(content.getBytes(StandardCharsets.UTF_8), publicKey);
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * 公钥加密（字节输入）
     *
     * <p>输出：C1(65 字节未压缩公钥点) || C3(32 字节 SM3 摘要) || C2(密文)。
     *
     * @param content   明文字节数组；不可为 null
     * @param publicKey SM2 公钥
     * @return 密文字节数组（C1C3C2 格式）
     */
    public static byte[] encrypt(byte[] content, PublicKey publicKey) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        try {
            Cipher cipher = acquireEncryptCipher();
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(content);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SM2 encryption failed", e);
        }
    }

    /**
     * 私钥解密（Base64 输入，字符串输出）
     *
     * @param base64Ciphertext Base64 编码密文
     * @param privateKey       SM2 私钥
     * @return 明文字符串（UTF-8）
     */
    public static String decrypt(String base64Ciphertext, PrivateKey privateKey) {
        Objects.requireNonNull(base64Ciphertext, "ciphertext must not be null");
        byte[] plaintext = decrypt(Base64.getDecoder().decode(base64Ciphertext), privateKey);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * 私钥解密（字节输入）
     *
     * @param ciphertext 密文（C1C3C2 格式）
     * @param privateKey SM2 私钥
     * @return 明文字节数组
     */
    public static byte[] decrypt(byte[] ciphertext, PrivateKey privateKey) {
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        try {
            Cipher cipher = acquireEncryptCipher();
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SM2 decryption failed, ciphertext may be corrupted or wrong key", e);
        }
    }

    // ==================== 签名 / 验签 ====================

    /**
     * 私钥签名（字符串输入，Base64 输出）
     *
     * <p>内部使用 SM3 摘要 + SM2 签名算法，签名值为 DER 编码的 (r, s) 对。
     *
     * @param content    待签名内容（UTF-8 编码）；不可为 null
     * @param privateKey SM2 私钥
     * @return Base64 编码签名
     */
    public static String sign(String content, PrivateKey privateKey) {
        Objects.requireNonNull(content, "content must not be null");
        byte[] signature = sign(content.getBytes(StandardCharsets.UTF_8), privateKey);
        return Base64.getEncoder().encodeToString(signature);
    }

    /**
     * 私钥签名（字节输入）
     *
     * @param content    内容字节数组；不可为 null
     * @param privateKey SM2 私钥
     * @return DER 编码签名值
     */
    public static byte[] sign(byte[] content, PrivateKey privateKey) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        try {
            Signature sig = acquireSignature();
            sig.initSign(privateKey);
            sig.update(content);
            return sig.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SM2 signing failed", e);
        }
    }

    /**
     * 公钥验签（字符串内容，Base64 签名）
     *
     * @param content       原始内容（UTF-8 编码）；不可为 null
     * @param base64Sig     Base64 编码签名
     * @param publicKey     SM2 公钥
     * @return true 表示签名有效
     */
    public static boolean verify(String content, String base64Sig, PublicKey publicKey) {
        Objects.requireNonNull(content, "content must not be null");
        return verify(content.getBytes(StandardCharsets.UTF_8),
                Base64.getDecoder().decode(base64Sig), publicKey);
    }

    /**
     * 公钥验签（字节输入）
     *
     * @param content   原始内容字节数组；不可为 null
     * @param signature DER 编码签名值
     * @param publicKey SM2 公钥
     * @return true 表示签名有效
     */
    public static boolean verify(byte[] content, byte[] signature, PublicKey publicKey) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        try {
            Signature sig = acquireSignature();
            sig.initVerify(publicKey);
            sig.update(content);
            return sig.verify(signature);
        } catch (GeneralSecurityException e) {
            log.warn("SM2 signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 字节数组转 Hex 字符串
     *
     * @param bytes 字节数组
     * @return Hex 字符串；bytes 为 null 时返回 null
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return HEX.formatHex(bytes);
    }

    /**
     * Hex 字符串转字节数组
     *
     * @param hex Hex 字符串（偶数长度）
     * @return 字节数组
     * @throws IllegalArgumentException hex 为 null 或长度为奇数
     */
    public static byte[] fromHex(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must not be null and must have even length");
        }
        return HEX.parseHex(hex);
    }

    /**
     * 获取密钥对的公钥坐标（Hex 格式，用于调试或低级 API 对接）
     *
     * <p>公钥曲线点坐标 (x, y)，各 32 字节。未压缩格式以 0x04 开头。
     *
     * @param publicKey SM2 公钥
     * @return 包含 x、y 坐标的 Hex 字符串数组，[0]=x, [1]=y
     */
    public static String[] getPublicKeyCoordinates(PublicKey publicKey) {
        if (publicKey == null) {
            return null;
        }
        try {
            BCECPublicKey pk = (BCECPublicKey) publicKey;
            ECPoint point = pk.getQ();
            BigInteger x = point.getAffineXCoord().toBigInteger();
            BigInteger y = point.getAffineYCoord().toBigInteger();
            return new String[]{
                    toHex(padTo32Bytes(x.toByteArray())),
                    toHex(padTo32Bytes(y.toByteArray()))
            };
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract public key coordinates", e);
        }
    }

    /**
     * 将字节数组左补零或截断至 32 字节
     */
    private static byte[] padTo32Bytes(byte[] bytes) {
        if (bytes.length == 32) {
            return bytes;
        }
        byte[] result = new byte[32];
        if (bytes.length > 32) {
            System.arraycopy(bytes, bytes.length - 32, result, 0, 32);
        } else {
            System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
        }
        return result;
    }

    /**
     * 简易密钥对信息 DTO
     */
    public static class KeyPairHex {
        public final String publicKey;
        public final String privateKey;

        public KeyPairHex(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
    }

    /**
     * 生成密钥对并以 Hex 格式返回（适用于脚本/配置文件场景）
     *
     * @return 包含 publicKey(Hex, X.509) 和 privateKey(Hex, PKCS8) 的 KeyPairHex
     */
    public static KeyPairHex generateKeyPairHex() {
        KeyPair kp = generateKeyPair();
        return new KeyPairHex(
                encodePublicKeyHex(kp.getPublic()),
                encodePrivateKeyHex(kp.getPrivate())
        );
    }

    /**
     * 生成密钥对并以 Base64 格式返回
     *
     * @return 包含 publicKey(Base64, X.509) 和 privateKey(Base64, PKCS8) 的 KeyPairHex
     */
    public static KeyPairHex generateKeyPairBase64() {
        KeyPair kp = generateKeyPair();
        return new KeyPairHex(
                encodePublicKeyBase64(kp.getPublic()),
                encodePrivateKeyBase64(kp.getPrivate())
        );
    }
}
