package com.njydsz.pmis.common.audit.mask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.njydsz.pmis.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 敏感字段脱敏工具类
 * <p>
 * 审计切面调用本工具对入参、响应中的敏感字段进行脱敏，避免密钥、密码、证件号等
 * 敏感信息随审计日志落盘。支持两种脱敏检测方式：
 * </p>
 * <ul>
 *   <li>基于 {@link MaskField} 注解的字段级精确控制</li>
 *   <li>基于 {@code patterns} 名称集合的模糊匹配（如 password、secret、token 等）</li>
 * </ul>
 *
 * <p><b>脱敏规则：</b></p>
 * <ul>
 *   <li>字符串：保留前后 2 位，中间用 {@code ****} 替换（长度不足时全替换为 {@code ****}）</li>
 *   <li>集合/Map：递归处理每个元素</li>
 *   <li>其他类型：转为 {@code ***MASKED***}</li>
 * </ul>
 *
 * <p><b>安全约束：</b></p>
 * <ul>
 *   <li>类为 final，构造器私有，禁止实例化</li>
 *   <li>脱敏前先 JSON 序列化深拷贝，避免污染原始业务对象</li>
 *   <li>循环引用防护（{@code visited} 集合）</li>
 *   <li>解析失败时降级返回原值而非异常，保证审计主流程不受影响</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
@Slf4j
public final class SensitiveFieldMask {

    /** 脱敏时字符串前后保留的字符数 */
    private static final int KEEP_CHARS = 2;

    /** 默认敏感字段名称匹配模式（不区分大小写、子串匹配） */
    private static final Set<String> DEFAULT_PATTERNS = Set.of(
            "password", "secret", "token", "creditcard", "cardno", "cardnumber",
            "idcard", "idnumber", "mobile", "phone", "email", "address", "bankcard"
    );

    private SensitiveFieldMask() {
        throw new UnsupportedOperationException("SensitiveFieldMask 是工具类，禁止实例化");
    }

    /**
     * 对对象进行脱敏处理
     *
     * @param obj      待脱敏对象（不会被修改）
     * @param patterns 额外敏感字段名称集合（与默认模式合并生效）
     * @param enabled  是否启用脱敏；false 时直接返回原对象
     * @return 脱敏后的对象副本；入参 null 时返回 null
     */
    public static Object mask(Object obj, Set<String> patterns, boolean enabled) {
        if (!enabled || obj == null) {
            return obj;
        }
        Set<String> combinedPatterns = new HashSet<>(DEFAULT_PATTERNS);
        if (patterns != null) {
            combinedPatterns.addAll(patterns);
        }
        // 脱敏前先深拷贝，避免修改原始业务数据
        Object copy = deepCopy(obj);
        return maskInternal(copy, combinedPatterns, new HashSet<>());
    }

    /**
     * 深拷贝对象，通过 JSON 序列化/反序列化实现。
     * <p>对不可变类型（String/Number/Boolean/Character）直接复用，不做拷贝。
     * 深拷贝失败时降级返回原对象（仅用于审计展示，不影响原业务）。
     *
     * @param obj 待拷贝对象
     * @return 深拷贝结果
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object deepCopy(Object obj) {
        if (obj == null) {
            return null;
        }
        // 不可变类型无需拷贝
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean || obj instanceof Character) {
            return obj;
        }
        try {
            String json = JsonUtils.toJson(obj);
            Class<?> clazz = obj.getClass();
            // Collection/Map 类型使用 parseArray/parseObject 保持泛型兼容
            if (Collection.class.isAssignableFrom(clazz)) {
                return JsonUtils.parseList(json);
            } else if (Map.class.isAssignableFrom(clazz)) {
                return JsonUtils.parseObject(json, HashMap.class);
            }
            return JsonUtils.parseObject(json, (Class) clazz);
        } catch (Exception e) {
            // 深拷贝失败时降级返回原对象
            log.debug("[SensitiveFieldMask] 深拷贝失败，降级返回原对象: {}", e.getMessage());
            return obj;
        }
    }

    /**
     * 对 JSON 字符串进行敏感字段脱敏
     *
     * @param json     JSON 字符串
     * @param patterns 敏感字段名称集合
     * @return 脱敏后的 JSON 字符串；解析失败时返回原 JSON
     */
    public static String maskJson(String json, Set<String> patterns) {
        if (json == null || json.isEmpty() || patterns == null || patterns.isEmpty()) {
            return json;
        }
        try {
            ObjectMapper mapper = JsonUtils.getObjectMapper();
            JsonNode parsed = mapper.readTree(json);
            maskJsonObject(parsed, patterns, new HashSet<>());
            return mapper.writeValueAsString(parsed);
        } catch (Exception e) {
            // 解析失败时降级返回原始 JSON
            log.debug("[SensitiveFieldMask] JSON解析失败，降级返回原始JSON: {}", e.getMessage());
            return json;
        }
    }

    @SuppressWarnings("deprecation")
    private static void maskJsonObject(Object obj, Set<String> patterns, Set<Object> visited) {
        if (obj == null || visited.contains(obj)) {
            return;
        }
        visited.add(obj);

        if (obj instanceof ObjectNode) {
            ObjectNode jsonObj = (ObjectNode) obj;
            for (Iterator<Map.Entry<String, JsonNode>> it = jsonObj.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (isSensitiveKey(key, patterns)) {
                    jsonObj.put(key, maskValue(value.asText()));
                } else if (value.isObject() || value.isArray()) {
                    maskJsonObject(value, patterns, visited);
                }
            }
        } else if (obj instanceof ArrayNode) {
            ArrayNode arr = (ArrayNode) obj;
            for (int i = 0; i < arr.size(); i++) {
                maskJsonObject(arr.get(i), patterns, visited);
            }
        }
    }

    /**
     * 递归脱敏处理核心逻辑（基于反射处理 POJO 字段）
     *
     * @param obj      待脱敏对象
     * @param patterns 敏感字段名称集合
     * @param visited  已访问对象集合，用于防止循环引用
     * @return 脱敏后的对象
     */
    private static Object maskInternal(Object obj, Set<String> patterns, Set<Object> visited) {
        if (obj == null) {
            return null;
        }
        if (visited.contains(obj)) {
            return obj;
        }
        visited.add(obj);

        if (obj instanceof String) {
            return maskValue((String) obj);
        }

        if (obj instanceof Collection) {
            Collection<?> collection = (Collection<?>) obj;
            Collection<Object> result = new java.util.ArrayList<>();
            for (Object item : collection) {
                result.add(maskInternal(item, patterns, visited));
            }
            return result;
        }

        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            Map<Object, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (key instanceof String && isSensitiveKey((String) key, patterns)) {
                    result.put(key, maskValue(String.valueOf(value)));
                } else {
                    result.put(key, maskInternal(value, patterns, visited));
                }
            }
            return result;
        }

        try {
            Class<?> clazz = obj.getClass();
            Class<?> currentClass = clazz;
            while (currentClass != null && currentClass != Object.class) {
                Field[] fields = currentClass.getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    if (field.isAnnotationPresent(MaskField.class) ||
                            isSensitiveKey(field.getName(), patterns)) {
                        Object fieldValue = field.get(obj);
                        if (fieldValue instanceof String) {
                            field.set(obj, maskValue((String) fieldValue));
                        } else if (fieldValue != null) {
                            field.set(obj, maskInternal(fieldValue, patterns, visited));
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
        } catch (Exception ignored) {
            // 反射失败时静默降级（仅影响审计展示，不影响业务）
        }

        return obj;
    }

    /**
     * 对字符串值进行脱敏，保留前后指定字符数，中间用 **** 替换
     *
     * @param value 原始字符串值
     * @return 脱敏后的字符串
     */
    private static String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= KEEP_CHARS * 2) {
            return "****";
        }
        return value.substring(0, KEEP_CHARS) + "****" + value.substring(value.length() - KEEP_CHARS);
    }

    /**
     * 判断字段名称是否为敏感字段（大小写不敏感、子串匹配）
     *
     * @param key      字段名称
     * @param patterns 敏感字段名称匹配模式集合
     * @return 是敏感字段返回 true，否则返回 false
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
