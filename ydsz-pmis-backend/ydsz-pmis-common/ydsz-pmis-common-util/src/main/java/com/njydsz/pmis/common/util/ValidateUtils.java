package com.njydsz.pmis.common.util;

import java.util.regex.Pattern;

/**
 * 校验工具类
 *
 * <p>提供常用数据格式校验方法（邮箱、手机号、身份证等）。
 * 对标 remi-comm ValidateUtils。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class ValidateUtils {

    private ValidateUtils() {
    }

    /** 邮箱正则 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    /** 中国手机号正则 */
    private static final Pattern MOBILE_PATTERN =
            Pattern.compile("^1[3-9]\\d{9}$");

    /** 中国身份证号正则（18位） */
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("^\\d{17}[\\dXx]$");

    /** 统一社会信用代码正则 */
    private static final Pattern CREDIT_CODE_PATTERN =
            Pattern.compile("^[0-9A-HJ-NPQRTUWXY]{18}$");

    /** URL 正则 */
    private static final Pattern URL_PATTERN =
            Pattern.compile("^https?://[\\w-]+(\\.[\\w-]+)+([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?$");

    /** IP 地址正则 */
    private static final Pattern IP_PATTERN =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    /** 邮编正则 */
    private static final Pattern ZIP_CODE_PATTERN =
            Pattern.compile("^\\d{6}$");

    /**
     * 校验邮箱格式
     *
     * @param email 邮箱
     * @return true 如果格式正确
     */
    public static boolean isEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 校验手机号格式（中国大陆）
     *
     * @param mobile 手机号
     * @return true 如果格式正确
     */
    public static boolean isMobile(String mobile) {
        return mobile != null && MOBILE_PATTERN.matcher(mobile).matches();
    }

    /**
     * 校验身份证号格式（18位）
     *
     * @param idCard 身份证号
     * @return true 如果格式正确
     */
    public static boolean isIdCard(String idCard) {
        return idCard != null && ID_CARD_PATTERN.matcher(idCard).matches();
    }

    /**
     * 校验统一社会信用代码
     *
     * @param creditCode 统一社会信用代码
     * @return true 如果格式正确
     */
    public static boolean isCreditCode(String creditCode) {
        return creditCode != null && CREDIT_CODE_PATTERN.matcher(creditCode).matches();
    }

    /**
     * 校验 URL 格式
     *
     * @param url URL
     * @return true 如果格式正确
     */
    public static boolean isUrl(String url) {
        return url != null && URL_PATTERN.matcher(url).matches();
    }

    /**
     * 校验 IP 地址格式
     *
     * @param ip IP 地址
     * @return true 如果格式正确
     */
    public static boolean isIp(String ip) {
        return ip != null && IP_PATTERN.matcher(ip).matches();
    }

    /**
     * 校验邮编格式
     *
     * @param zipCode 邮编
     * @return true 如果格式正确
     */
    public static boolean isZipCode(String zipCode) {
        return zipCode != null && ZIP_CODE_PATTERN.matcher(zipCode).matches();
    }

    /**
     * 校验字符串是否为纯数字
     *
     * @param str 字符串
     * @return true 如果是纯数字
     */
    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验字符串是否为纯字母
     *
     * @param str 字符串
     * @return true 如果是纯字母
     */
    public static boolean isAlpha(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (!Character.isLetter(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验字符串是否为字母+数字组合
     *
     * @param str 字符串
     * @return true 如果是字母+数字组合
     */
    public static boolean isAlphanumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : str.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                return false;
            }
        }
        return hasLetter && hasDigit;
    }
}
