package com.njydsz.common.util.id;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 高性能随机字符串生成工具类
 * <p>
 * 参考 Apache Commons Lang3 RandomStringUtils 实现，
 * 优化性能并增加更多实用方法。
 * </p>
 * <p>
 * 特性：
 * 1. 基于 ThreadLocalRandom，无锁高性能
 * 2. 支持 SecureRandom 加密级别随机数
 * 3. 支持自定义字符集
 * 4. 支持长度范围随机
 * </p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public final class RandomUtils {

    private static final String ALL_CHAR = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LETTER_CHAR = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER_CHAR = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER_CHAR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String NUMBER_CHAR = "0123456789";

    private static final ThreadLocal<SecureRandom> SECURE_RANDOM = ThreadLocal.withInitial(SecureRandom::new);

    private RandomUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 获取定长的随机字符串 (包含字母和数字)
     *
     * @param length 字符串长度
     * @return 随机字符串
     */
    public static String generateString(int length) {
        return buildRandomString(length, ALL_CHAR);
    }

    /**
     * 获取定长的随机字母字符串 (包含大小写)
     *
     * @param length 字符串长度
     * @return 随机字母字符串
     */
    public static String generateMixString(int length) {
        return buildRandomString(length, LETTER_CHAR);
    }

    /**
     * 获取定长的随机小写字母字符串
     *
     * @param length 字符串长度
     * @return 随机小写字母字符串
     */
    public static String generateLowerString(int length) {
        return buildRandomString(length, LOWER_CHAR);
    }

    /**
     * 获取定长的随机大写字母字符串
     *
     * @param length 字符串长度
     * @return 随机大写字母字符串
     */
    public static String generateUpperString(int length) {
        return buildRandomString(length, UPPER_CHAR);
    }

    /**
     * 获取定长的随机数字字符串
     *
     * @param length 字符串长度
     * @return 随机数字字符串
     */
    public static String generateNumberString(int length) {
        return buildRandomString(length, NUMBER_CHAR);
    }

    /**
     * 生成随机十六进制字符串
     *
     * @param length 字符串长度（必须为偶数）
     * @return 十六进制字符串
     */
    public static String generateHexString(int length) {
        if (length <= 0) {
            return "";
        }
        byte[] bytes = new byte[(length + 1) / 2];
        ThreadLocalRandom.current().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.substring(0, length);
    }

    /**
     * 生成指定长度范围内的随机字符串
     *
     * @param minLength 最小长度（包含）
     * @param maxLength 最大长度（不包含）
     * @return 随机字符串
     */
    public static String generateStringInRange(int minLength, int maxLength) {
        if (minLength >= maxLength) {
            throw new IllegalArgumentException("minLength must be less than maxLength");
        }
        int length = randomInt(minLength, maxLength - 1);
        return generateString(length);
    }

    /**
     * 生成指定长度范围内的随机字母字符串
     *
     * @param minLength 最小长度（包含）
     * @param maxLength 最大长度（不包含）
     * @return 随机字母字符串
     */
    public static String generateMixStringInRange(int minLength, int maxLength) {
        if (minLength >= maxLength) {
            throw new IllegalArgumentException("minLength must be less than maxLength");
        }
        int length = randomInt(minLength, maxLength - 1);
        return generateMixString(length);
    }

    /**
     * 生成指定长度范围内的随机数字字符串
     *
     * @param minLength 最小长度（包含）
     * @param maxLength 最大长度（不包含）
     * @return 随机数字字符串
     */
    public static String generateNumberStringInRange(int minLength, int maxLength) {
        if (minLength >= maxLength) {
            throw new IllegalArgumentException("minLength must be less than maxLength");
        }
        int length = randomInt(minLength, maxLength - 1);
        return generateNumberString(length);
    }

    /**
     * 生成加密级别的随机字符串（适用于 token、密钥等安全场景）
     *
     * @param length 字符串长度
     * @return 加密级别随机字符串
     */
    public static String generateSecureString(int length) {
        if (length <= 0) {
            return "";
        }
        byte[] bytes = new byte[length];
        SECURE_RANDOM.get().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, Math.min(length, (length * 4 + 2) / 3));
    }

    /**
     * 生成自定义字符集的随机字符串
     *
     * @param length     字符串长度
     * @param charSource 字符集
     * @return 随机字符串
     */
    public static String generateCustomString(int length, String charSource) {
        if (length <= 0 || charSource == null || charSource.isEmpty()) {
            return "";
        }
        return buildRandomString(length, charSource);
    }

    /**
     * 生成不包含歧义字符的随机字符串（去除 0, O, 1, I, l 等）
     *
     * @param length 字符串长度
     * @return 随机字符串
     */
    public static String generateUnambiguousString(int length) {
        String unambiguousChars = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
        return buildRandomString(length, unambiguousChars);
    }

    /**
     * 生成随机布尔值
     *
     * @return 随机布尔值
     */
    public static boolean randomBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    /**
     * 生成指定概率的随机布尔值
     *
     * @param probability 为 true 的概率（0.0-1.0）
     * @return 随机布尔值
     */
    public static boolean randomBoolean(double probability) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("Probability must be between 0.0 and 1.0");
        }
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    /**
     * 产生 [min, max] 区间的随机整数
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机整数
     */
    public static int randomInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be less than or equal to max");
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * 产生 [0, max] 区间的随机整数
     *
     * @param max 最大值（包含）
     * @return 随机整数
     */
    public static int randomInt(int max) {
        return randomInt(0, max);
    }

    /**
     * 产生 [min, max] 区间的随机长整数
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机长整数
     */
    public static long randomLong(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be less than or equal to max");
        }
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    /**
     * 产生 [0, max] 区间的随机长整数
     *
     * @param max 最大值（包含）
     * @return 随机长整数
     */
    public static long randomLong(long max) {
        return randomLong(0, max);
    }

    /**
     * 产生 [min, max] 区间的随机浮点数
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机浮点数
     */
    public static double randomDouble(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be less than or equal to max");
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    /**
     * 产生 [0, max] 区间的随机浮点数
     *
     * @param max 最大值（包含）
     * @return 随机浮点数
     */
    public static double randomDouble(double max) {
        return randomDouble(0.0, max);
    }

    /**
     * 从数组中随机选择一个元素
     *
     * @param array 数组
     * @param <T>   元素类型
     * @return 随机元素
     */
    public static <T> T randomElement(T[] array) {
        if (array == null || array.length == 0) {
            return null;
        }
        return array[randomInt(array.length - 1)];
    }

    /**
     * 随机打乱数组（Fisher-Yates 洗牌算法）
     *
     * @param array 要打乱的数组
     * @param <T>   元素类型
     */
    public static <T> void shuffle(T[] array) {
        if (array == null || array.length <= 1) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = array.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    /**
     * 构建随机字符串核心逻辑
     *
     * @param length     字符串长度
     * @param charSource 字符集
     * @return 随机字符串
     */
    private static String buildRandomString(int length, String charSource) {
        if (length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int charLen = charSource.length();
        for (int i = 0; i < length; i++) {
            sb.append(charSource.charAt(random.nextInt(charLen)));
        }
        return sb.toString();
    }
}
