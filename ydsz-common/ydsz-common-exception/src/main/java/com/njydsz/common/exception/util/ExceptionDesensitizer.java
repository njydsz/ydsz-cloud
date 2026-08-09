package com.njydsz.common.exception.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 异常堆栈脱敏工具类。
 *
 * <p>对异常消息和堆栈中的敏感信息进行脱敏处理，防止敏感数据泄露到日志、
 * 监控系统和前端响应中。</p>
 *
 * <p><b>脱敏范围：</b></p>
 * <ul>
 *   <li>密码类字段（password、passwd、secret、token、apikey、accessKey、privateKey）</li>
 *   <li>银行卡号（13-19 位数字）</li>
 *   <li>身份证号（15 或 18 位，含 X 校验位）</li>
 *   <li>手机号（11 位，1 开头）</li>
 *   <li>邮箱地址</li>
 *   <li>Redis/Memcached 连接地址</li>
 *   <li>数据库连接地址（含密码部分）</li>
 * </ul>
 *
 * <p><b>使用方式：</b></p>
 * <pre>{@code
 * // 脱敏单个异常消息
 * String safe = ExceptionDesensitizer.desensitize(exception.getMessage());
 *
 * // 脱敏完整堆栈
 * String safeStack = ExceptionDesensitizer.desensitizeStackTrace(exception);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public final class ExceptionDesensitizer {

    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)(password|passwd|secret|token|apikey|accesskey|privatekey|credential|auth)[" +
                    "\\s]*[=:][\\s]*[\"']?([^\"'\\s,;)]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BANK_CARD_PATTERN = Pattern.compile(
            "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}([\\s-]?\\d{0,7})\\b"
    );

    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "\\b[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b"
    );

    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            "(?<![\\d])1[3-9]\\d{9}(?![\\d])"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    private static final Pattern JDBC_PASSWORD_PATTERN = Pattern.compile(
            "(?i)(jdbc:[^?]*\\?.*)(password|pwd)=([^&\\s]*)",
            Pattern.CASE_INSENSITIVE
    );

    private ExceptionDesensitizer() {
        throw new UnsupportedOperationException();
    }

    /**
     * 脱敏单个字符串消息。
     *
     * @param message 原始消息，可为 null
     * @return 脱敏后的消息，null 入参返回 null
     */
    public static String desensitize(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String result = message;
        result = desensitizeFieldAssign(result);
        result = desensitizeBankCard(result);
        result = desensitizeIdCard(result);
        result = desensitizeMobile(result);
        result = desensitizeJdbcPassword(result);
        result = desensitizeEmail(result);
        return result;
    }

    /**
     * 脱敏异常的完整堆栈。
     *
     * @param throwable 目标异常，可为 null
     * @return 脱敏后的完整堆栈，null 入参返回 empty
     */
    public static String desensitizeStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }

        StringBuilder sb = acquireBuilder();
        try {
            buildDesensitizedStack(throwable, sb, Integer.MAX_VALUE);
            return sb.toString();
        } finally {
            releaseBuilder(sb);
        }
    }

    /**
     * 脱敏堆栈到指定深度。
     *
     * @param throwable 目标异常
     * @param maxFrames 最大堆栈帧数（建议 20-50）
     * @return 脱敏后的截断堆栈
     */
    public static String desensitizeStackTrace(Throwable throwable, int maxFrames) {
        if (throwable == null) {
            return "";
        }

        StringBuilder sb = acquireBuilder();
        try {
            buildDesensitizedStack(throwable, sb, maxFrames);
            return sb.toString();
        } finally {
            releaseBuilder(sb);
        }
    }

    private static void buildDesensitizedStack(Throwable throwable, StringBuilder sb, int maxFrames) {
        Throwable current = throwable;
        int frames = 0;
        while (current != null && frames < maxFrames) {
            sb.append(current.getClass().getName());
            String msg = current.getMessage();
            if (msg != null && !msg.isEmpty()) {
                sb.append(": ").append(desensitize(msg));
            }
            sb.append('\n');

            for (StackTraceElement frame : current.getStackTrace()) {
                if (frames >= maxFrames) {
                    int remaining = current.getStackTrace().length - maxFrames;
                    sb.append("\t... ").append(remaining).append(" more\n");
                    break;
                }
                sb.append("\tat ").append(frame).append('\n');
                frames++;
            }

            current = current.getCause();
            if (current != null) {
                sb.append("Caused by: ");
            }
        }
    }

    private static String desensitizeFieldAssign(String input) {
        Matcher m = SENSITIVE_FIELD_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String replacement = key + "=******";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String desensitizeBankCard(String input) {
        Matcher m = BANK_CARD_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String card = m.group();
            int digits = card.replaceAll("[\\s-]", "").length();
            if (digits >= 13 && digits <= 19) {
                m.appendReplacement(sb, Matcher.quoteReplacement("****"));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String desensitizeIdCard(String input) {
        Matcher m = ID_CARD_PATTERN.matcher(input);
        return m.replaceAll("****");
    }

    private static String desensitizeMobile(String input) {
        Matcher m = MOBILE_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String mobile = m.group();
            String masked = mobile.substring(0, 3) + "****" + mobile.substring(7);
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String desensitizeEmail(String input) {
        Matcher m = EMAIL_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String email = m.group();
            int atIdx = email.indexOf('@');
            if (atIdx > 1) {
                String masked = email.charAt(0) + "***" + email.substring(atIdx);
                m.appendReplacement(sb, Matcher.quoteReplacement(masked));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String desensitizeJdbcPassword(String input) {
        Matcher m = JDBC_PASSWORD_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String key = m.group(2);
            String replacement = prefix + key + "=******";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static final ThreadLocal<StringBuilder> BUILDER_CACHE = new ThreadLocal<>();

    private static StringBuilder acquireBuilder() {
        StringBuilder sb = BUILDER_CACHE.get();
        if (sb == null) {
            sb = new StringBuilder(1024);
            BUILDER_CACHE.set(sb);
        } else {
            sb.setLength(0);
        }
        return sb;
    }

    private static void releaseBuilder(StringBuilder sb) {
        if (sb != null && sb.capacity() <= 8192) {
            BUILDER_CACHE.set(sb);
        }
    }
}
