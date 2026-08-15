package com.njydsz.message.server.util;

import java.util.regex.Pattern;

/**
 * 消息模块 PII 脱敏工具类。
 *
 * <p>统一收口消息发送、日志打印场景中收方（receiver）联系方式的脱敏逻辑。
 * 覆盖手机号、邮箱、身份证三种常见 PII 形态，其它形态（userId / openId 等）
 * 保留前 2 后 2，中间以 {@code ***} 替代。
 *
 * <p>所有方法均为 null 安全：输入 null / 空串时原样返回。
 *
 * <p><b>使用约定：</b>
 * <ul>
 *   <li>日志打印 receiver 前必须调用 {@link #maskReceiver(String)}</li>
 *   <li>禁止在 INFO / WARN / ERROR 级别日志中出现明文手机号 / 邮箱 / 身份证</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class PiiMasker {

    /** 11 位手机号正则 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 邮箱正则（简易） */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** 15 或 18 位身份证正则 */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{15}$|^\\d{17}[\\dXx]$");

    private PiiMasker() {
        throw new UnsupportedOperationException("PiiMasker is a utility class and cannot be instantiated");
    }

    /**
     * 手机号脱敏，保留前 3 后 4 位。
     *
     * <p>如 {@code "13812345678"} → {@code "138****5678"}。
     *
     * @param mobile 手机号
     * @return 脱敏后手机号，输入为 null 返回 null
     */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.isEmpty()) {
            return mobile;
        }
        if (mobile.length() < 7) {
            return "****";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    /**
     * 邮箱脱敏，保留首字符和 @ 及域名部分。
     *
     * <p>如 {@code "user@test.com"} → {@code "u***@test.com"}。
     *
     * @param email 邮箱地址
     * @return 脱敏后邮箱，输入为 null 返回 null
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "****";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /**
     * 身份证号脱敏，保留前 3 后 4 位。
     *
     * <p>如 {@code "110101199001011234"} → {@code "110***********1234"}。
     *
     * @param idCard 身份证号（15 或 18 位）
     * @return 脱敏后身份证号，输入为 null 返回 null
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.isEmpty()) {
            return idCard;
        }
        if (idCard.length() < 7) {
            return "****";
        }
        return idCard.substring(0, 3) + "***********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 智能识别 receiver 形态并脱敏。
     *
     * <p>按以下优先级匹配：
     * <ol>
     *   <li>11 位手机号 → {@link #maskMobile(String)}</li>
     *   <li>邮箱 → {@link #maskEmail(String)}</li>
     *   <li>15/18 位身份证 → {@link #maskIdCard(String)}</li>
     *   <li>其它（userId / openId 等）→ 保留前 2 后 2，中间 {@code ***}</li>
     * </ol>
     *
     * @param receiver 原始收方标识（手机号 / 邮箱 / 身份证 / userId / openId 等）
     * @return 脱敏后的值，输入为 null / 空串时原样返回
     */
    public static String maskReceiver(String receiver) {
        if (receiver == null || receiver.isEmpty()) {
            return receiver;
        }
        if (PHONE_PATTERN.matcher(receiver).matches()) {
            return maskMobile(receiver);
        }
        if (EMAIL_PATTERN.matcher(receiver).matches()) {
            return maskEmail(receiver);
        }
        if (ID_CARD_PATTERN.matcher(receiver).matches()) {
            return maskIdCard(receiver);
        }
        // 其它形态（userId / openId 等）：保留前 2 后 2，中间 ***
        if (receiver.length() <= 4) {
            return "****";
        }
        return receiver.substring(0, 2) + "***" + receiver.substring(receiver.length() - 2);
    }
}
