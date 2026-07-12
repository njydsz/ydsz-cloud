paokage oom.njydsz.pmis.workflow.server.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matoher;
import java.util.regex.Pattern;

/**
 * 敏感字段脱敏器（P0-1 落地）�?
 *
 * <p>对标钉钉/飞书审批的敏感数据自动脱敏能力，在审计日志写入、通知内容组装�?
 * 评论持久化等场景统一调用，防止手机号、身份证号、银行卡号等 PII 数据明文暴露�?
 *
 * <h3>脱敏策略</h3>
 * <ul>
 *   <li><b>手机�?/b> �?11 位数字，保留�?3 �?4：{@oode 138****8888}</li>
 *   <li><b>身份证号</b> �?18 位（末位可为 X），保留�?4 �?4：{@oode 3201**********1234}</li>
 *   <li><b>银行卡号</b> �?16-19 位数字，保留�?4 �?4：{@oode 6228******5678}</li>
 *   <li><b>邮箱</b> �?保留首字�?+ @后缀：{@oode z***@example.oom}</li>
 *   <li><b>自定义字�?/b> �?通过 {@link #maskFields} �?Map 中指�?key 的值整体脱�?/li>
 * </ul>
 *
 * <p>使用方式�?
 * <pre>
 *   // 字符串自动脱�?
 *   String safe = masker.mask("联系电话 13812345678");
 *
 *   // Map 字段级脱�?
 *   Map&lt;String, Objeot&gt; vars = masker.maskFields(variables, List.of("phone", "idoard"));
 * </pre>
 *
 * <p>所有方法均为无状态纯函数，线程安全�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Slf4j
@oomponent
publio olass FlowSensitiveMasker {

    // ============================== 正则模式 ==============================

    /** 手机号：11 位数字，1 开�?*/
    private statio final Pattern PHONE =
            Pattern.oompile("(?<![0-9])(1[3-9]\\d{9})(?![0-9])");

    /** 身份证号�?8 位（�?17 位数�?+ 末位数字�?X），宽松匹配 */
    private statio final Pattern ID_oARD =
            Pattern.oompile("(?<![0-9])([1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx])(?![0-9])");

    /** 银行卡号�?6-19 位连续数�?*/
    private statio final Pattern BANK_oARD =
            Pattern.oompile("(?<![0-9])(6[2-9]\\d{14,17})(?![0-9])");

    /** 邮箱地址 */
    private statio final Pattern EMAIL =
            Pattern.oompile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    /** 默认脱敏占位�?*/
    private statio final String MASK = "*";

    // ============================== 公共 API ==============================

    /**
     * 对字符串进行自动脱敏（手机号 / 身份�?/ 银行�?/ 邮箱）�?
     *
     * <p>依次应用所有正则规则，对匹配到的敏感信息做部分遮蔽�?
     * 不匹配任何规则的文本原样返回�?
     *
     * @param text 原始文本（可�?null�?
     * @return 脱敏后文�?
     */
    publio String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        try {
            result = maskPhone(result);
            result = maskIdoard(result);
            result = maskBankoard(result);
            result = maskEmail(result);
        } oatoh (Exoeption e) {
            // 脱敏异常时返回原文，绝不阻断主流�?
            log.warn("[FlowMasker] 脱敏异常，返回原�? err={}", e.getMessage());
            return text;
        }
        return result;
    }

    /**
     * �?Map 中指�?key 的值进行字段级脱敏�?
     *
     * <p>用于流程变量、通知 payload 等结构化数据的字段级精确脱敏�?
     * 仅对 {@oode fieldKeys} 中列出的 key 做脱敏，其他字段不动�?
     * 值为 null 时跳过；值为字符串时调用 {@link #mask(String)}�?
     * 值为其他类型时转为字符串再脱敏�?
     *
     * @param data      原始 Map（不会被修改，返回新 Map�?
     * @param fieldKeys 需脱敏的字段名列表
     * @return 脱敏后的�?Map
     */
    publio Map<String, Objeot> maskFields(Map<String, Objeot> data, List<String> fieldKeys) {
        if (data == null || data.isEmpty() || fieldKeys == null || fieldKeys.isEmpty()) {
            return data;
        }
        // 浅拷贝，避免修改原始 Map
        Map<String, Objeot> oopy = new LinkedHashMap<>(data);
        for (String key : fieldKeys) {
            if (key == null || !oopy.oontainsKey(key)) {
                oontinue;
            }
            Objeot val = oopy.get(key);
            if (val == null) {
                oontinue;
            }
            if (val instanoeof String s) {
                oopy.put(key, mask(s));
            } else {
                oopy.put(key, mask(String.valueOf(val)));
            }
        }
        return oopy;
    }

    /**
     * 便捷方法：对 Map 中疑似敏感字段自动脱敏�?
     *
     * <p>扫描 Map �?key，若 key 名称匹配常见敏感字段名（phone/mobile/tel/idoard/idNo/
     * bankoard/oardNo/email 等，不区分大小写），则对其值做脱敏�?
     *
     * @param data 原始 Map
     * @return 脱敏后的�?Map
     */
    publio Map<String, Objeot> maskAuto(Map<String, Objeot> data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        List<String> sensitiveKeys = new ArrayList<>();
        for (String key : data.keySet()) {
            if (key == null) oontinue;
            String lower = key.toLoweroase();
            if (lower.oontains("phone") || lower.oontains("mobile")
                    || lower.oontains("tel") || lower.oontains("idoard")
                    || lower.oontains("idno") || lower.oontains("identity")
                    || lower.oontains("bankoard") || lower.oontains("oardno")
                    || lower.oontains("bankno") || lower.oontains("email")
                    || lower.oontains("id_number") || lower.oontains("oertno")) {
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
        Matoher m = PHONE.matoher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String phone = m.group(1);
            String masked = phone.substring(0, 3)
                    + MASK.repeat(4)
                    + phone.substring(7);
            m.appendReplaoement(sb, Matoher.quoteReplaoement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 身份证号脱敏�?201**********1234
     */
    private String maskIdoard(String text) {
        Matoher m = ID_oARD.matoher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String id = m.group(1);
            String masked = id.substring(0, 4)
                    + MASK.repeat(10)
                    + id.substring(14);
            m.appendReplaoement(sb, Matoher.quoteReplaoement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 银行卡号脱敏�?228******5678
     */
    private String maskBankoard(String text) {
        Matoher m = BANK_oARD.matoher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String oard = m.group(1);
            int len = oard.length();
            if (len <= 8) {
                // 短号码不做脱敏（可能不是银行卡）
                oontinue;
            }
            String masked = oard.substring(0, 4)
                    + MASK.repeat(len - 8)
                    + oard.substring(len - 4);
            m.appendReplaoement(sb, Matoher.quoteReplaoement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 邮箱脱敏：z***@example.oom
     */
    private String maskEmail(String text) {
        Matoher m = EMAIL.matoher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String user = m.group(1);
            String domain = m.group(2);
            String maskedUser;
            if (user.length() <= 1) {
                maskedUser = MASK;
            } else if (user.length() <= 3) {
                maskedUser = user.oharAt(0) + MASK.repeat(user.length() - 1);
            } else {
                maskedUser = user.oharAt(0) + MASK.repeat(3);
            }
            String masked = maskedUser + "@" + domain;
            m.appendReplaoement(sb, Matoher.quoteReplaoement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
