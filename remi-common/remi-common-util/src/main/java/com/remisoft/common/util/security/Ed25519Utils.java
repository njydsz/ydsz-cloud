package com.remisoft.common.util.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Ed25519 签名算法工具类。
 *
 * <p>基于 RFC 8032 定义的 Ed25519 椭圆曲线签名算法，使用 Curve25519 配套曲线。
 * Ed25519 是现代主流签名方案，已被 TLS 1.3、WireGuard、SSH、OpenSSL 等广泛采用。
 *
 * <h2>核心能力</h2>
 * <ul>
 *   <li>密钥长度：256 位（32 字节私钥/公钥）</li>
 *   <li>签名长度：512 位（64 字节）</li>
 *   <li>算法：EdDSA over Curve25519（Edwards form）</li>
 *   <li>确定性签名：相同输入+密钥总是产生相同签名（无需随机数生成器，避免 nonce 重用攻击）</li>
 * </ul>
 *
 * <h2>与 RSA/ECDSA 对比</h2>
 * <ul>
 *   <li>比 RSA-2048 签名快 10-30 倍，验签快 3-5 倍</li>
 *   <li>比 ECDSA 更安全（确定性算法消除 nonce 侧信道风险）</li>
 *   <li>签名短小（64 字节 vs RSA-2048 的 256 字节）</li>
 *   <li>私钥短小（32 字节 vs RSA-2048 的 256 字节）</li>
 * </ul>
 *
 * <h2>适用场景</h2>
 * <ul>
 *   <li>JWS (JSON Web Tokens) 的 EdDSA 算法</li>
 *   <li>SSH 密钥交换和主机认证</li>
 *   <li>TLS 1.3 客户端证书</li>
 *   <li>区块链、加密货币地址签名</li>
 *   <li>替代 RSA/ECDSA 的所有数字签名场景</li>
 * </ul>
 *
 * <h2>安全说明</h2>
 * <ul>
 *   <li>JDK 要求 JDK 15+（推荐 JDK 17+）</li>
 *   <li>私钥字节为 PKCS8 编码后的裸密钥部分</li>
 *   <li>公钥字节为 X509 编码后的裸密钥部分</li>
 *   <li>为确定性签名算法，不需要随机数（但 {@link SecureRandom} 在密钥生成时仍需使用）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.4.0
 */
public class Ed25519Utils {

    /**
     * 算法名称（JDK 标准名称，覆盖 Ed25519 / Ed448）。
     */
    private static final String ALGORITHM = "Ed25519";

    /**
     * 密钥工厂算法（X.509 / PKCS8）。
     */
    private static final String KEY_ALGORITHM = "EC";

    /**
     * 私钥长度（32 字节）。
     */
    private static final int PRIVATE_KEY_LENGTH = 32;

    /**
     * 公钥长度（32 字节）。
     */
    private static final int PUBLIC_KEY_LENGTH = 32;

    /**
     * 签名长度（64 字节）。
     */
    private static final int SIGNATURE_LENGTH = 64;

    /**
     * 共享的 SecureRandom 实例（线程安全，用于密钥对生成）。
     */
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    /**
     * Signature 实例缓存（ThreadLocal 池化）。
     */
    private static final ThreadLocal<Signature> SIGNATURE_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return Signature.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 not supported (requires JDK 15+)", e);
        }
    });

    /**
     * KeyPairGenerator 实例缓存（ThreadLocal 池化）。
     * <p>非线程安全，需要 ThreadLocal 隔离。
     */
    private static final ThreadLocal<KeyPairGenerator> KEY_PAIR_GENERATOR_CACHE = ThreadLocal.withInitial(() -> {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);
            return kpg;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 not supported (requires JDK 15+)", e);
        }
    });

    /**
     * KeyFactory 实例缓存（ThreadLocal 池化）。
     */
    private static final ThreadLocal<KeyFactory> KEY_FACTORY_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return KeyFactory.getInstance(KEY_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("EC KeyFactory not supported", e);
        }
    });

    /**
     * 私有构造器，工具类不允许实例化。
     */
    private Ed25519Utils() {
        throw new UnsupportedOperationException("Ed25519Utils is a utility class and cannot be instantiated");
    }

    // ==================== 密钥对生成 ====================

    /**
     * 生成新的 Ed25519 密钥对。
     *
     * @return 新的 KeyPair 实例
     */
    public static KeyPair generateKeyPair() {
        return KEY_PAIR_GENERATOR_CACHE.get().generateKeyPair();
    }

    /**
     * 生成密钥对并返回 Hex 编码的密钥。
     *
     * @return KeyPairHex 实例（包含 Hex 编码的公钥和私钥）
     */
    public static KeyPairHex generateKeyPairHex() {
        KeyPair kp = generateKeyPair();
        return new KeyPairHex(
            HexFormat.of().formatHex(kp.getPrivate().getEncoded()),
            HexFormat.of().formatHex(kp.getPublic().getEncoded())
        );
    }

    /**
     * 生成密钥对并返回 Base64 编码的密钥。
     *
     * @return KeyPairBase64 实例
     */
    public static KeyPairBase64 generateKeyPairBase64() {
        KeyPair kp = generateKeyPair();
        return new KeyPairBase64(
            Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()),
            Base64.getEncoder().encodeToString(kp.getPublic().getEncoded())
        );
    }

    // ==================== 密钥加载（Hex） ====================

    /**
     * 从 Hex 编码的 PKCS8 字节加载私钥。
     *
     * @param privateKeyHex PKCS8 编码私钥的 Hex 字符串
     * @return PrivateKey 实例
     */
    public static PrivateKey loadPrivateKeyHex(String privateKeyHex) {
        return loadPrivateKey(HexFormat.of().parseHex(privateKeyHex));
    }

    /**
     * 从 Hex 编码的 X509 字节加载公钥。
     *
     * @param publicKeyHex X509 编码公钥的 Hex 字符串
     * @return PublicKey 实例
     */
    public static PublicKey loadPublicKeyHex(String publicKeyHex) {
        return loadPublicKey(HexFormat.of().parseHex(publicKeyHex));
    }

    // ==================== 密钥加载（Base64） ====================

    /**
     * 从 Base64 编码的 PKCS8 字节加载私钥。
     *
     * @param privateKeyBase64 PKCS8 编码私钥的 Base64 字符串
     * @return PrivateKey 实例
     */
    public static PrivateKey loadPrivateKeyBase64(String privateKeyBase64) {
        return loadPrivateKey(Base64.getDecoder().decode(privateKeyBase64));
    }

    /**
     * 从 Base64 编码的 X509 字节加载公钥。
     *
     * @param publicKeyBase64 X509 编码公钥的 Base64 字符串
     * @return PublicKey 实例
     */
    public static PublicKey loadPublicKeyBase64(String publicKeyBase64) {
        return loadPublicKey(Base64.getDecoder().decode(publicKeyBase64));
    }

    // ==================== 密钥加载（字节数组） ====================

    /**
     * 从 PKCS8 编码字节加载私钥。
     *
     * @param pkcs8Bytes PKCS8 编码的私钥
     * @return PrivateKey 实例
     */
    public static PrivateKey loadPrivateKey(byte[] pkcs8Bytes) {
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8Bytes);
            return KEY_FACTORY_CACHE.get().generatePrivate(spec);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid PKCS8 encoded Ed25519 private key", e);
        }
    }

    /**
     * 从 X509 编码字节加载公钥。
     *
     * @param x509Bytes X509 编码的公钥
     * @return PublicKey 实例
     */
    public static PublicKey loadPublicKey(byte[] x509Bytes) {
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(x509Bytes);
            return KEY_FACTORY_CACHE.get().generatePublic(spec);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid X509 encoded Ed25519 public key", e);
        }
    }

    // ==================== 密钥提取（原始裸密钥） ====================

    /**
     * 提取私钥的原始 32 字节裸密钥（去掉 PKCS8 容器头）。
     *
     * @param privateKey Ed25519 私钥
     * @return 32 字节裸私钥
     */
    public static byte[] getRawPrivateKeyBytes(PrivateKey privateKey) {
        byte[] encoded = privateKey.getEncoded();
        // PKCS8 头：30 2e 02 01 00 30 05 06 03 2b 65 70 04 22 04 20（16 字节头）
        if (encoded.length < PRIVATE_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid Ed25519 private key encoding: too short");
        }
        int offset = encoded.length - PRIVATE_KEY_LENGTH;
        byte[] raw = new byte[PRIVATE_KEY_LENGTH];
        System.arraycopy(encoded, offset, raw, 0, PRIVATE_KEY_LENGTH);
        return raw;
    }

    /**
     * 提取公钥的原始 32 字节裸密钥（去掉 X509 容器头）。
     *
     * <p>Ed25519 公钥标准 X509 编码为 12 字节头（30 2a 30 05 06 03 2b 65 70 03 21 00）+ 32 字节密钥。
     *
     * @param publicKey Ed25519 公钥
     * @return 32 字节裸公钥
     */
    public static byte[] getRawPublicKeyBytes(PublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();
        if (encoded.length < PUBLIC_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid Ed25519 public key encoding: too short");
        }
        int offset = encoded.length - PUBLIC_KEY_LENGTH;
        byte[] raw = new byte[PUBLIC_KEY_LENGTH];
        System.arraycopy(encoded, offset, raw, 0, PUBLIC_KEY_LENGTH);
        return raw;
    }

    /**
     * 将原始 32 字节裸私钥重建为 PrivateKey。
     *
     * @param rawPrivateKey 32 字节裸私钥
     * @return PrivateKey 实例
     */
    public static PrivateKey privateKeyFromRaw(byte[] rawPrivateKey) {
        if (rawPrivateKey == null || rawPrivateKey.length != PRIVATE_KEY_LENGTH) {
            throw new IllegalArgumentException("Raw Ed25519 private key must be exactly 32 bytes");
        }
        // 构造 PKCS8 包装：SEQUENCE { INTEGER(0), SEQUENCE { OID(1.3.101.112) }, OCTET STRING(裸密钥) }
        byte[] pkcs8 = new byte[]{
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
        };
        byte[] result = new byte[pkcs8.length + rawPrivateKey.length];
        System.arraycopy(pkcs8, 0, result, 0, pkcs8.length);
        System.arraycopy(rawPrivateKey, 0, result, pkcs8.length, rawPrivateKey.length);
        return loadPrivateKey(result);
    }

    /**
     * 将原始 32 字节裸公钥重建为 PublicKey。
     *
     * @param rawPublicKey 32 字节裸公钥
     * @return PublicKey 实例
     */
    public static PublicKey publicKeyFromRaw(byte[] rawPublicKey) {
        if (rawPublicKey == null || rawPublicKey.length != PUBLIC_KEY_LENGTH) {
            throw new IllegalArgumentException("Raw Ed25519 public key must be exactly 32 bytes");
        }
        // 构造 X509 SubjectPublicKeyInfo：SEQUENCE { SEQUENCE { OID(1.3.101.112) }, BIT STRING(裸公钥) }
        byte[] x509 = new byte[]{
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        };
        byte[] result = new byte[x509.length + rawPublicKey.length];
        System.arraycopy(x509, 0, result, 0, x509.length);
        System.arraycopy(rawPublicKey, 0, result, x509.length, rawPublicKey.length);
        return loadPublicKey(result);
    }

    // ==================== 签名 ====================

    /**
     * 使用私钥对消息进行签名（返回原始签名字节）。
     *
     * @param message    消息字节数组
     * @param privateKey Ed25519 私钥
     * @return 签名字节数组（64 字节）
     */
    public static byte[] sign(byte[] message, PrivateKey privateKey) {
        try {
            Signature signature = SIGNATURE_CACHE.get();
            signature.initSign(privateKey);
            signature.update(message);
            return signature.sign();
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid Ed25519 private key", e);
        } catch (SignatureException e) {
            throw new IllegalStateException("Ed25519 signing failed", e);
        }
    }

    /**
     * 使用私钥对字符串进行签名（返回 Hex 编码签名）。
     *
     * @param message    明文字符串（UTF-8）
     * @param privateKey Ed25519 私钥
     * @return Hex 编码的签名（128 字符 = 64 字节）
     */
    public static String signHex(String message, PrivateKey privateKey) {
        return HexFormat.of().formatHex(
            sign(message.getBytes(StandardCharsets.UTF_8), privateKey)
        );
    }

    /**
     * 使用私钥对字符串进行签名（返回 Base64 编码签名）。
     *
     * @param message    明文字符串（UTF-8）
     * @param privateKey Ed25519 私钥
     * @return Base64 编码的签名
     */
    public static String signBase64(String message, PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(
            sign(message.getBytes(StandardCharsets.UTF_8), privateKey)
        );
    }

    // ==================== 验签 ====================

    /**
     * 使用公钥验证签名。
     *
     * @param message   原始消息字节数组
     * @param signature 签名字节数组（64 字节）
     * @param publicKey Ed25519 公钥
     * @return 签名有效返回 true，否则返回 false
     */
    public static boolean verify(byte[] message, byte[] signature, PublicKey publicKey) {
        try {
            Signature sig = SIGNATURE_CACHE.get();
            sig.initVerify(publicKey);
            sig.update(message);
            return sig.verify(signature);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid Ed25519 public key", e);
        } catch (SignatureException e) {
            // 验签失败不一定是异常，可能是签名伪造
            return false;
        }
    }

    /**
     * 使用公钥验证 Hex 编码的签名。
     *
     * @param message      明文字符串（UTF-8）
     * @param signatureHex Hex 编码的签名（128 字符）
     * @param publicKey    Ed25519 公钥
     * @return 签名有效返回 true，否则返回 false
     */
    public static boolean verifyHex(String message, String signatureHex, PublicKey publicKey) {
        return verify(
            message.getBytes(StandardCharsets.UTF_8),
            HexFormat.of().parseHex(signatureHex),
            publicKey
        );
    }

    /**
     * 使用公钥验证 Base64 编码的签名。
     *
     * @param message         明文字符串（UTF-8）
     * @param signatureBase64 Base64 编码的签名
     * @param publicKey       Ed25519 公钥
     * @return 签名有效返回 true，否则返回 false
     */
    public static boolean verifyBase64(String message, String signatureBase64, PublicKey publicKey) {
        return verify(
            message.getBytes(StandardCharsets.UTF_8),
            Base64.getDecoder().decode(signatureBase64),
            publicKey
        );
    }

    // ==================== 密钥序列化 ====================

    /**
     * 将私钥编码为 Hex（PKCS8 格式）。
     */
    public static String encodePrivateKeyHex(PrivateKey privateKey) {
        return HexFormat.of().formatHex(privateKey.getEncoded());
    }

    /**
     * 将公钥编码为 Hex（X509 格式）。
     */
    public static String encodePublicKeyHex(PublicKey publicKey) {
        return HexFormat.of().formatHex(publicKey.getEncoded());
    }

    /**
     * 将私钥编码为 Base64（PKCS8 格式）。
     */
    public static String encodePrivateKeyBase64(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * 将公钥编码为 Base64（X509 格式）。
     */
    public static String encodePublicKeyBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 将裸私钥编码为 Hex（32 字节原始密钥）。
     */
    public static String encodeRawPrivateKeyHex(PrivateKey privateKey) {
        return HexFormat.of().formatHex(getRawPrivateKeyBytes(privateKey));
    }

    /**
     * 将裸公钥编码为 Hex（32 字节原始密钥）。
     */
    public static String encodeRawPublicKeyHex(PublicKey publicKey) {
        return HexFormat.of().formatHex(getRawPublicKeyBytes(publicKey));
    }

    // ==================== 密钥长度信息 ====================

    /**
     * 返回私钥长度（32 字节 = 256 位）。
     */
    public static int getPrivateKeyLength() {
        return PRIVATE_KEY_LENGTH;
    }

    /**
     * 返回公钥长度（32 字节 = 256 位）。
     */
    public static int getPublicKeyLength() {
        return PUBLIC_KEY_LENGTH;
    }

    /**
     * 返回签名长度（64 字节 = 512 位）。
     */
    public static int getSignatureLength() {
        return SIGNATURE_LENGTH;
    }

    /**
     * 返回底层算法参数规格（NamedParameterSpec.ED25519）。
     */
    public static NamedParameterSpec getParameterSpec() {
        return NamedParameterSpec.ED25519;
    }

    // ==================== 内部类 ====================

    /**
     * Hex 格式密钥对封装。
     */
    public static class KeyPairHex {
        private final String privateKey;
        private final String publicKey;

        public KeyPairHex(String privateKey, String publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public PrivateKey toPrivateKey() {
            return loadPrivateKeyHex(privateKey);
        }

        public PublicKey toPublicKey() {
            return loadPublicKeyHex(publicKey);
        }

        @Override
        public String toString() {
            return "KeyPairHex{privateKey='" + privateKey + "', publicKey='" + publicKey + "'}";
        }
    }

    /**
     * Base64 格式密钥对封装。
     */
    public static class KeyPairBase64 {
        private final String privateKey;
        private final String publicKey;

        public KeyPairBase64(String privateKey, String publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public PrivateKey toPrivateKey() {
            return loadPrivateKeyBase64(privateKey);
        }

        public PublicKey toPublicKey() {
            return loadPublicKeyBase64(publicKey);
        }

        @Override
        public String toString() {
            return "KeyPairBase64{privateKey='" + privateKey + "', publicKey='" + publicKey + "'}";
        }
    }
}
