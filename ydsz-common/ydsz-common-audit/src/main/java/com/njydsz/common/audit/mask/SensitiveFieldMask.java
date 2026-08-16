package com.njydsz.common.audit.mask;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.json.YdszJson;

/**
 * 敏感字段脱敏工具类
 *
 * <p>基于字段名称匹配的脱敏实现：当 JSON 中某个 key 命中敏感词列表时，
 * 对其 value 进行规则化替换。采用 Map 递归遍历，避免 JSON 树 API 和反射的复杂性。
 *
 * <p><b>脱敏规则：</b>
 * <ul>
 *   <li>字符串：根据字段名类型（手机号/邮箱/身份证/银行卡）选择特定策略，否则保留前后 2 位</li>
 *   <li>List/Map：递归处理每个元素</li>
 * </ul>
 *
 * <p><b>安全约束：</b></p>
 * <ul>
 *   <li>类为 final，构造器私有，禁止实例化</li>
 *   <li>解析失败时降级返回原 JSON，保证审计主流程不受影响</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class SensitiveFieldMask {

    /** 脱敏时字符串前后保留的字符数 */
    private static final int KEEP_CHARS = 2;

    /** 手机号脱敏：保留前 3 位和后 4 位 */
    private static final int PHONE_PREFIX = 3;
    private static final int PHONE_SUFFIX = 4;

    /** 身份证号脱敏：保留前 6 位和后 4 位 */
    private static final int IDCARD_PREFIX = 6;
    private static final int IDCARD_SUFFIX = 4;

    /** 银行卡号脱敏：保留前 4 位和后 4 位 */
    private static final int BANKCARD_PREFIX = 4;
    private static final int BANKCARD_SUFFIX = 4;

    /** 邮箱脱敏：本地部分最少保留字符数 */
    private static final int EMAIL_LOCAL_KEEP = 1;

    /** 默认敏感字段名称匹配模式（不区分大小写、子串匹配） */
    private static final Set<String> DEFAULT_PATTERNS = Set.of(
            // 认证凭据类
            "password", "secret", "token", "credential", "apikey", "apisecret",
            "privatekey", "publickey", "salt", "auth", "sessionid", "refreshtoken",
            // 个人信息类
            "creditcard", "cardno", "cardnumber", "bankcard", "cvv", "pin",
            "idcard", "idnumber", "mobile", "phone", "email", "address",
            // 其他敏感信息
            "passport", "license", "accountno", "accountnumber"
    );

    private SensitiveFieldMask() {
        throw new UnsupportedOperationException("SensitiveFieldMask 是工具类，禁止实例化");
    }

    /**
     * 对 JSON 字符串中的敏感字段进行脱敏处理
     *
     * <p>解析 JSON 为 Map 结构后递归遍历，命中敏感词列表的字段值将被替换。
     * 解析失败时降级返回原 JSON。</p>
     *
     * @param json     JSON 字符串
     * @param patterns 额外敏感字段名称集合（与默认模式合并生效）
     * @return 脱敏后的 JSON 字符串；解析失败时返回原 JSON
     */
    public static String maskJson(String json, Set<String> patterns) {
        if (json == null || json.isEmpty() || patterns == null || patterns.isEmpty()) {
            return json;
        }
        try {
            Set<String> combined = new HashSet<>(DEFAULT_PATTERNS);
            combined.addAll(patterns);
            // 解析 JSON 为 Map/List 结构
            Object parsed = YdszJson.fromJson(json, Object.class);
            Object masked = maskValue(parsed, combined);
            return YdszJson.toJson(masked);
        } catch (Exception e) {
            // 解析失败时降级返回原始 JSON
            log.debug("[SensitiveFieldMask] JSON解析失败，降级返回原始JSON: {}", e.getMessage());
            return json;
        }
    }

    /**
     * 递归脱敏处理：根据字段名或 Map key 匹配敏感词
     *
     * @param value    待处理值
     * @param patterns 敏感词集合
     * @return 脱敏后的值
     */
    @SuppressWarnings("unchecked")
    private static Object maskValue(Object value, Set<String> patterns) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return maskString(str);
        }
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> result = new HashMap<>(map.size());
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (isSensitiveKey(key, patterns)) {
                    result.put(key, maskString(String.valueOf(val), key));
                } else {
                    result.put(key, maskValue(val, patterns));
                }
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> maskValue(item, patterns))
                    .toList();
        }
        // 数字、布尔等不可变类型直接返回
        return value;
    }

    /**
     * 对字符串进行默认脱敏：保留前后 2 位
     *
     * @param value 原始字符串值
     * @return 脱敏后的字符串
     */
    private static String maskString(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= KEEP_CHARS * 2) {
            return "****";
        }
        return value.substring(0, KEEP_CHARS) + "****" + value.substring(value.length() - KEEP_CHARS);
    }

    /**
     * 根据字段名选择类型特定脱敏策略
     *
     * @param value 原始值
     * @param key   字段名
     * @return 脱敏后的字符串
     */
    private static String maskString(String value, String key) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String lowerKey = key.toLowerCase();

        // 手机号脱敏：保留前 3 后 4
        if (lowerKey.contains("mobile") || lowerKey.contains("phone")) {
            if (value.length() <= PHONE_PREFIX + PHONE_SUFFIX) {
                return "****";
            }
            return value.substring(0, PHONE_PREFIX) + "****" + value.substring(value.length() - PHONE_SUFFIX);
        }
        // 邮箱脱敏：本地部分仅保留首字符
        if (lowerKey.contains("email")) {
            int atIndex = value.indexOf('@');
            if (atIndex > 0) {
                String localPart = value.substring(0, atIndex);
                String domain = value.substring(atIndex);
                if (localPart.length() <= EMAIL_LOCAL_KEEP) {
                    return "*" + domain;
                }
                return localPart.charAt(0) + "***" + domain;
            }
        }
        // 身份证号脱敏：保留前 6 后 4
        if (lowerKey.contains("idcard") || lowerKey.contains("idnumber")) {
            if (value.length() <= IDCARD_PREFIX + IDCARD_SUFFIX) {
                return "****";
            }
            return value.substring(0, IDCARD_PREFIX) + "********" + value.substring(value.length() - IDCARD_SUFFIX);
        }
        // 银行卡号脱敏：保留前 4 后 4
        if (lowerKey.contains("bankcard") || lowerKey.contains("cardno") || lowerKey.contains("cardnumber")) {
            if (value.length() <= BANKCARD_PREFIX + BANKCARD_SUFFIX) {
                return "****";
            }
            return value.substring(0, BANKCARD_PREFIX) + "****" + value.substring(value.length() - BANKCARD_SUFFIX);
        }
        // 默认：保留前后 2 位
        return maskString(value);
    }

    /**
     * 判断字段名称是否为敏感字段（大小写不敏感、子串匹配）
     *
     * @param key      字段名称
     * @param patterns 敏感词集合
     * @return 命中返回 true
     */
    private static boolean isSensitiveKey(String key, Set<String> patterns) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String pattern : patterns) {
            if (lower.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
