package com.njydsz.common.json.schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.njydsz.common.json.annotation.Experimental;
import com.njydsz.common.json.YdszJson;

/**
 * JSON Schema 校验器（Draft 07 核心子集）。
 *
 * <p>提供静态 {@link #validate(JsonSchema, Object)} 方法，对给定数据执行 Schema 校验。
 * 支持的关键字：
 * <ul>
 *   <li><b>类型</b>：type（string / number / integer / boolean / array / object / null）</li>
 *   <li><b>字符串约束</b>：minLength / maxLength / pattern / format（date-time / date / time /
 *       email / uri / uuid / hostname / ipv4 / ipv6 / regex）</li>
 *   <li><b>数值约束</b>：minimum / maximum / exclusiveMinimum / exclusiveMaximum / multipleOf</li>
 *   <li><b>数组约束</b>：items / minItems / maxItems / uniqueItems</li>
 *   <li><b>对象约束</b>：properties / required / additionalProperties / minProperties /
 *       maxProperties / patternProperties / dependentRequired / dependentSchemas</li>
 *   <li><b>组合关键字</b>：allOf / anyOf / oneOf / not</li>
 *   <li><b>条件关键字</b>：if / then / else</li>
 *   <li><b>常量</b>：const</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * JsonSchema schema = JsonSchema.object()
 *     .addProperty("name", JsonSchema.string().required().minLength(1))
 *     .addProperty("age", JsonSchema.integer().minimum(0));
 * ValidationResult result = JsonSchemaValidator.validate(schema, data);
 * if (!result.isValid()) {
 *     System.err.println("验证失败：" + result.getErrors());
 * }
 * }</pre>
 *
 * <p><b>线程安全：</b>本方法无状态，线程安全。内部使用 {@link ConcurrentMap} 缓存已编译正则。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonSchema JSON Schema 定义
 * @see ValidationResult 校验结果
 */
@Experimental
public final class JsonSchemaValidator {

    private static final ConcurrentMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private JsonSchemaValidator() {
    }

    /**
     * 对给定数据执行 JSON Schema 校验。
     *
     * <p>Schema 为 null 时返回通过结果；数据为 null 时按 null 类型校验。
     *
     * @param schema JSON Schema 定义（为 null 时返回通过）
     * @param data   待校验数据（任意类型）
     * @return 校验结果（永不为 null）
     */
    public static ValidationResult validate(JsonSchema schema, Object data) {
        if (schema == null) {
            return new ValidationResult(true);
        }
        ValidationResult result = new ValidationResult(true);
        validateType(schema, data, result, "$");
        return result;
    }

    private static void validateType(JsonSchema schema, Object data, ValidationResult result, String path) {
        if (data == null) {
            if ("null".equals(schema.getType())) {
                return;
            }
            if (schema.isRequired()) {
                result.addError(path + " 是必填字段，不能为 null");
            }
            return;
        }

        if (!matchesType(schema.getType(), data)) {
            result.addError(path + " 类型不匹配：期望 " + schema.getType() + "，实际 " + getType(data));
            return;
        }

        validateString(schema, data, result, path);
        validateNumber(schema, data, result, path);
        validateArray(schema, data, result, path);
        validateObject(schema, data, result, path);

        if (schema.getEnumValues() != null) {
            boolean matched = false;
            for (Object ev : schema.getEnumValues()) {
                if (ev != null && ev.equals(data)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                result.addError(path + " 值不在允许范围内：" + schema.getEnumValues());
            }
        }

        if (schema.getConstValue() != null && !schema.getConstValue().equals(data)) {
            result.addError(path + " 值必须为常量：" + schema.getConstValue());
        }

        validateCombinators(schema, data, result, path);
    }

    @SuppressWarnings("unchecked")
    private static void validateCombinators(JsonSchema schema, Object data, ValidationResult result, String path) {
        if (schema.getAllOf() != null) {
            for (int i = 0; i < schema.getAllOf().size(); i++) {
                ValidationResult sub = new ValidationResult(true);
                JsonSchema subSchema = schema.getAllOf().get(i);
                validateType(subSchema, data, sub, path + ".allOf[" + i + "]");
                if (sub.hasErrors()) {
                    result.addError(path + " 不满足 allOf[" + i + "]：" + sub.getErrors().get(0));
                }
            }
        }

        if (schema.getAnyOf() != null) {
            boolean anyMatched = false;
            for (int i = 0; i < schema.getAnyOf().size(); i++) {
                ValidationResult sub = new ValidationResult(true);
                JsonSchema subSchema = schema.getAnyOf().get(i);
                validateType(subSchema, data, sub, path + ".anyOf[" + i + "]");
                if (sub.isValid()) {
                    anyMatched = true;
                    break;
                }
            }
            if (!anyMatched) {
                result.addError(path + " 不满足任意一个 anyOf 条件");
            }
        }

        if (schema.getOneOf() != null) {
            int matchCount = 0;
            for (int i = 0; i < schema.getOneOf().size(); i++) {
                ValidationResult sub = new ValidationResult(true);
                JsonSchema subSchema = schema.getOneOf().get(i);
                validateType(subSchema, data, sub, path + ".oneOf[" + i + "]");
                if (sub.isValid()) {
                    matchCount++;
                }
            }
            if (matchCount != 1) {
                result.addError(path + " 必须满足且仅满足一个 oneOf 条件（当前匹配 " + matchCount + " 个）");
            }
        }

        if (schema.getNot() != null) {
            ValidationResult sub = new ValidationResult(true);
            validateType(schema.getNot(), data, sub, path + ".not");
            if (sub.isValid()) {
                result.addError(path + " 不应满足 not 条件");
            }
        }

        if (schema.getIfSchema() != null && data instanceof Map) {
            ValidationResult ifResult = new ValidationResult(true);
            validateType(schema.getIfSchema(), data, ifResult, path + ".if");
            if (ifResult.isValid() && schema.getThenSchema() != null) {
                validateType(schema.getThenSchema(), data, result, path + ".then");
            } else if (!ifResult.isValid() && schema.getElseSchema() != null) {
                validateType(schema.getElseSchema(), data, result, path + ".else");
            }
        }
    }

    private static void validateString(JsonSchema schema, Object data, ValidationResult result, String path) {
        if (!(data instanceof String)) {
            return;
        }
        String str = (String) data;
        if ("string".equals(schema.getType())) {
            if (schema.getMinLength() != null && str.length() < schema.getMinLength()) {
                result.addError(path + " 字符串长度不能小于 " + schema.getMinLength() + "（当前 " + str.length() + "）");
            }
            if (schema.getMaxLength() != null && str.length() > schema.getMaxLength()) {
                result.addError(path + " 字符串长度不能大于 " + schema.getMaxLength() + "（当前 " + str.length() + "）");
            }
            if (schema.getPattern() != null) {
                Pattern p = PATTERN_CACHE.computeIfAbsent(schema.getPattern(), k -> {
                    try {
                        return Pattern.compile(k);
                    } catch (PatternSyntaxException e) {
                        return null;
                    }
                });
                if (p == null) {
                    result.addError(path + " Schema pattern 编译失败：" + schema.getPattern());
                } else if (!p.matcher(str).matches()) {
                    result.addError(path + " 字符串不匹配模式：" + schema.getPattern());
                }
            }
            if (schema.getFormat() != null) {
                validateFormat(str, schema.getFormat(), result, path);
            }
        }
    }

    private static void validateFormat(String value, String format, ValidationResult result, String path) {
        boolean valid;
        switch (format) {
            case "date-time":
                valid = isValidDateTime(value);
                break;
            case "date":
                valid = isValidDate(value);
                break;
            case "time":
                valid = isValidTime(value);
                break;
            case "email":
                valid = isValidEmail(value);
                break;
            case "uri":
            case "url":
                valid = isValidUri(value);
                break;
            case "uuid":
                valid = isValidUuid(value);
                break;
            case "hostname":
                valid = isValidHostname(value);
                break;
            case "ipv4":
                valid = isValidIpv4(value);
                break;
            case "ipv6":
                valid = isValidIpv6(value);
                break;
            case "regex":
                valid = isValidRegex(value);
                break;
            default:
                return;
        }
        if (!valid) {
            result.addError(path + " 不符合 " + format + " 格式");
        }
    }

    private static boolean isValidDateTime(String value) {
        return value.matches("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?$");
    }

    private static boolean isValidDate(String value) {
        return value.matches("^\\d{4}-\\d{2}-\\d{2}$");
    }

    private static boolean isValidTime(String value) {
        return value.matches("^\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$");
    }

    private static boolean isValidEmail(String value) {
        return value.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }

    private static boolean isValidUri(String value) {
        return value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.+$");
    }

    private static boolean isValidUuid(String value) {
        return value.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    private static boolean isValidHostname(String value) {
        return value.matches("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*$");
    }

    private static boolean isValidIpv4(String value) {
        return value.matches("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");
    }

    private static boolean isValidIpv6(String value) {
        return value.matches("^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$") || value.contains("::");
    }

    private static boolean isValidRegex(String value) {
        try {
            Pattern.compile(value);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateNumber(JsonSchema schema, Object data, ValidationResult result, String path) {
        if (data instanceof Integer || data instanceof Long || data instanceof BigDecimal
                || data instanceof Double || data instanceof Float) {
            double value;
            if (data instanceof BigDecimal) {
                value = ((BigDecimal) data).doubleValue();
            } else if (data instanceof Double) {
                value = (Double) data;
            } else if (data instanceof Float) {
                value = ((Float) data).doubleValue();
            } else {
                value = ((Number) data).longValue();
            }

            if ("integer".equals(schema.getType()) && data instanceof Double || data instanceof Float) {
                if (value != Math.rint(value)) {
                    result.addError(path + " 类型不匹配：期望 integer，实际为浮点数");
                    return;
                }
            }

            if (schema.getMinimum() != null && value < schema.getMinimum()) {
                result.addError(path + " 值不能小于 " + schema.getMinimum());
            }
            if (schema.getMaximum() != null && value > schema.getMaximum()) {
                result.addError(path + " 值不能大于 " + schema.getMaximum());
            }
            if (schema.getExclusiveMinimum() != null && value <= schema.getExclusiveMinimum()) {
                result.addError(path + " 值必须大于 " + schema.getExclusiveMinimum());
            }
            if (schema.getExclusiveMaximum() != null && value >= schema.getExclusiveMaximum()) {
                result.addError(path + " 值必须小于 " + schema.getExclusiveMaximum());
            }
            if (schema.getMultipleOf() != null && schema.getMultipleOf() > 0) {
                double remainder = value % schema.getMultipleOf();
                if (Math.abs(remainder) > 1e-10 && Math.abs(remainder - schema.getMultipleOf()) > 1e-10) {
                    result.addError(path + " 值必须是 " + schema.getMultipleOf() + " 的倍数");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateArray(JsonSchema schema, Object data, ValidationResult result, String path) {
        if (!(data instanceof List)) {
            return;
        }
        List<Object> list = (List<Object>) data;
        if (schema.getMinItems() != null && list.size() < schema.getMinItems()) {
            result.addError(path + " 数组长度不能小于 " + schema.getMinItems() + "（当前 " + list.size() + "）");
        }
        if (schema.getMaxItems() != null && list.size() > schema.getMaxItems()) {
            result.addError(path + " 数组长度不能大于 " + schema.getMaxItems() + "（当前 " + list.size() + "）");
        }
        if (schema.getItems() != null) {
            for (int i = 0; i < list.size(); i++) {
                ValidationResult itemResult = new ValidationResult(true);
                validateType(schema.getItems(), list.get(i), itemResult, path + "[" + i + "]");
                if (itemResult.hasErrors()) {
                    for (String err : itemResult.getErrors()) {
                        result.addError(err);
                    }
                }
            }
        }
        if (schema.getUniqueItems() != null && schema.getUniqueItems()) {
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    if (list.get(i) != null && list.get(i).equals(list.get(j))) {
                        result.addError(path + " 数组元素必须唯一（索引 " + i + " 与 " + j + " 重复）");
                        return;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateObject(JsonSchema schema, Object data, ValidationResult result, String path) {
        if (!(data instanceof Map)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) data;

        if (schema.getMinProperties() != null && map.size() < schema.getMinProperties()) {
            result.addError(path + " 对象属性数不能小于 " + schema.getMinProperties());
        }
        if (schema.getMaxProperties() != null && map.size() > schema.getMaxProperties()) {
            result.addError(path + " 对象属性数不能大于 " + schema.getMaxProperties());
        }

        if (schema.getRequiredProperties() != null) {
            for (String req : schema.getRequiredProperties()) {
                if (!map.containsKey(req) || map.get(req) == null) {
                    result.addError(path + " 缺少必填属性：" + req);
                }
            }
        }

        if (schema.getProperties() != null) {
            for (Map.Entry<String, JsonSchema> entry : schema.getProperties().entrySet()) {
                String propName = entry.getKey();
                JsonSchema propSchema = entry.getValue();
                if (map.containsKey(propName)) {
                    Object propValue = map.get(propName);
                    ValidationResult propResult = new ValidationResult(true);
                    validateType(propSchema, propValue, propResult, path + "." + propName);
                    if (propResult.hasErrors()) {
                        for (String err : propResult.getErrors()) {
                            result.addError(err);
                        }
                    }
                } else if (propSchema.isRequired()) {
                    result.addError(path + " 缺少必填属性：" + propName);
                }
            }
        }

        if (schema.getPatternProperties() != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                validatePatternProperties(schema, entry.getKey(), entry.getValue(), result, path);
            }
        }

        if (Boolean.FALSE.equals(schema.getAdditionalProperties()) && schema.getProperties() != null) {
            for (String key : map.keySet()) {
                if (!schema.getProperties().containsKey(key)) {
                    boolean matched = validatePatternProperties(schema, key, map.get(key), result, path);
                    if (!matched) {
                        result.addError(path + " 不允许额外属性：" + key);
                    }
                }
            }
        }

        if (schema.getDependentRequired() != null && schema.getProperties() != null) {
            for (Map.Entry<String, List<String>> entry : schema.getDependentRequired().entrySet()) {
                String prop = entry.getKey();
                if (map.containsKey(prop) && map.get(prop) != null) {
                    for (String dep : entry.getValue()) {
                        if (!map.containsKey(dep) || map.get(dep) == null) {
                            result.addError(path + " 属性 " + prop + " 存在时，" + dep + " 也必须存在");
                        }
                    }
                }
            }
        }
    }

    private static boolean validatePatternProperties(JsonSchema schema, String key, Object value,
                                                      ValidationResult result, String path) {
        if (schema.getPatternProperties() == null) {
            return false;
        }
        boolean matched = false;
        for (Map.Entry<String, JsonSchema> entry : schema.getPatternProperties().entrySet()) {
            try {
                if (Pattern.matches(entry.getKey(), key)) {
                    matched = true;
                    ValidationResult subResult = new ValidationResult(true);
                    validateType(entry.getValue(), value, subResult, path + "." + key);
                    if (subResult.hasErrors()) {
                        for (String err : subResult.getErrors()) {
                            result.addError(err);
                        }
                    }
                }
            } catch (PatternSyntaxException e) {
                result.addError(path + " patternProperties 正则编译失败：" + entry.getKey());
            }
        }
        return matched;
    }

    private static boolean matchesType(String expectedType, Object data) {
        if ("any".equals(expectedType)) {
            return true;
        }
        String actualType = getType(data);
        if (expectedType == null) {
            return true;
        }
        return expectedType.equals(actualType);
    }

    private static String getType(Object data) {
        if (data == null) {
            return "null";
        }
        if (data instanceof Boolean) {
            return "boolean";
        }
        if (data instanceof Integer || data instanceof Long || data instanceof Short || data instanceof Byte) {
            return "integer";
        }
        if (data instanceof Double || data instanceof Float || data instanceof BigDecimal) {
            return "number";
        }
        if (data instanceof String) {
            return "string";
        }
        if (data instanceof List) {
            return "array";
        }
        if (data instanceof Map) {
            return "object";
        }
        return data.getClass().getSimpleName().toLowerCase();
    }
}
