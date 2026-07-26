package com.njydsz.workflow.server.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.njydsz.common.safe.sensitive.SensitiveType;
import com.njydsz.common.safe.sensitive.SensitiveUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 敏感字段脱敏器（P0-1 落地）。
 *
 * <p>对标钉钉/飞书审批的敏感数据自动脱敏能力，在审计日志写入、通知内容组装、
 * 评论持久化等场景统一调用，防止手机号、身份证号、银行卡号等 PII 数据明文暴露。
 *
 * <h3>脱敏策略</h3>
 * <ul>
 *   <li><b>手机号</b> — 正则扫描文本中的手机号，委托 {@link SensitiveUtil#desensitize} 脱敏</li>
 *   <li><b>身份证号</b> — 正则扫描文本中的身份证号，委托 {@link SensitiveUtil#desensitize} 脱敏</li>
 *   <li><b>银行卡号</b> — 正则扫描文本中的银行卡号，委托 {@link SensitiveUtil#desensitize} 脱敏</li>
 *   <li><b>邮箱</b> — 正则扫描文本中的邮箱地址，委托 {@link SensitiveUtil#desensitize} 脱敏</li>
 *   <li><b>自定义字段</b> — 通过 {@link #maskFields} 对 Map 中指定 key 的值整体脱敏</li>
 * </ul>
 *
 * <p>P1-4: 脱敏算法委托 {@link SensitiveUtil}（common-safe 模块），消除重复的正则替换逻辑。
 * 本类仅保留文本扫描（find-and-replace）能力，因为通知内容/评论内容是自由文本，
 * 需要从文本中自动发现 PII 并替换。
 *
 * <p>所有方法均为无状态纯函数，线程安全。
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class FlowSensitiveMasker {

    // ============================== 正则模式（仅用于文本扫描，脱敏算法委托 SensitiveUtil） ==============================

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

    // ============================== 公共 API ==============================

    /**
     * 对字符串进行自动脱敏（手机号 / 身份证 / 银行卡 / 邮箱）。
     *
     * <p>依次扫描所有正则规则，对匹配到的敏感信息委托 {@link SensitiveUtil} 脱敏。
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
            result = maskPattern(result, PHONE, SensitiveType.PHONE);
            result = maskPattern(result, ID_CARD, SensitiveType.ID_CARD);
            result = maskPattern(result, BANK_CARD, SensitiveType.BANK_CARD);
            result = maskEmailPattern(result);
        } catch (Exception e) {
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
     *
     * @param data      原始 Map（不会被修改，返回新 Map）
     * @param fieldKeys 需脱敏的字段名列表
     * @return 脱敏后的新 Map
     */
    public Map<String, Object> maskFields(Map<String, Object> data, List<String> fieldKeys) {
        if (data == null || data.isEmpty() || fieldKeys == null || fieldKeys.isEmpty()) {
            return data;
        }
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

    // ============================== 内部方法（委托 SensitiveUtil） ==============================

    /**
     * 通用正则匹配 + 委托 SensitiveUtil 脱敏
     *
     * @param text 原始文本
     * @param pattern PII 正则模式
     * @param type SensitiveUtil 脱敏类型
     * @return 脱敏后文本
     */
    private String maskPattern(String text, Pattern pattern, SensitiveType type) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String raw = m.group(1);
            String masked = SensitiveUtil.desensitize(raw, type);
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 邮箱脱敏需特殊处理（正则捕获了 user + domain 两组）
     */
    private String maskEmailPattern(String text) {
        Matcher m = EMAIL.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String fullEmail = m.group(1) + "@" + m.group(2);
            String masked = SensitiveUtil.desensitize(fullEmail, SensitiveType.EMAIL);
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
