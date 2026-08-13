package com.njydsz.common.util.mask;

import java.util.Objects;

/**
 * 数据脱敏工具类
 *
 * <p>提供个人隐私数据的掩码/脱敏操作，用于日志打印、前端展示等场景。
 * 涵盖手机号、身份证号、银行卡、邮箱、姓名等常见敏感信息的脱敏规则。
 *
 * <p>所有方法均为 null 安全：输入 null 时返回 null。
 *
 * <p>脱敏规则遵循等保 2.0 要求：保留必要信息以便识别，隐藏关键位数。
 *
 * @author ydsz-team
 * @since 4.0.0
 */
public final class MaskUtils {

    /** 默认掩码字符 */
    private static final char MASK_CHAR = '*';

    /** 手机号保留前位数 */
    private static final int PHONE_PREFIX_LEN = 3;

    /** 手机号保留后位数 */
    private static final int PHONE_SUFFIX_LEN = 4;

    /** 身份证号保留前位数 */
    private static final int ID_CARD_PREFIX_LEN = 3;

    /** 身份证号保留后位数 */
    private static final int ID_CARD_SUFFIX_LEN = 4;

    /** 银行卡号保留前位数 */
    private static final int BANK_CARD_PREFIX_LEN = 4;

    /** 银行卡号保留后位数 */
    private static final int BANK_CARD_SUFFIX_LEN = 4;

    /** 邮箱前缀最少保留位数 */
    private static final int EMAIL_PREFIX_KEEP = 1;

    /** 姓名保留前位数 */
    private static final int NAME_PREFIX_LEN = 1;

    /** 默认掩码字符重复次数 */
    private static final int DEFAULT_MASK_REPEAT = 4;

    /** 最小可脱敏文本长度（至少保留前缀+后缀能覆盖的长度） */
    private static final int MIN_MASKABLE_LENGTH = 0;

    private MaskUtils() {
        throw new UnsupportedOperationException("MaskUtils is a utility class and cannot be instantiated");
    }

    // ==================== 特定类型脱敏 ====================

    /**
     * 手机号脱敏，保留前 3 后 4 位，中间用 **** 替代。
     *
     * <p>如 "13812345678" → "138****5678"。不足 7 位时尽可能保留前后部分。
     *
     * @param phone 手机号
     * @return 脱敏后手机号，输入为 null 返回 null
     */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        return mask(phone, PHONE_PREFIX_LEN, PHONE_SUFFIX_LEN);
    }

    /**
     * 身份证号脱敏，保留前 3 后 4 位，中间用 * 替代。
     *
     * <p>如 "320102199001011234" → "320***********1234"。
     *
     * @param idCard 身份证号
     * @return 脱敏后身份证号，输入为 null 返回 null
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null) {
            return null;
        }
        return mask(idCard, ID_CARD_PREFIX_LEN, ID_CARD_SUFFIX_LEN);
    }

    /**
     * 银行卡号脱敏，保留前 4 后 4 位，中间用 **** 替代。
     *
     * <p>如 "6222021234567890" → "6222****7890"。
     *
     * @param bankCard 银行卡号
     * @return 脱敏后银行卡号，输入为 null 返回 null
     */
    public static String maskBankCard(String bankCard) {
        if (bankCard == null) {
            return null;
        }
        return mask(bankCard, BANK_CARD_PREFIX_LEN, BANK_CARD_SUFFIX_LEN);
    }

    /**
     * 邮箱脱敏，保留第 1 字符和 @ 及域名部分。
     *
     * <p>如 "abc@qq.com" → "a****@qq.com"。如果没有 @ 符号，按通用脱敏处理。
     *
     * @param email 邮箱地址
     * @return 脱敏后邮箱，输入为 null 返回 null
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= EMAIL_PREFIX_KEEP) {
            return mask(email, EMAIL_PREFIX_KEEP, email.length() - EMAIL_PREFIX_KEEP);
        }
        String prefix = email.substring(0, EMAIL_PREFIX_KEEP);
        String suffix = email.substring(atIndex);
        int maskLength = atIndex - EMAIL_PREFIX_KEEP;
        return prefix + repeatMask(maskLength) + suffix;
    }

    /**
     * 姓名脱敏，保留首字，其余用 * 替代。
     *
     * <p>如 "张三" → "张*"，"张三丰" → "张**"。单字姓名不脱敏。
     *
     * @param name 姓名
     * @return 脱敏后姓名，输入为 null 返回 null
     */
    public static String maskName(String name) {
        if (name == null) {
            return null;
        }
        if (name.length() <= NAME_PREFIX_LEN) {
            return name;
        }
        return name.substring(0, NAME_PREFIX_LEN) + repeatMask(name.length() - NAME_PREFIX_LEN);
    }

    // ==================== 通用脱敏 ====================

    /**
     * 通用脱敏方法：保留前 keepPrefix 和后 keepSuffix 字符，中间用 * 替代。
     *
     * <p>边界处理规则：
     * <ul>
     *   <li>如果 keepPrefix + keepSuffix >= 文本长度，尽可能保留前后部分，不追加掩码</li>
     *   <li>如果文本为 null，返回 null</li>
     *   <li>长度不足以保留时，前缀尽量保留，剩余给后缀</li>
     * </ul>
     *
     * @param text       待脱敏文本
     * @param keepPrefix 保留的前缀字符数（应 >= 0）
     * @param keepSuffix 保留的后缀字符数（应 >= 0）
     * @return 脱敏后文本，输入为 null 返回 null
     */
    public static String mask(String text, int keepPrefix, int keepSuffix) {
        if (text == null) {
            return null;
        }
        int length = text.length();
        if (length == 0) {
            return text;
        }
        int prefix = Math.max(0, keepPrefix);
        int suffix = Math.max(0, keepSuffix);
        if (prefix + suffix >= length) {
            return text;
        }
        String prefixStr = text.substring(0, prefix);
        String suffixStr = text.substring(length - suffix);
        int maskLength = length - prefix - suffix;
        return prefixStr + repeatMask(maskLength) + suffixStr;
    }

    // ==================== 内部方法 ====================

    /**
     * 生成指定重复次数的掩码字符串。
     *
     * @param count 重复次数
     * @return 重复掩码字符串，count <= 0 时返回空字符串
     */
    private static String repeatMask(int count) {
        if (count <= MIN_MASKABLE_LENGTH) {
            return "";
        }
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(MASK_CHAR);
        }
        return sb.toString();
    }
}
