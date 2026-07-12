package com.njydsz.pmis.common.safe.sensitive;

/**
 * 敏感数据脱敏工具类
 *
 * <p>提供各种敏感数据类型的脱敏处理方法。
 *
 * <p><b>支持的脱敏类型：</b>
 * <ul>
 *   <li>中文姓名、身份证、手机号、邮箱</li>
 *   <li>银行卡号、固定电话、密码</li>
 *   <li>地址、护照、军官证等</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see SensitiveType
 */
public final class SensitiveUtil {

    private static final char ASTERISK = '*';

    private SensitiveUtil() {
    }

    /**
     * 根据脱敏类型对数据进行脱敏
     *
     * @param value     原数据
     * @param type      脱敏类型
     * @param replaceChar 替换字符
     * @return 脱敏后的数据
     */
    public static String desensitize(String value, SensitiveType type, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (type == null) {
            return value;
        }
        return switch (type) {
            case DEFAULT -> defaultDesensitize(value, replaceChar);
            case CHINESE_NAME -> chineseName(value, replaceChar);
            case ID_CARD -> idCard(value, replaceChar);
            case PHONE -> phone(value, replaceChar);
            case EMAIL -> email(value, replaceChar);
            case BANK_CARD -> bankCard(value, replaceChar);
            case ADDRESS -> address(value, replaceChar);
            case PASSWORD -> password(value);
            case FIXED_PHONE -> fixedPhone(value, replaceChar);
            case CVV -> cvv(value);
            case MILITARY_ID -> militaryId(value, replaceChar);
            case PASSPORT -> passport(value, replaceChar);
            case BUSINESS_LICENSE -> businessLicense(value, replaceChar);
            case CAR_LICENSE -> carLicense(value, replaceChar);
            case SOCIAL_SECURITY -> socialSecurity(value, replaceChar);
            case BIRTH_DATE -> birthDate(value);
            case NAME -> name(value, replaceChar);
            case CUSTOM -> custom(value, replaceChar);
        };
    }

    /**
     * 根据脱敏类型对数据进行脱敏（使用默认替换字符 *）
     *
     * @param value 原数据
     * @param type  脱敏类型
     * @return 脱敏后的数据
     */
    public static String desensitize(String value, SensitiveType type) {
        return desensitize(value, type, ASTERISK);
    }

    /**
     * 默认脱敏
     *
     * <p>脱敏规则：保留前2后2位，中间用替换字符替换
     *
     * @param value 原数据
     * @param replaceChar 替换字符
     * @return 脱敏后的数据
     */
    public static String defaultDesensitize(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 4) {
            return repeat(replaceChar, length);
        }
        String prefix = value.substring(0, 2);
        String suffix = value.substring(length - 2);
        return prefix + repeat(replaceChar, length - 4) + suffix;
    }

    /**
     * 中文姓名脱敏
     *
     * <p>脱敏规则：保留姓氏，隐藏名字
     *
     * @param value 原姓名
     * @param replaceChar 替换字符
     * @return 脱敏后的姓名
     */
    public static String chineseName(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length == 1) {
            return String.valueOf(replaceChar);
        }
        if (length == 2) {
            return value.charAt(0) + String.valueOf(replaceChar);
        }
        return value.charAt(0) + String.valueOf(replaceChar) + value.substring(2);
    }

    /**
     * 身份证号脱敏
     *
     * <p>脱敏规则：中间8位脱敏
     *
     * @param value 原身份证号
     * @param replaceChar 替换字符
     * @return 脱敏后的身份证号
     */
    public static String idCard(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 8) {
            return repeat(replaceChar, length);
        }
        String prefix = value.substring(0, 3);
        String suffix = value.substring(11);
        return prefix + repeat(replaceChar, 8) + suffix;
    }

    /**
     * 手机号脱敏
     *
     * <p>脱敏规则：显示前3位和后4位，中间隐藏
     *
     * @param value 原手机号
     * @param replaceChar 替换字符
     * @return 脱敏后的手机号
     */
    public static String phone(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 7) {
            return repeat(replaceChar, length);
        }
        String prefix = value.substring(0, 3);
        String suffix = value.substring(length - 4);
        return prefix + repeat(replaceChar, length - 7) + suffix;
    }

    /**
     * 电子邮箱脱敏
     *
     * <p>脱敏规则：保留首尾字符，中间隐藏
     *
     * @param value 原邮箱
     * @param replaceChar 替换字符
     * @return 脱敏后的邮箱
     */
    public static String email(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int atIndex = value.indexOf('@');
        if (atIndex <= 1) {
            return repeat(replaceChar, value.length());
        }
        String username = value.substring(0, atIndex);
        String domain = value.substring(atIndex);

        int usernameLength = username.length();
        if (usernameLength == 2) {
            return username.charAt(0) + String.valueOf(replaceChar) + domain;
        }
        if (usernameLength == 3) {
            return username.charAt(0) + String.valueOf(replaceChar) + username.charAt(2) + domain;
        }
        return username.charAt(0) + repeat(replaceChar, 2) + username.charAt(usernameLength - 1) + domain;
    }

    /**
     * 银行卡号脱敏
     *
     * <p>脱敏规则：后4位保留，其余脱敏
     *
     * @param value 原银行卡号
     * @param replaceChar 替换字符
     * @return 脱敏后的银行卡号
     */
    public static String bankCard(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 4) {
            return repeat(replaceChar, length);
        }
        String suffix = value.substring(length - 4);
        return repeat(replaceChar, length - 4) + suffix;
    }

    /**
     * 家庭住址脱敏
     *
     * <p>脱敏规则：保留省市区，隐藏详细地址
     *
     * @param value 原地址
     * @param replaceChar 替换字符
     * @return 脱敏后的地址
     */
    public static String address(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 4) {
            return repeat(replaceChar, length);
        }
        return value.substring(0, 4) + repeat(replaceChar, Math.min(6, length - 4));
    }

    /**
     * 密码脱敏
     *
     * <p>脱敏规则：不返回实际密码
     *
     * @param value 原密码
     * @return 脱敏后的密码
     */
    public static String password(String value) {
        return "******";
    }

    /**
     * 固定电话脱敏
     *
     * <p>脱敏规则：显示区号和后4位，中间隐藏
     *
     * @param value 原固定电话
     * @param replaceChar 替换字符
     * @return 脱敏后的固定电话
     */
    public static String fixedPhone(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 4) {
            return repeat(replaceChar, length);
        }
        int dashIndex = value.lastIndexOf('-');
        if (dashIndex > 0) {
            String areaCode = value.substring(0, dashIndex);
            String number = value.substring(dashIndex + 1);
            if (number.length() <= 4) {
                return areaCode + "-" + repeat(replaceChar, number.length());
            }
            return areaCode + "-" + repeat(replaceChar, number.length() - 4) + number.substring(number.length() - 4);
        }
        return phone(value, replaceChar);
    }

    /**
     * CVV 脱敏
     *
     * <p>脱敏规则：不返回
     *
     * @param value 原 CVV
     * @return 脱敏后的 CVV
     */
    public static String cvv(String value) {
        return "***";
    }

    /**
     * 军官证脱敏
     *
     * @param value 原军官证号
     * @param replaceChar 替换字符
     * @return 脱敏后的军官证号
     */
    public static String militaryId(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 4) {
            return repeat(replaceChar, length);
        }
        return value.substring(0, 2) + repeat(replaceChar, length - 4) + value.substring(length - 2);
    }

    /**
     * 护照脱敏
     *
     * @param value 原护照号
     * @param replaceChar 替换字符
     * @return 脱敏后的护照号
     */
    public static String passport(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 4) {
            return repeat(replaceChar, length);
        }
        return value.substring(0, 2) + repeat(replaceChar, length - 4) + value.substring(length - 2);
    }

    /**
     * 营业执照注册号脱敏
     *
     * @param value 原注册号
     * @param replaceChar 替换字符
     * @return 脱敏后的注册号
     */
    public static String businessLicense(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 6) {
            return repeat(replaceChar, length);
        }
        return value.substring(0, 3) + repeat(replaceChar, length - 6) + value.substring(length - 3);
    }

    /**
     * 车牌号脱敏
     *
     * <p>脱敏规则：保留首字符和最后一位
     *
     * @param value 原车牌号
     * @param replaceChar 替换字符
     * @return 脱敏后的车牌号
     */
    public static String carLicense(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 2) {
            return repeat(replaceChar, length);
        }
        return value.charAt(0) + repeat(replaceChar, length - 2) + value.charAt(length - 1);
    }

    /**
     * 社保卡号脱敏
     *
     * @param value 原社保卡号
     * @param replaceChar 替换字符
     * @return 脱敏后的社保卡号
     */
    public static String socialSecurity(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length <= 6) {
            return repeat(replaceChar, length);
        }
        return value.substring(0, 3) + repeat(replaceChar, length - 6) + value.substring(length - 3);
    }

    /**
     * 出生日期脱敏
     *
     * <p>脱敏规则：只显示年月
     *
     * @param value 原出生日期
     * @return 脱敏后的出生日期
     */
    public static String birthDate(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() >= 6) {
            return value.substring(0, 4) + "-**-**";
        }
        return repeat(ASTERISK, value.length());
    }

    /**
     * 姓名脱敏
     *
     * <p>脱敏规则：仅保留首字
     *
     * @param value 原姓名
     * @param replaceChar 替换字符
     * @return 脱敏后的姓名
     */
    public static String name(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (length == 1) {
            return value;
        }
        if (length == 2) {
            return value.charAt(0) + String.valueOf(replaceChar);
        }
        if (length == 3) {
            return value.charAt(0) + String.valueOf(replaceChar).repeat(2);
        }
        return value.charAt(0) + String.valueOf(replaceChar).repeat(length - 2) + value.charAt(length - 1);
    }

    /**
     * 自定义脱敏
     *
     * <p>脱敏规则：默认将字符串全部替换为指定字符
     *
     * @param value 原始数据
     * @param replaceChar 替换字符
     * @return 脱敏后的数据
     */
    public static String custom(String value, char replaceChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return repeat(replaceChar, value.length());
    }

    private static String repeat(char c, int count) {
        return String.valueOf(c).repeat(count);
    }
}
