package com.njydsz.common.util.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.njydsz.common.util.bytes.HexUtils;

/**
 * 用户密码安全工具类（纯 JDK 实现 + Spring Security BCrypt）
 *
 * <p>支持多种密码加密方式：BCrypt（推荐）、PBKDF2（推荐）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class PwdUtils {

    /** BCrypt 格式正则 */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    /** Spring Security BCrypt 编码器（线程安全）
     *
     * <p>强度 12 对应 2^12 = 4096 轮哈希计算，OWASP 推荐至少 10。
     * 如需调整强度，请通过配置注入新的 BCryptPasswordEncoder 实例。
     */
    private static final BCryptPasswordEncoder BCRYPT_ENCODER = new BCryptPasswordEncoder(12);

    /**
     * 密码强度枚举
     */
    public enum PasswordStrength {
        /** 弱密码 */
        WEAK,
        /** 中等密码 */
        MEDIUM,
        /** 强密码 */
        STRONG
    }

    /**
     * 私有构造器，工具类不允许实例化。
     */
    private PwdUtils() {
        throw new UnsupportedOperationException("PwdUtils is a utility class and cannot be instantiated");
    }

    /**
     * 默认密码（从系统属性或环境变量读取，禁止硬编码）
     *
     * <p>读取优先级：系统属性 {@code ydsz.default.password} > 环境变量 {@code YDSZ_DEFAULT_PASSWORD}
     * <p>若均未配置，使用随机生成的强密码（每次启动不同，需通过日志获取）
     */
    private static final String DEFAULT_PASS = resolveDefaultPassword();

    private static String resolveDefaultPassword() {
        String password = System.getProperty("ydsz.default.password");
        if (password != null && !password.isEmpty()) {
            return password;
        }
        password = System.getenv("YDSZ_DEFAULT_PASSWORD");
        if (password != null && !password.isEmpty()) {
            return password;
        }
        byte[] randomBytes = new byte[16];
        new SecureRandom().nextBytes(randomBytes);
        String generated = HexUtils.bytesToHex(randomBytes);
        System.getLogger(PwdUtils.class.getName()).log(System.Logger.Level.WARNING,
                "未配置默认密码(ydsz.default.password/YDSZ_DEFAULT_PASSWORD)，已随机生成");
        return generated;
    }

    /**
     * PBKDF2 默认迭代次数
     *
     * <p>OWASP 2023 推荐 PBKDF2-SHA256 至少 600000 次迭代。
     * 迭代次数存储在编码密码中（salt:iterations:hash），
     * 验证旧密码时使用存储的迭代次数，不受此值变化影响。
     */
    private static final int DEFAULT_ITERATIONS = 600000;

    /**
     * 默认盐值长度（16 字节）
     */
    private static final int DEFAULT_SALT_LENGTH = 16;

    /**
     * 使用 BCrypt 哈希密码
     *
     * @param rawPassword 原始密码
     * @return BCrypt 哈希值
     */
    public static String hashPasswordBCrypt(String rawPassword) {
        return BCRYPT_ENCODER.encode(rawPassword);
    }

    /**
     * 验证 BCrypt 密码
     *
     * @param rawPassword 原始密码
     * @param hashedPassword BCrypt 哈希值
     * @return 匹配返回 true
     */
    public static boolean verifyPasswordBCrypt(String rawPassword, String hashedPassword) {
        return BCRYPT_ENCODER.matches(rawPassword, hashedPassword);
    }

    /**
     * 判断密码是否为 BCrypt 格式
     *
     * @param password 密码字符串
     * @return 是 BCrypt 格式返回 true
     */
    public static boolean isBCryptFormat(String password) {
        return password != null && BCRYPT_PATTERN.matcher(password).matches();
    }

    /**
     * 使用 PBKDF2 加密密码（推荐用于生产环境）
     * @param password 密码字符数组
     * @param saltHex 十六进制盐值
     * @return 加密结果（salt:iterations:hash 格式）
     */
    public static String encodePBKDF2(char[] password, String saltHex) {
        return encodePBKDF2(password, saltHex, DEFAULT_ITERATIONS);
    }

    /**
     * 使用 PBKDF2 加密密码（可指定迭代次数）
     * @param password 密码字符数组
     * @param saltHex 十六进制盐值
     * @param iterations 迭代次数
     * @return 加密结果（salt:iterations:hash 格式）
     */
    public static String encodePBKDF2(char[] password, String saltHex, int iterations) {
        if (saltHex == null || saltHex.isEmpty()) {
            throw new IllegalArgumentException("Salt must not be empty");
        }
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        
        byte[] salt = HexUtils.hexToBytes(saltHex);
        byte[] hash = DigestUtils.pbkdf2(password, salt, iterations, 256);
        return saltHex + ":" + iterations + ":" + HexUtils.bytesToHex(hash);
    }

    /**
     * 使用 PBKDF2 加密密码（自动生成盐值）
     * @param password 密码字符数组
     * @return 加密结果（salt:iterations:hash 格式）
     */
    public static String encodePBKDF2WithAutoSalt(char[] password) {
        return encodePBKDF2WithAutoSalt(password, DEFAULT_ITERATIONS);
    }

    /**
     * 使用 PBKDF2 加密密码（自动生成盐值和指定迭代次数）
     * @param password 密码字符数组
     * @param iterations 迭代次数
     * @return 加密结果（salt:iterations:hash 格式）
     */
    public static String encodePBKDF2WithAutoSalt(char[] password, int iterations) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        
        String saltHex = DigestUtils.genSaltHex(DEFAULT_SALT_LENGTH);
        return encodePBKDF2(password, saltHex, iterations);
    }

    /**
     * 验证 PBKDF2 加密的密码
     * @param password 明文密码
     * @param encodedPassword 加密后的密码（格式：salt:iterations:hash）
     * @return 是否匹配
     */
    public static boolean verifyPBKDF2(String password, String encodedPassword) {
        if (password == null || password.isEmpty() || encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        
        String[] parts = encodedPassword.split(":");
        if (parts.length != 3) {
            return false;
        }
        
        try {
            String saltHex = parts[0];
            int iterations = Integer.parseInt(parts[1]);
            String expectedHash = parts[2];
            
            byte[] salt = HexUtils.hexToBytes(saltHex);
            byte[] actualHash = DigestUtils.pbkdf2(password.toCharArray(), salt, iterations, 256);
            String actualHashHex = HexUtils.bytesToHex(actualHash);
            
            return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                actualHashHex.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成随机盐值
     * @param length 盐值长度（字节）
     * @return 十六进制盐值字符串
     */
    public static String generateSalt(int length) {
        return DigestUtils.genSaltHex(length);
    }

    /**
     * 生成默认长度的随机盐值
     * @return 十六进制盐值字符串
     */
    public static String generateSalt() {
        return generateSalt(DEFAULT_SALT_LENGTH);
    }

    /**
     * 检查密码强度
     * @param password 密码
     * @return 密码强度枚举（WEAK/MEDIUM/STRONG）
     */
    public static PasswordStrength checkPasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return PasswordStrength.WEAK;
        }
        
        int length = password.length();
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        
        int score = 0;
        if (length >= 8) score++;
        if (length >= 12) score++;
        if (length >= 16) score++;
        if (hasLower && hasUpper) score++;
        if (hasDigit) score++;
        if (hasSpecial) score++;
        
        if (score >= 5) {
            return PasswordStrength.STRONG;
        } else if (score >= 3) {
            return PasswordStrength.MEDIUM;
        } else {
            return PasswordStrength.WEAK;
        }
    }
}
