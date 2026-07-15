package com.njydsz.pmis.workflow.server.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 敏感字段脱敏器（P0-1 落地）。
 *
 * <p>对标钉钉/飞书审批的敏感数据自动脱敏能力，在审计日志写入、通知内容组装、
 * 评论持久化等场景统一调用，防止手机号、身份证号、银行卡号等 PII 数据明文暴露。
 *
 * <h3>脱敏策略</h3>
 * <ul>
 *   <li><b>手机号</b> — 11 位数字，保留前 3 后 4：{@code 138****8888}</li>
 *   <li><b>身份证号</b> — 18 位（末位可为 X），保留前 4 后 4：{@code 3201**********1234}</li>
 *   <li><b>银行卡号</b> — 16-19 位数字，保留前 4 后 4：{@code 6228******5678}</li>
 *   <li><b>邮箱</b> — 保留首字符 + @后缀：{@code z***@example.com}</li>
 *   <li><b>自定义字段</b> — 通过 {@link #maskFields} 对 Map 中指定 key 的值整体脱敏</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   // 字符串自动脱敏
 *   String safe = masker.mask("联系电话 13812345678");
 *
 *   // Map 字段级脱敏
 *   Map&lt;String, Object&gt; vars = masker.maskFields(variables, List.of("phone", "idCard"));
 * </pre>
 *
 * <p>所有方法均为无状态纯函数，线程安全。
 *
 * @since 1.8.0
 */
@Slf4j
@Component
public class FlowSensitiveMasker {

    // ============================== 正则模式 ==============================

    /** 手机号：11 位数字，1 开头 */
    private static final Pattern PHONE =
            Pattern.compile("(?<![0-9])(1[3-9]\\d{9})(?![0-9])");

    /** 身份证号：18 位（前 17 位数字 + 末位数字或 X），宽松匹配 */
    private static final Pattern ID_CARD =
            Pattern.compile("(?<![0-9])([1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx])(?![0-9])");

    /** 银行卡号：16-19 位连续数字 */
    private static final Pattern BANK_CARD =
            Pattern.compile("(?<![0-9])(6[2-9]\\d{14,17})(?![0-9])");

    /** 邮箱地址 */
    private static final Pattern EMAIL =
            Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    /** 默认脱敏占位符 */
    private static final String MASK = "*";

    // ============================== 公共 API ==============================

    /**
     * 对字符串进行自动脱敏（手机号 / 身份证 / 银行卡 / 邮箱）。
     *
     * <p>依次应用所有正则规则，对匹配到的敏感信息做部分遮蔽。
     * 不匹配任何规则的文本原样返回。
     *
     * @param text 原始文本（可为 null）
     * @return 脱敏后文本
     */
    public String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        try {
            result = maskPhone(result);
            result = maskIdCard(result);
            result = maskBankCard(result);
            result = maskEmail(result);
        } catch (Exception e) {
            // 脱敏异常时返回原文，绝不阻断主流程
            log.warn("[FlowMasker] 脱敏异常，返回原文: err={}", e.getMessage());
            return text;
        }
        return result;
    }

    /**
     * 对 Map 中指定 key 的值进行字段级脱敏。
     *
     * <p>用于流程变量、通知 payload 等结构化数据的字段级精确脱敏。
     * 仅对 {@code fieldKeys} 中列出的 key 做脱敏，其他字段不动。
     * 值为 null 时跳过；值为字符串时调用 {@link #mask(String)}；
     * 值为其他类型时转为字符串再脱敏。
     *
     * @param data      原始 Map（不会被修改，返回新 Map）
     * @param fieldKeys 需脱敏的字段名列表
     * @return 脱敏后的新 Map
     */
    public Map<String, Object> maskFields(Map<String, Object> data, List<String> fieldKeys) {
        if (data == null || data.isEmpty() || fieldKeys == null || fieldKeys.isEmpty()) {
            return data;
        }
        // 浅拷贝，避免修改原始 Map
        Map<String, Object> copy = new LinkedHashMap<>(data);
        for (String key : fieldKeys) {
            if (key == null || !copy.containsKey(key)) {
                continue;
            }
            Object val = copy.get(key);
            if (val == null) {
                continue;
            }
            if (val instanceof String s) {
                copy.put(key, mask(s));
            } else {
                copy.put(key, mask(String.valueOf(val)));
            }
        }
        return copy;
    }

    /**
     * 便捷方法：对 Map 中疑似敏感字段自动脱敏。
     *
     * <p>扫描 Map 的 key，若 key 名称匹配常见敏感字段名（phone/mobile/tel/idCard/idNo/
     * bankCard/cardNo/email 等，不区分大小写），则对其值做脱敏。
     *
     * @param data 原始 Map
     * @return 脱敏后的新 Map
     */
    public Map<String, Object> maskAuto(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        List<String> sensitiveKeys = new ArrayList<>();
        for (String key : data.keySet()) {
            if (key == null) continue;
            String lower = key.toLowerCase();
            if (lower.contains("phone") || lower.contains("mobile")
                    || lower.contains("tel") || lower.contains("idcard")
                    || lower.contains("idno") || lower.contains("identity")
                    || lower.contains("bankcard") || lower.contains("cardno")
                    || lower.contains("bankno") || lower.contains("email")
                    || lower.contains("id_number") || lower.contains("certno")) {
                sensitiveKeys.add(key);
            }
        }
        return maskFields(data, sensitiveKeys);
    }

    // ============================== 内部脱敏方法 ==============================

    /**
     * 手机号脱敏：138****8888
     */
    private String maskPhone(String text) {
        Matcher m = PHONE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String phone = m.group(1);
            String masked = phone.substring(0, 3)
                    + MASK.repeat(4)
                    + phone.substring(7);
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 身份证号脱敏：3201**********1234
     */
    private String maskIdCard(String text) {
        Matcher m = ID_CARD.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String id = m.group(1);
            String masked = id.substring(0, 4)
                    + MASK.repeat(10)
                    + id.substring(14);
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 银行卡号脱敏：6228******5678
     */
    private String maskBankCard(String text) {
        Matcher m = BANK_CARD.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String card = m.group(1);
            int len = card.length();
            if (len <= 8) {
                // 短号码不做脱敏（可能不是银行卡）
                continue;
            }
            String masked = card.substring(0, 4)
                    + MASK.repeat(len - 8)
                    + card.substring(len - 4);
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 邮箱脱敏：z***@example.com
     */
    private String maskEmail(String text) {
        Matcher m = EMAIL.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String user = m.group(1);
            String domain = m.group(2);
            String maskedUser;
            if (user.length() <= 1) {
                maskedUser = MASK;
            } else if (user.length() <= 3) {
                maskedUser = user.charAt(0) + MASK.repeat(user.length() - 1);
            } else {
                maskedUser = user.charAt(0) + MASK.repeat(3);
            }
            String masked = maskedUser + "@" + domain;
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
