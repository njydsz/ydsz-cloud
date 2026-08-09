package com.njydsz.common.util.password;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.njydsz.common.util.security.DigestUtils;

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
     *
     * <p>本枚举兼容 1.x {@code WEAK/MEDIUM/STRONG} 三档评分逻辑。
     * 2.x 新增 SPI 接口 {@link PasswordStrengthChecker} 提供更细粒度的
     * {@link PasswordStrengthChecker.PasswordStrengthLevel} 五档评分与国际化提示。
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

        byte[] salt = HexFormat.of().parseHex(saltHex);
        byte[] hash = DigestUtils.pbkdf2(password, salt, iterations, 256);
        return saltHex + ":" + iterations + ":" + HexFormat.of().formatHex(hash);
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
            // 防御恶意高迭代次数导致 CPU DoS（如 Integer.MAX_VALUE）。
            // 上限 10_000_000 远高于 OWASP 推荐 600000，兼顾合法旧数据与安全性。
            if (iterations < 1 || iterations > 10_000_000) {
                throw new IllegalArgumentException("iterations 超出允许范围 [1, 10000000]");
            }
            String expectedHash = parts[2];

            byte[] salt = HexFormat.of().parseHex(saltHex);
            byte[] actualHash = DigestUtils.pbkdf2(password.toCharArray(), salt, iterations, 256);
            String actualHashHex = HexFormat.of().formatHex(actualHash);

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
     * 密码强度检查器（ServiceLoader SPI 懒加载）。
     *
     * <p>通过 {@code META-INF/services/com.njydsz.common.util.password.PasswordStrengthChecker}
     * 注册的自定义实现，可被第三方覆盖以适配企业密码策略。
     */
    private static volatile PasswordStrengthChecker strengthChecker;

    /**
     * 获取密码强度检查器实例。
     *
     * <p>优先通过 {@link ServiceLoader} 发现自定义注册实现；
     * 若未注册则返回 {@link DefaultPasswordStrengthChecker} 单例。
     * 结果被缓存为 volatile 字段，ServiceLoader 开销仅首次加载发生。
     *
     * @return 密码强度检查器（不为 null）
     */
    public static PasswordStrengthChecker getPasswordStrengthChecker() {
        PasswordStrengthChecker checker = strengthChecker;
        if (checker == null) {
            synchronized (PwdUtils.class) {
                checker = strengthChecker;
                if (checker == null) {
                    ServiceLoader<PasswordStrengthChecker> loader =
                            ServiceLoader.load(PasswordStrengthChecker.class);
                    PasswordStrengthChecker found = null;
                    for (PasswordStrengthChecker impl : loader) {
                        found = impl;
                        break; // 取第一个注册实现
                    }
                    strengthChecker = checker = (found != null)
                            ? found
                            : DefaultPasswordStrengthChecker.INSTANCE;
                }
            }
        }
        return checker;
    }

    /**
     * 检查密码强度（兼容 1.x 三档枚举）。
     *
     * <p>内部委托给 SPI {@link #getPasswordStrengthChecker()} 获取评分结果，
     * 并映射到新五档 {@link PasswordStrengthChecker.PasswordStrengthLevel} 到旧三档 {@link PasswordStrength}：
     * <ul>
     *   <li>VERY_WEAK / WEAK → WEAK</li>
     *   <li>MEDIUM → MEDIUM</li>
     *   <li>STRONG / VERY_STRONG → STRONG</li>
     * </ul>
     *
     * @param password 密码
     * @return 密码强度枚举（WEAK/MEDIUM/STRONG），null/空串返回 WEAK
     */
    public static PasswordStrength checkPasswordStrength(String password) {
        PasswordStrengthChecker checker = getPasswordStrengthChecker();
        PasswordStrengthChecker.PasswordStrengthLevel level = checker.check(password);
        if (level == PasswordStrengthChecker.PasswordStrengthLevel.STRONG
                || level == PasswordStrengthChecker.PasswordStrengthLevel.VERY_STRONG) {
            return PasswordStrength.STRONG;
        } else if (level == PasswordStrengthChecker.PasswordStrengthLevel.MEDIUM) {
            return PasswordStrength.MEDIUM;
        }
        return PasswordStrength.WEAK;
    }

    /**
     * 检查密码强度（五档精细评分，返回新 API Level 枚举）。
     *
     * <p>2.x 新增方法，建议新代码调用本方法替代旧三档 {@link #checkPasswordStrength(String)}。
     * 内部委托给 SPI {@link #getPasswordStrengthChecker()}。
     *
     * @param password 密码（可为 null）
     * @return 密码强度级别；null 或空串返回 VERY_WEAK
     * @since 1.3.0
     */
    public static PasswordStrengthChecker.PasswordStrengthLevel checkPasswordStrengthLevel(String password) {
        return getPasswordStrengthChecker().check(password);
    }

    /**
     * 获取密码强度描述（国际化支持）。
     *
     * @param password 密码
     * @param locale   语言区域（{@link Locale#CHINESE} / {@link Locale#ENGLISH} 等）
     * @return 本地化描述字符串（弱/中等/强 等）
     * @since 1.3.0
     */
    public static String describePasswordStrength(String password, Locale locale) {
        PasswordStrengthChecker.PasswordStrengthLevel level = getPasswordStrengthChecker().check(password);
        return getPasswordStrengthChecker().describe(level, locale);
    }

    /**
     * 获取密码改进建议（国际化支持）。
     *
     * @param password 当前密码（可为 null）
     * @param locale   语言区域
     * @return 建议文本（可能为空；不会返回 null）
     * @since 1.3.0
     */
    public static String suggestPasswordImprovement(String password, Locale locale) {
        return getPasswordStrengthChecker().suggest(password, locale);
    }

}
