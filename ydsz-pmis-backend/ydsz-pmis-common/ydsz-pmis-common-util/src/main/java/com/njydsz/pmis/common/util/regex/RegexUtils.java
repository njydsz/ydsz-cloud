package com.njydsz.pmis.common.util.regex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RegexUtils 正则表达式工具类 - 高性能预编译版
 * 参考阿里巴巴、Apache Commons、Hutool 等大厂工具类实现
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class RegexUtils {

    private static final String EMPTY = "";

    private RegexUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== 常用正则模式常量 ====================

    /**
     * 手机号（简单）
     */
    public static final String MOBILE_SIMPLE = "^1\\d{10}$";
    private static final Pattern P_MOBILE_SIMPLE = Pattern.compile(MOBILE_SIMPLE);

    /**
     * 手机号（精确）
     */
    public static final String MOBILE_EXACT = "^1(3\\d|4[5-9]|5[0-35-9]|6[2567]|7[0-8]|8\\d|9[0-35-9])\\d{8}$";
    private static final Pattern P_MOBILE_EXACT = Pattern.compile(MOBILE_EXACT);

    /**
     * 固定电话
     */
    public static final String TELEPHONE = "^(0\\d{2,3}-)?\\d{7,8}(-\\d{1,7})?$";
    private static final Pattern P_TELEPHONE = Pattern.compile(TELEPHONE);

    /**
     * 邮箱
     */
    public static final String EMAIL = "^\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$";
    private static final Pattern P_EMAIL = Pattern.compile(EMAIL);

    /**
     * 身份证 (18 位)
     */
    public static final String ID_CARD = "^[1-9]\\d{5}[1-9]\\d{3}((0\\d)|(1[0-2]))(([0|1|2]\\d)|3[0-1])\\d{3}([0-9Xx])$";
    private static final Pattern P_ID_CARD = Pattern.compile(ID_CARD);

    /**
     * 身份证（15 位或 18 位）
     */
    public static final String ID_CARD_ALL = "^[1-9]\\d{7}((0\\d)|(1[0-2]))(([0|1|2]\\d)|3[0-1])\\d{3}$|^[1-9]\\d{5}[1-9]\\d{3}((0\\d)|(1[0-2]))(([0|1|2]\\d)|3[0-1])\\d{3}([0-9Xx])$";
    private static final Pattern P_ID_CARD_ALL = Pattern.compile(ID_CARD_ALL);

    /**
     * URL
     */
    public static final String URL = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$";
    private static final Pattern P_URL = Pattern.compile(URL);

    /**
     * IP 地址（IPv4）
     */
    public static final String IP = "^((2[0-4]\\d|25[0-5]|[01]?\\d\\d?)\\.){3}(2[0-4]\\d|25[0-5]|[01]?\\d\\d?)$";
    private static final Pattern P_IP = Pattern.compile(IP);

    /**
     * IP 地址（简单版）
     */
    public static final String IP_SIMPLE = "^(\\d{1,3}\\.){3}\\d{1,3}$";
    private static final Pattern P_IP_SIMPLE = Pattern.compile(IP_SIMPLE);

    /**
     * 日期（YYYY-MM-DD）
     */
    public static final String DATE = "^\\d{4}-\\d{2}-\\d{2}$";
    private static final Pattern P_DATE = Pattern.compile(DATE);

    /**
     * 日期时间（YYYY-MM-DD HH:MM:SS）
     */
    public static final String DATETIME = "^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}$";
    private static final Pattern P_DATETIME = Pattern.compile(DATETIME);

    /**
     * 时间（HH:MM:SS）
     */
    public static final String TIME = "^\\d{2}:\\d{2}:\\d{2}$";
    private static final Pattern P_TIME = Pattern.compile(TIME);

    /**
     * 邮政编码
     */
    public static final String POSTAL_CODE = "^[1-9]\\d{5}$";
    private static final Pattern P_POSTAL_CODE = Pattern.compile(POSTAL_CODE);

    /**
     * 车牌号（普通）
     */
    public static final String LICENSE_PLATE = "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][A-Z0-9]{5}$";
    private static final Pattern P_LICENSE_PLATE = Pattern.compile(LICENSE_PLATE);

    /**
     * 车牌号（新能源）
     */
    public static final String LICENSE_PLATE_NEW_ENERGY = "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][DF][A-Z0-9]{5}$";
    private static final Pattern P_LICENSE_PLATE_NEW_ENERGY = Pattern.compile(LICENSE_PLATE_NEW_ENERGY);

    /**
     * 统一社会信用代码
     */
    public static final String CREDIT_CODE = "^[0-9A-HJ-NPQRTUWXY]{2}\\d{6}[0-9A-HJ-NPQRTUWXY]{10}$";
    private static final Pattern P_CREDIT_CODE = Pattern.compile(CREDIT_CODE);

    /**
     * 银行卡号（简单验证）
     */
    public static final String BANK_CARD = "^\\d{16,19}$";
    private static final Pattern P_BANK_CARD = Pattern.compile(BANK_CARD);

    /**
     * 密码强度（至少包含数字和字母，长度 6-20）
     */
    public static final String PASSWORD = "^(?=.*[0-9])(?=.*[a-zA-Z]).{6,20}$";
    private static final Pattern P_PASSWORD = Pattern.compile(PASSWORD);

    /**
     * 用户名（字母开头，允许字母数字下划线，长度 4-16）
     */
    public static final String USERNAME = "^[a-zA-Z][a-zA-Z0-9_]{3,15}$";
    private static final Pattern P_USERNAME = Pattern.compile(USERNAME);

    /**
     * 中文
     */
    public static final String CHINESE = "^[\\u4e00-\\u9fa5]+$";
    private static final Pattern P_CHINESE = Pattern.compile(CHINESE);

    /**
     * 中文姓名
     */
    public static final String CHINESE_NAME = "^[\\u4e00-\\u9fa5]{2,6}$";
    private static final Pattern P_CHINESE_NAME = Pattern.compile(CHINESE_NAME);

    /**
     * 整数
     */
    public static final String INTEGER = "^-?\\d+$";
    private static final Pattern P_INTEGER = Pattern.compile(INTEGER);

    /**
     * 正整数
     */
    public static final String POSITIVE_INTEGER = "^\\d+$";
    private static final Pattern P_POSITIVE_INTEGER = Pattern.compile(POSITIVE_INTEGER);

    /**
     * 负整数
     */
    public static final String NEGATIVE_INTEGER = "^-\\d+$";
    private static final Pattern P_NEGATIVE_INTEGER = Pattern.compile(NEGATIVE_INTEGER);

    /**
     * 浮点数
     */
    public static final String DECIMAL = "^-?\\d+(\\.\\d+)?$";
    private static final Pattern P_DECIMAL = Pattern.compile(DECIMAL);

    /**
     * 正浮点数
     */
    public static final String POSITIVE_DECIMAL = "^\\d+(\\.\\d+)?$";
    private static final Pattern P_POSITIVE_DECIMAL = Pattern.compile(POSITIVE_DECIMAL);

    /**
     * 负浮点数
     */
    public static final String NEGATIVE_DECIMAL = "^-\\d+(\\.\\d+)?$";
    private static final Pattern P_NEGATIVE_DECIMAL = Pattern.compile(NEGATIVE_DECIMAL);

    /**
     * 数字（整数或浮点数，支持科学计数法）
     */
    public static final String NUMBER = "^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$";
    private static final Pattern P_NUMBER = Pattern.compile(NUMBER);

    /**
     * MAC 地址
     */
    public static final String MAC = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
    private static final Pattern P_MAC = Pattern.compile(MAC);

    /**
     * HTML 标签
     */
    public static final String HTML_TAG = "<[^>]+>";
    private static final Pattern P_HTML_TAG = Pattern.compile(HTML_TAG);

    /**
     * 空白行
     */
    public static final String BLANK_LINE = "\\n\\s*\\r";
    private static final Pattern P_BLANK_LINE = Pattern.compile(BLANK_LINE);

    /**
     * 数字提取
     */
    public static final String EXTRACT_NUMBER = "\\d+";
    private static final Pattern P_EXTRACT_NUMBER = Pattern.compile(EXTRACT_NUMBER);

    /**
     * 字母提取
     */
    public static final String EXTRACT_LETTER = "[a-zA-Z]+";
    private static final Pattern P_EXTRACT_LETTER = Pattern.compile(EXTRACT_LETTER);

    /**
     * 中文提取
     */
    public static final String EXTRACT_CHINESE = "[\\u4e00-\\u9fa5]+";
    private static final Pattern P_EXTRACT_CHINESE = Pattern.compile(EXTRACT_CHINESE);

    // ==================== 基础验证方法 ====================

    /**
     * 验证手机号（简单）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isMobile(String input) {
        return input != null && P_MOBILE_SIMPLE.matcher(input).matches();
    }

    /**
     * 验证手机号（精确）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isMobileExact(String input) {
        return input != null && P_MOBILE_EXACT.matcher(input).matches();
    }

    /**
     * 验证固定电话
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isTelephone(String input) {
        return input != null && P_TELEPHONE.matcher(input).matches();
    }

    /**
     * 验证邮箱
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isEmail(String input) {
        return input != null && P_EMAIL.matcher(input).matches();
    }

    /**
     * 验证身份证（18 位）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isIdCard(String input) {
        return input != null && P_ID_CARD.matcher(input).matches();
    }

    /**
     * 验证身份证（15 位或 18 位）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isIdCardAll(String input) {
        return input != null && P_ID_CARD_ALL.matcher(input).matches();
    }

    /**
     * 验证 URL
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isUrl(String input) {
        return input != null && P_URL.matcher(input).matches();
    }

    /**
     * 验证 IP 地址（IPv4）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isIp(String input) {
        return input != null && P_IP.matcher(input).matches();
    }

    /**
     * 验证 IP 地址（简单版）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isIpSimple(String input) {
        return input != null && P_IP_SIMPLE.matcher(input).matches();
    }

    /**
     * 验证日期（YYYY-MM-DD）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isDate(String input) {
        return input != null && P_DATE.matcher(input).matches();
    }

    /**
     * 验证日期时间（YYYY-MM-DD HH:MM:SS）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isDateTime(String input) {
        return input != null && P_DATETIME.matcher(input).matches();
    }

    /**
     * 验证时间（HH:MM:SS）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isTime(String input) {
        return input != null && P_TIME.matcher(input).matches();
    }

    /**
     * 验证邮政编码
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isPostalCode(String input) {
        return input != null && P_POSTAL_CODE.matcher(input).matches();
    }

    /**
     * 验证车牌号
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isLicensePlate(String input) {
        return input != null && P_LICENSE_PLATE.matcher(input).matches();
    }

    /**
     * 验证新能源车牌号
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isLicensePlateNewEnergy(String input) {
        return input != null && P_LICENSE_PLATE_NEW_ENERGY.matcher(input).matches();
    }

    /**
     * 验证统一社会信用代码
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isCreditCode(String input) {
        return input != null && P_CREDIT_CODE.matcher(input).matches();
    }

    /**
     * 验证银行卡号（简单验证）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isBankCard(String input) {
        return input != null && P_BANK_CARD.matcher(input).matches();
    }

    /**
     * 验证密码强度（至少包含数字和字母，长度 6-20）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isPassword(String input) {
        return input != null && P_PASSWORD.matcher(input).matches();
    }

    /**
     * 验证用户名（字母开头，允许字母数字下划线，长度 4-16）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isUsername(String input) {
        return input != null && P_USERNAME.matcher(input).matches();
    }

    /**
     * 验证是否为中文
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isChinese(String input) {
        return input != null && P_CHINESE.matcher(input).matches();
    }

    /**
     * 验证中文姓名
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isChineseName(String input) {
        return input != null && P_CHINESE_NAME.matcher(input).matches();
    }

    /**
     * 验证整数
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isInteger(String input) {
        return input != null && P_INTEGER.matcher(input).matches();
    }

    /**
     * 验证正整数
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isPositiveInteger(String input) {
        return input != null && P_POSITIVE_INTEGER.matcher(input).matches();
    }

    /**
     * 验证负整数
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isNegativeInteger(String input) {
        return input != null && P_NEGATIVE_INTEGER.matcher(input).matches();
    }

    /**
     * 验证浮点数
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isDecimal(String input) {
        return input != null && P_DECIMAL.matcher(input).matches();
    }

    /**
     * 验证正浮点数
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isPositiveDecimal(String input) {
        return input != null && P_POSITIVE_DECIMAL.matcher(input).matches();
    }

    /**
     * 验证负浮点数
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isNegativeDecimal(String input) {
        return input != null && P_NEGATIVE_DECIMAL.matcher(input).matches();
    }

    /**
     * 验证数字（整数或浮点数）
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isNumber(String input) {
        return input != null && P_NUMBER.matcher(input).matches();
    }

    /**
     * 验证 MAC 地址
     *
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isMac(String input) {
        return input != null && P_MAC.matcher(input).matches();
    }

    // ==================== 通用验证方法 ====================

    /**
     * 通用验证方法
     *
     * @param regex 正则表达式
     * @param input 输入字符串
     * @return 是否匹配
     */
    public static boolean isMatch(String regex, String input) {
        if (regex == null || input == null) {
            return false;
        }
        return Pattern.matches(regex, input);
    }

    /**
     * 通用验证方法（使用预编译 Pattern）
     *
     * @param pattern 预编译的正则模式
     * @param input   输入字符串
     * @return 是否匹配
     */
    public static boolean isMatch(Pattern pattern, String input) {
        return input != null && pattern.matcher(input).matches();
    }

    /**
     * 批量验证
     *
     * @param regex  正则表达式
     * @param inputs 输入字符串数组
     * @return 是否全部匹配
     */
    public static boolean isAllMatch(String regex, String... inputs) {
        if (inputs == null || inputs.length == 0) {
            return false;
        }
        Pattern pattern = Pattern.compile(regex);
        for (String input : inputs) {
            if (!isMatch(pattern, input)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 批量验证（任意一个匹配）
     *
     * @param regex  正则表达式
     * @param inputs 输入字符串数组
     * @return 是否有任意一个匹配
     */
    public static boolean isAnyMatch(String regex, String... inputs) {
        if (inputs == null || inputs.length == 0) {
            return false;
        }
        Pattern pattern = Pattern.compile(regex);
        for (String input : inputs) {
            if (isMatch(pattern, input)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 查找与提取方法 ====================

    /**
     * 查找第一个匹配的子串
     *
     * @param regex 正则表达式
     * @param input 输入字符串
     * @return 匹配的子串，未找到返回 null
     */
    public static String findFirst(String regex, String input) {
        if (regex == null || input == null) {
            return null;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 查找所有匹配的子串
     *
     * @param regex 正则表达式
     * @param input 输入字符串
     * @return 匹配的子串列表
     */
    public static List<String> findAll(String regex, String input) {
        List<String> result = new ArrayList<>();
        if (regex == null || input == null) {
            return result;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    /**
     * 提取第一个匹配的分组
     *
     * @param regex 正则表达式（必须包含分组）
     * @param input 输入字符串
     * @param group 分组索引（从 1 开始）
     * @return 匹配的分组内容，未找到返回 null
     */
    public static String extractGroup(String regex, String input, int group) {
        if (regex == null || input == null || group < 0) {
            return null;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find() && matcher.groupCount() >= group) {
            return matcher.group(group);
        }
        return null;
    }

    /**
     * 提取所有匹配的分组
     *
     * @param regex 正则表达式（必须包含分组）
     * @param input 输入字符串
     * @param group 分组索引（从 1 开始）
     * @return 匹配的分组内容列表
     */
    public static List<String> extractAllGroups(String regex, String input, int group) {
        List<String> result = new ArrayList<>();
        if (regex == null || input == null || group < 0) {
            return result;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        while (matcher.find() && matcher.groupCount() >= group) {
            result.add(matcher.group(group));
        }
        return result;
    }

    /**
     * 提取多个分组并拼接
     *
     * @param regex              正则表达式（必须包含分组）
     * @param input              输入字符串
     * @param replacementTemplate 替换模板（$1 表示分组 1，$2 表示分组 2，以此类推）
     * @return 拼接后的字符串
     */
    public static String extractMulti(String regex, String input, String replacementTemplate) {
        if (regex == null || input == null || replacementTemplate == null) {
            return null;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.replaceAll(replacementTemplate);
        }
        return null;
    }

    /**
     * 提取所有数字
     *
     * @param input 输入字符串
     * @return 数字列表
     */
    public static List<String> extractNumbers(String input) {
        return findAll(P_EXTRACT_NUMBER, input);
    }

    /**
     * 提取所有字母
     *
     * @param input 输入字符串
     * @return 字母列表
     */
    public static List<String> extractLetters(String input) {
        return findAll(P_EXTRACT_LETTER, input);
    }

    /**
     * 提取所有中文
     *
     * @param input 输入字符串
     * @return 中文列表
     */
    public static List<String> extractChinese(String input) {
        return findAll(P_EXTRACT_CHINESE, input);
    }

    // ==================== 替换方法 ====================

    /**
     * 替换第一个匹配的子串
     *
     * @param regex       正则表达式
     * @param input       输入字符串
     * @param replacement 替换字符串
     * @return 替换后的字符串
     */
    public static String replaceFirst(String regex, String input, String replacement) {
        if (regex == null || input == null || replacement == null) {
            return input;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.replaceFirst(replacement);
        }
        return input;
    }

    /**
     * 替换所有匹配的子串
     *
     * @param regex       正则表达式
     * @param input       输入字符串
     * @param replacement 替换字符串
     * @return 替换后的字符串
     */
    public static String replaceAll(String regex, String input, String replacement) {
        if (regex == null || input == null) {
            return input;
        }
        return input.replaceAll(regex, Objects.toString(replacement, EMPTY));
    }

    /**
     * 移除所有匹配的子串
     *
     * @param regex 正则表达式
     * @param input 输入字符串
     * @return 移除后的字符串
     */
    public static String removeAll(String regex, String input) {
        return replaceAll(regex, input, EMPTY);
    }

    /**
     * 移除所有匹配的子串（使用预编译 Pattern）
     *
     * @param pattern 预编译的正则模式
     * @param input   输入字符串
     * @return 移除后的字符串
     */
    public static String removeAll(Pattern pattern, String input) {
        return replaceAll(pattern, input, EMPTY);
    }

    /**
     * 移除 HTML 标签
     *
     * @param input 输入字符串
     * @return 移除 HTML 标签后的字符串
     */
    public static String removeHtmlTags(String input) {
        if (input == null) {
            return null;
        }
        return removeAll(HTML_TAG, input);
    }

    /**
     * 移除空白行
     *
     * @param input 输入字符串
     * @return 移除空白行后的字符串
     */
    public static String removeBlankLines(String input) {
        if (input == null) {
            return null;
        }
        return removeAll(P_BLANK_LINE, input);
    }

    /**
     * 统计字符串中双字节字符的个数
     *
     * @param input 输入字符串
     * @return 双字节字符的个数
     */
    public static int countDoubleByteChars(String input) {
        if (input == null) {
            return 0;
        }
        Pattern pattern = Pattern.compile("[^\\x00-\\xff]");
        Matcher matcher = pattern.matcher(input);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    // ==================== 分割方法 ====================

    /**
     * 按照正则表达式分割字符串
     *
     * @param regex 正则表达式
     * @param input 输入字符串
     * @return 分割后的字符串数组
     */
    public static String[] split(String regex, String input) {
        if (regex == null || input == null) {
            return new String[0];
        }
        return input.split(regex);
    }

    /**
     * 按照正则表达式分割字符串（限制分割数量）
     *
     * @param regex 正则表达式
     * @param input 输入字符串
     * @param limit 分割数量限制
     * @return 分割后的字符串数组
     */
    public static String[] split(String regex, String input, int limit) {
        if (regex == null || input == null) {
            return new String[0];
        }
        return input.split(regex, limit);
    }

    // ==================== 统计方法 ====================

    /**
     * 统计字符串中匹配正则表达式的子串个数
     *
     * @param regex 正则表达式
     * @param input 输入字符串
     * @return 匹配的子串个数
     */
    public static int countMatches(String regex, String input) {
        if (regex == null || input == null) {
            return 0;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 统计字符串中数字的个数
     *
     * @param input 输入字符串
     * @return 数字的个数
     */
    public static int countNumbers(String input) {
        return countMatches(P_EXTRACT_NUMBER, input);
    }

    /**
     * 统计字符串中中文的个数
     *
     * @param input 输入字符串
     * @return 中文的个数
     */
    public static int countChinese(String input) {
        return countMatches(P_EXTRACT_CHINESE, input);
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断字符串是否为空或 null
     *
     * @param input 输入字符串
     * @return 是否为空或 null
     */
    public static boolean isBlank(String input) {
        return input == null || input.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空且不为 null
     *
     * @param input 输入字符串
     * @return 是否不为空且不为 null
     */
    public static boolean isNotBlank(String input) {
        return !isBlank(input);
    }

    /**
     * 判断字符串是否只包含数字
     *
     * @param input 输入字符串
     * @return 是否只包含数字
     */
    public static boolean isNumeric(String input) {
        if (isBlank(input)) {
            return false;
        }
        return input.chars().allMatch(Character::isDigit);
    }

    /**
     * 判断字符串是否只包含字母
     *
     * @param input 输入字符串
     * @return 是否只包含字母
     */
    public static boolean isAlpha(String input) {
        if (isBlank(input)) {
            return false;
        }
        return input.chars().allMatch(Character::isLetter);
    }

    /**
     * 判断字符串是否只包含字母或数字
     *
     * @param input 输入字符串
     * @return 是否只包含字母或数字
     */
    public static boolean isAlphaNumeric(String input) {
        if (isBlank(input)) {
            return false;
        }
        return input.chars().allMatch(Character::isLetterOrDigit);
    }

    /**
     * 获取预编译的 Pattern 对象
     *
     * @param type 正则类型（如 MOBILE_SIMPLE, EMAIL 等）
     * @return 预编译的 Pattern 对象
     */
    public static Pattern getPattern(String type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case MOBILE_SIMPLE:
                return P_MOBILE_SIMPLE;
            case MOBILE_EXACT:
                return P_MOBILE_EXACT;
            case TELEPHONE:
                return P_TELEPHONE;
            case EMAIL:
                return P_EMAIL;
            case ID_CARD:
                return P_ID_CARD;
            case ID_CARD_ALL:
                return P_ID_CARD_ALL;
            case URL:
                return P_URL;
            case IP:
                return P_IP;
            case IP_SIMPLE:
                return P_IP_SIMPLE;
            case DATE:
                return P_DATE;
            case DATETIME:
                return P_DATETIME;
            case TIME:
                return P_TIME;
            case POSTAL_CODE:
                return P_POSTAL_CODE;
            case LICENSE_PLATE:
                return P_LICENSE_PLATE;
            case LICENSE_PLATE_NEW_ENERGY:
                return P_LICENSE_PLATE_NEW_ENERGY;
            case CREDIT_CODE:
                return P_CREDIT_CODE;
            case BANK_CARD:
                return P_BANK_CARD;
            case PASSWORD:
                return P_PASSWORD;
            case USERNAME:
                return P_USERNAME;
            case CHINESE:
                return P_CHINESE;
            case CHINESE_NAME:
                return P_CHINESE_NAME;
            case INTEGER:
                return P_INTEGER;
            case POSITIVE_INTEGER:
                return P_POSITIVE_INTEGER;
            case NEGATIVE_INTEGER:
                return P_NEGATIVE_INTEGER;
            case DECIMAL:
                return P_DECIMAL;
            case POSITIVE_DECIMAL:
                return P_POSITIVE_DECIMAL;
            case NEGATIVE_DECIMAL:
                return P_NEGATIVE_DECIMAL;
            case NUMBER:
                return P_NUMBER;
            case MAC:
                return P_MAC;
            case HTML_TAG:
                return P_HTML_TAG;
            case EXTRACT_NUMBER:
                return P_EXTRACT_NUMBER;
            case EXTRACT_LETTER:
                return P_EXTRACT_LETTER;
            case EXTRACT_CHINESE:
                return P_EXTRACT_CHINESE;
            default:
                return null;
        }
    }

    /**
     * 查找第一个匹配的子串（使用预编译 Pattern）
     *
     * @param pattern 预编译的正则模式
     * @param input   输入字符串
     * @return 匹配的子串，未找到返回 null
     */
    public static String findFirst(Pattern pattern, String input) {
        if (pattern == null || input == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 查找所有匹配的子串（使用预编译 Pattern）
     *
     * @param pattern 预编译的正则模式
     * @param input   输入字符串
     * @return 匹配的子串列表
     */
    public static List<String> findAll(Pattern pattern, String input) {
        List<String> result = new ArrayList<>();
        if (pattern == null || input == null) {
            return result;
        }
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    /**
     * 替换所有匹配的子串（使用预编译 Pattern）
     *
     * @param pattern     预编译的正则模式
     * @param input       输入字符串
     * @param replacement 替换字符串
     * @return 替换后的字符串
     */
    public static String replaceAll(Pattern pattern, String input, String replacement) {
        if (pattern == null || input == null) {
            return input;
        }
        return pattern.matcher(input).replaceAll(Objects.toString(replacement, EMPTY));
    }

    /**
     * 统计字符串中匹配正则表达式的子串个数（使用预编译 Pattern）
     *
     * @param pattern 预编译的正则模式
     * @param input   输入字符串
     * @return 匹配的子串个数
     */
    public static int countMatches(Pattern pattern, String input) {
        if (pattern == null || input == null) {
            return 0;
        }
        Matcher matcher = pattern.matcher(input);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
