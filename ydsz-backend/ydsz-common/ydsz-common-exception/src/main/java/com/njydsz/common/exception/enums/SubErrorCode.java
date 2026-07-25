package com.njydsz.common.exception.enums;

/**
 * 子错误码常量接口
 *
 * <p>用于在主错误码（{@link ExceptionCode}）下细分具体场景。
 * 子错误码采用 4 位数字字符串（如 "0001"），便于日志检索与客户端差异化提示。
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>每个主错误码下的子错误码必须从 0001 开始递增（0000 保留为"无子错误码"）</li>
 *   <li>子错误码在主错误码范围内全局唯一，但不跨主错误码</li>
 *   <li>子错误码与国际化 key 一一对应：messages_{subCode}.properties</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 定义子错误码常量
 * public final class UserSubErrorCode implements SubErrorCode {
 *     public static final String PASSWORD_INCORRECT = "0001";
 *     public static final String ACCOUNT_LOCKED = "0002";
 *     public static final String EMAIL_NOT_VERIFIED = "0003";
 *     public static final String PHONE_NOT_VERIFIED = "0004";
 *
 *     private UserSubErrorCode() {}
 * }
 *
 * // 业务使用
 * throw BusinessException.builder()
 *     .code(UnifiedExceptionCode.AUTHENTICATION_FAILED.getCode())
 *     .subCode(UserSubErrorCode.PASSWORD_INCORRECT)
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface SubErrorCode {

    /**
     * 默认子错误码（无子错误码）
     */
    String DEFAULT = "0000";

    /**
     * 校验子错误码格式（4 位数字）
     *
     * @param subCode 子错误码字符串
     * @return true-有效，false-无效
     */
    static boolean isValid(String subCode) {
        if (subCode == null) {
            return false;
        }
        if (subCode.length() != 4) {
            return false;
        }
        for (int i = 0; i < subCode.length(); i++) {
            char c = subCode.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * 规范化子错误码（4 位补零）
     *
     * @param code 原始子错误码（整数）
     * @return 4 位数字字符串
     */
    static String normalize(int code) {
        if (code < 0 || code > 9999) {
            throw new IllegalArgumentException("SubCode must be in range [0, 9999], got: " + code);
        }
        return String.format("%04d", code);
    }
}
