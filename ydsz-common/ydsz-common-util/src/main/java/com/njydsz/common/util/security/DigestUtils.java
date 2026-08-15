package com.njydsz.common.util.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 不可逆加密工具类（纯 JDK 实现，零第三方依赖）
 *
 * <p>提供 MD5、SHA-1、SHA-256、SHA-512 散列、HMAC-SHA256 签名、PBKDF2 密钥派生、
 * 常量时间比较等安全能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class DigestUtils {

    /**
     * 私有构造器，工具类不允许实例化。
     */
    private DigestUtils() {
        throw new UnsupportedOperationException("DigestUtils is a utility class and cannot be instantiated");
    }

    /**
     * 使用共享的线程安全 SecureRandom 实例
     *
     * <p>SecureRandom 本身是线程安全的，无需 ThreadLocal 隔离。
     * 相比 ThreadLocal 方案，避免了线程池场景下的内存泄漏风险。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 流处理缓冲区大小（8KB）。
     */
    private static final int STREAM_BUFFER_SIZE = 8 * 1024;

    /**
     * 流处理缓冲区（ThreadLocal 复用，带复用计数自动重置）。
     *
     * <p>每次调用 {@link #digest(InputStream, String)} 时复用本线程的缓冲区，
     * 避免频繁分配 8KB 数组带来的 GC 压力。
     *
     * <p>注意：缓冲区仅在 digest 方法内部借用，方法返回前不被修改或清空。
     * 由于方法执行期间线程独占使用，不会出现并发安全问题。
     *
     * <p>复用次数超过 {@link #MAX_REUSE_COUNT} 后会自动重新分配，
     * 防止长期运行后缓冲区意外膨胀。
     */
    /** 每个摘要实例的最大 ThreadLocal 缓冲区复用次数（防止长期运行后缓冲区膨胀） */
    private static final int MAX_REUSE_COUNT = 1024;

    private static final ThreadLocal<BufferWithReuseCounter> BUFFER_HOLDER =
            ThreadLocal.withInitial(BufferWithReuseCounter::new);

    /**
     * PBKDF2 密钥派生算法
     */
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    /**
     * 生成随机的 byte[] 作为 salt 密钥（线程安全）
     *
     * @param numBytes 盐值字节数（≥ 1）
     * @return 随机生成的盐值字节数组
     */
    public static byte[] genSalt(int numBytes) {
        if (numBytes <= 0) {
            throw new IllegalArgumentException("numBytes argument must be a positive integer (1 or larger)");
        }
        byte[] bytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * 生成指定长度的 Hex 格式 salt
     *
     * @param numBytes 盐值字节数（≥ 1）
     * @return Hex 编码的盐值字符串
     */
    public static String genSaltHex(int numBytes) {
        return HexFormat.of().formatHex(genSalt(numBytes));
    }

    /**
     * 优化的散列方法（支持 salt 和多次迭代）
     *
     * @apiNote 本方法为自研迭代哈希，非标准 PBKDF2/bcrypt/scrypt，<b>不可用于密码存储</b>。密码存储请使用 {@link PwdUtils}。
     *
     * <p>每次迭代均混入 salt，确保 salt 对最终哈希值的充分影响。
     * 迭代公式：H_0 = H(salt || input)，H_i = H(salt || H_{i-1})
     *
     * @param input      待散列的数据
     * @param algorithm  散列算法（如 SHA-256）
     * @param salt       盐值（可为 null）
     * @param iterations 迭代次数（\u22651）
     * @return 散列结果
     * @throws IllegalArgumentException 当 iterations &lt; 1 时抛出
     */
    public static byte[] digest(byte[] input, String algorithm, byte[] salt, int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations 必须 >= 1");
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] currentHash;
            if (salt != null) {
                digest.update(salt);
                currentHash = digest.digest(input);
                for (int i = 1; i < iterations; i++) {
                    digest.update(salt);
                    digest.update(currentHash);
                    currentHash = digest.digest();
                }
            } else {
                currentHash = digest.digest(input);
                for (int i = 1; i < iterations; i++) {
                    digest.update(currentHash);
                    currentHash = digest.digest();
                }
            }
            return currentHash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm not available: " + algorithm, e);
        }
    }

    /**
     * 优化的流处理散列
     *
     * <p>使用 {@link ThreadLocal} 复用 8KB 缓冲区，避免每次调用分配新数组。
     * 缓冲区仅在方法执行期间借用，方法返回后自动归还至线程本地存储。
     *
     * @param input     输入流（方法内不关闭，由调用方管理）
     * @param algorithm 散列算法（如 SHA-256）
     * @return 散列结果
     * @throws IOException              读取输入流时发生 I/O 错误
     * @throws IllegalStateException    算法不可用时抛出
     */
    public static byte[] digest(InputStream input, String algorithm) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance(algorithm);
            BufferWithReuseCounter counter = BUFFER_HOLDER.get();
            byte[] buffer = counter.getBuffer();

            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            // 与 sha256Hex(InputStream) 保持一致：使用后立即清零缓冲区，避免同线程后续
            // 复用 ThreadLocal 缓冲区时残留上一文件尾部数据（即使仅内存残留也应消除）
            Arrays.fill(buffer, (byte) 0);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm not available: " + algorithm, e);
        }
    }

    /**
     * 计算 SHA-256 散列
  * @param input 输入
  * @return 处理后的结果
 */
    public static byte[] sha256(byte[] input) {
        return digest(input, "SHA-256", null, 1);
    }

    /**
     * 计算 SHA-256 散列（Hex 格式）
 */
    public static String sha256Hex(byte[] input) {
        return HexFormat.of().formatHex(sha256(input));
    }

    /**
     * 计算 SHA-256 散列（字符串）
 */
    public static String sha256Hex(String input) {
        if (input == null) {
            return null;
        }
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算流式 SHA-256 散列（Hex 格式）。
     *
     * <p>适用于大文件的流式 SHA-256 计算，复用 ThreadLocal 8KB 缓冲区。
     * 返回之前会主动清理缓冲区，不会泄露文件内容。
     *
     * @param input 输入流（方法内不关闭，由调用方管理）
     * @return 十六进制字符串
     * @throws IOException 读取输入流时发生 I/O 错误
     */
    public static String sha256Hex(InputStream input) throws IOException {
        byte[] hash = digest(input, "SHA-256");
        String hex = HexFormat.of().formatHex(hash);
        // 清空 ThreadLocal 缓冲区，避免文件数据残留
        BufferWithReuseCounter counter = BUFFER_HOLDER.get();
        byte[] buffer = counter.getBuffer();
        java.util.Arrays.fill(buffer, (byte) 0);
        return hex;
    }

    /**
     * 计算 SHA-512 散列
 */
    public static byte[] sha512(byte[] input) {
        return digest(input, "SHA-512", null, 1);
    }

    /**
     * 计算 SHA-512 散列（Hex 格式）
 */
    public static String sha512Hex(byte[] input) {
        return HexFormat.of().formatHex(sha512(input));
    }

    /**
     * 计算 SHA-512 散列（字符串）
 */
    public static String sha512Hex(String input) {
        if (input == null) {
            return null;
        }
        return sha512Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 HMAC-SHA256（带密钥的散列）
 */
    public static byte[] hmacSha256(byte[] input, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(input);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 algorithm not available", e);
        }
    }

    /**
     * 计算 HMAC-SHA256（Hex 格式）
 */
    public static String hmacSha256Hex(byte[] input, byte[] key) {
        return HexFormat.of().formatHex(hmacSha256(input, key));
    }

    /**
     * 计算 HMAC-SHA256（字符串）
 */
    public static String hmacSha256Hex(String input, String key) {
        if (input == null || key == null) {
            return null;
        }
        return hmacSha256Hex(
            input.getBytes(StandardCharsets.UTF_8),
            key.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * PBKDF2 密钥派生（推荐用于密码存储）
 */
    public static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 algorithm not available", e);
        }
    }

    /**
     * PBKDF2 密钥派生（Hex 格式）
 */
    public static String pbkdf2Hex(char[] password, byte[] salt, int iterations, int keyLength) {
        return HexFormat.of().formatHex(pbkdf2(password, salt, iterations, keyLength));
    }

    /**
     * 验证散列值是否匹配（时序恒定比较，防止时序攻击）
 */
    public static boolean verifyDigest(byte[] expected, byte[] actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * 验证 Hex 格式的散列值是否匹配
     */
    public static boolean verifyDigestHex(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        try {
            byte[] expected = HexFormat.of().parseHex(expectedHex);
            byte[] actual = HexFormat.of().parseHex(actualHex);
            return verifyDigest(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 常量时间字符串比较（防止计时攻击）
     *
     * <p>使用 {@link MessageDigest#isEqual(byte[], byte[])} 进行恒定时间比较，
     * 防止攻击者通过比较耗时推断字符串差异位置。
     *
     * @param a 字符串 a
     * @param b 字符串 b
     * @return 相等返回 true
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    /**
     * 计算 HMAC-SHA256 并返回 Base64 标准编码的签名
     *
     * @param data   原始数据
     * @param secret 密钥
     * @return Base64 标准编码的签名
     */
    public static String hmacSha256Base64(String data, String secret) {
        if (data == null || secret == null) {
            return null;
        }
        byte[] hmac = hmacSha256(
            data.getBytes(StandardCharsets.UTF_8),
            secret.getBytes(StandardCharsets.UTF_8)
        );
        return Base64.getEncoder().encodeToString(hmac);
    }

    /**
     * 计算 HMAC-SHA256 并返回 Base64 URL-safe 编码（无填充）的签名
     *
     * @param data 待签名数据
     * @param key  密钥字节数组
     * @return Base64 URL-safe 编码的签名
     */
    public static String hmacSha256UrlSafe(String data, byte[] key) {
        if (data == null || key == null) {
            return null;
        }
        byte[] hmac = hmacSha256(data.getBytes(StandardCharsets.UTF_8), key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
    }

    /**
     * 签名编码格式
     */
    public enum SignatureEncoding {
        /** Base64 编码 */
        BASE64,
        /** Hex 编码 */
        HEX
    }

    /**
     * 验证 HMAC-SHA256 签名（常量时间比较，防止时序攻击）
     *
     * @param data      原始数据
     * @param secret    密钥
     * @param signature 待验证的签名
     * @param encoding  签名编码格式
     * @return 验证通过返回 true
     */
    public static boolean verifySignature(String data, String secret, String signature, SignatureEncoding encoding) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String computed = encoding == SignatureEncoding.BASE64
                ? hmacSha256Base64(data, secret)
                : hmacSha256Hex(data, secret);
        return constantTimeEquals(computed, signature);
    }

    /**
     * 附带复用计数的缓冲区，超过复用次数后自动重新分配。
     */
    private static final class BufferWithReuseCounter {

        private byte[] buffer;
        private int reuseCount;

        BufferWithReuseCounter() {
            this.buffer = new byte[STREAM_BUFFER_SIZE];
            this.reuseCount = 0;
        }

        byte[] getBuffer() {
            if (reuseCount >= MAX_REUSE_COUNT) {
                buffer = new byte[STREAM_BUFFER_SIZE];
                reuseCount = 0;
            }
            reuseCount++;
            return buffer;
        }
    }
}


