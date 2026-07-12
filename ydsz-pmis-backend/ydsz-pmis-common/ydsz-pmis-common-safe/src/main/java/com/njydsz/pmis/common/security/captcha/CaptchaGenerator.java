package com.njydsz.pmis.common.security.captcha;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码生成与校验组件。
 * <p>
 * 对标 remi-comm CaptchaService，支持：
 * <ul>
 *   <li>数字+字母混合验证码</li>
 *   <li>纯数字验证码（适用于短信验证码）</li>
 *   <li>运算验证码（a + b = ?）</li>
 * </ul>
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public class CaptchaGenerator {

    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789".toCharArray();

    /**
     * 生成混合验证码（数字+字母）。
     *
     * @param length 验证码长度
     * @return 验证码字符串
     */
    public static String generateMixed(int length) {
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(CHARS[random.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * 生成纯数字验证码。
     *
     * @param length 验证码长度
     * @return 数字验证码字符串
     */
    public static String generateNumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 生成运算验证码。
     *
     * @return 运算验证码 [表达式, 结果]
     */
    public static String[] generateArithmetic() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int a = random.nextInt(1, 20);
        int b = random.nextInt(1, 20);
        int op = random.nextInt(3);
        String expression;
        int result;
        switch (op) {
            case 0 -> {
                expression = a + " + " + b;
                result = a + b;
            }
            case 1 -> {
                if (a < b) {
                    int temp = a;
                    a = b;
                    b = temp;
                }
                expression = a + " - " + b;
                result = a - b;
            }
            default -> {
                if (a > 10) a = a / 2;
                if (b > 10) b = b / 2;
                expression = a + " × " + b;
                result = a * b;
            }
        }
        return new String[]{expression + " = ?", String.valueOf(result)};
    }

    /**
     * 校验验证码（忽略大小写）。
     *
     * @param input     用户输入
     * @param expected  期望值
     * @return true 如果匹配
     */
    public static boolean verify(String input, String expected) {
        if (input == null || expected == null) {
            return false;
        }
        return input.equalsIgnoreCase(expected);
    }
}
