package com.njydsz.common.json.schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * JSON Schema 校验器（Draft 07 核心子集）。
 *
 * <p>提供 {@link #validate(JsonSchema, Object)} 静态入口，对值进行类型/格式/范围/枚举等约束校验。
 * 对标 Everit JSON Schema Validator（JavaScript）与 networknt/json-schema-validator（Java）的轻量级替代。
 *
 * <p><b>覆盖的约束：</b></p>
 * <ul>
 *   <li>类型约束（string/integer/number/boolean/array/object/null）</li>
 *   <li>字符串：minLength/maxLength/pattern/format（date-time/email/uri/uuid/ipv4/hostname）</li>
 *   <li>数值：minimum/maximum/multipleOf</li>
 *   <li>数组：minItems/maxItems/uniqueItems/items</li>
 *   <li>对象：required/properties/additionalProperties/minProperties/maxProperties</li>
 *   <li>组合：enum/const</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * JsonSchema schema = JsonSchema.string().minLength(1).maxLength(100).build();
 * ValidationResult result = JsonSchemaValidator.validate(schema, "hello");
 * if (!result.isValid()) {
 *     log.warn("校验失败: {}", result.getErrors());
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see JsonSchema
 */
public final class JsonSchemaValidator {

    /** Email 正则（RFC 5322 简化子集） */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** URI 正则 */
    private static final Pattern URI_PATTERN = Pattern.compile(
            "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$", Pattern.CASE_INSENSITIVE);

    /** UUID 正则 */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

    /** IPv4 正则 */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    /** Hostname 正则 */
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z]{2,}$");

    private JsonSchemaValidator() {
    }

    /**
     * 校验值是否符合 Schema 约束。
     *
     * @param schema 要校验的 Schema（不可为 null）
     * @param value  要校验的值
     * @return 校验结果，通过返回 {@link ValidationResult#VALID}
     */
    public static ValidationResult validate(JsonSchema schema, Object value) {
        if (schema == null) {
            return ValidationResult.VALID;
        }
        ValidationResult result = new ValidationResult(true, new ArrayList<>());
        result = validateType(schema, value, result);
        if (value == null || "null".equals(schema.getType())) {
            return result;
        }
        result = validateEnum(schema, value, result);
        result = validateConst(schema, value, result);

        if ("string".equals(schema.getType())) {
            result = validateString(schema, value, result);
        } else if ("number".equals(schema.getType()) || "integer".equals(schema.getType())) {
            result = validateNumber(schema, value, result);
        } else if ("array".equals(schema.getType())) {
            result = validateArray(schema, value, result);
        } else if ("object".equals(schema.getType())) {
            result = validateObject(schema, value, result);
        }
        return result;
    }

    // -------- 类型校验 --------

    private static ValidationResult validateType(JsonSchema schema, Object value, ValidationResult result) {
        String type = schema.getType();
        if (type == null || "null".equals(type)) {
            return result;
        }
        boolean typeMatch = switch (type) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number && !(value instanceof Double d && (d.isNaN() || d.isInfinite()));
            case "integer" -> value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List;
            case "object" -> value instanceof Map;
            default -> true; // unknown type: skip
        };
        if (!typeMatch) {
            result = result.addError(String.format(
                    "类型不匹配: 期望 %s，实际 %s",
                    type, value.getClass().getSimpleName()));
        }
        return result;
    }

    // -------- 枚举与常量 --------

    private static ValidationResult validateEnum(JsonSchema schema, Object value, ValidationResult result) {
        List<Object> enumValues = schema.getEnumValues();
        if (enumValues == null || enumValues.isEmpty()) {
            return result;
        }
        boolean matched = false;
        for (Object ev : enumValues) {
            if (ev != null && ev.equals(value)) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            result = result.addError(String.format(
                    "值 '%s' 不在枚举范围内 %s", value, enumValues));
        }
        return result;
    }

    private static ValidationResult validateConst(JsonSchema schema, Object value, ValidationResult result) {
        Object constValue = schema.getConstValue();
        if (constValue == null) {
            return result;
        }
        if (!constValue.equals(value)) {
            result = result.addError(String.format(
                    "常量不匹配: 期望 '%s'，实际 '%s'", constValue, value));
        }
        return result;
    }

    // -------- 字符串约束 --------

    private static ValidationResult validateString(JsonSchema schema, Object value, ValidationResult result) {
        if (!(value instanceof String strValue)) {
            return result;
        }

        // minLength
        Integer minLength = schema.getMinLength();
        if (minLength != null && strValue.length() < minLength) {
            result = result.addError(String.format(
                    "字符串长度不足: 最小 %d，实际 %d", minLength, strValue.length()));
        }

        // maxLength
        Integer maxLength = schema.getMaxLength();
        if (maxLength != null && strValue.length() > maxLength) {
            result = result.addError(String.format(
                    "字符串超长: 最大 %d，实际 %d", maxLength, strValue.length()));
        }

        // pattern
        Pattern pattern = schema.getPattern();
        if (pattern != null && !pattern.matcher(strValue).matches()) {
            result = result.addError(String.format(
                    "字符串不匹配正则 '%s': %s", pattern.pattern(), truncate(strValue)));
        }

        // format
        String format = schema.getFormat();
        if (format != null) {
            result = validateFormat(strValue, format, result);
        }
        return result;
    }

    private static ValidationResult validateFormat(String value, String format, ValidationResult result) {
        boolean valid = switch (format) {
            case "date-time" -> value.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
            case "email" -> EMAIL_PATTERN.matcher(value).matches();
            case "uri" -> URI_PATTERN.matcher(value).matches();
            case "uuid" -> UUID_PATTERN.matcher(value).matches();
            case "ipv4" -> IPV4_PATTERN.matcher(value).matches();
            case "hostname" -> HOSTNAME_PATTERN.matcher(value).matches();
            default -> true; // unknown format: skip
        };
        if (!valid) {
            result = result.addError(String.format("格式校验失败: 期望 %s，实际 '%s'", format, truncate(value)));
        }
        return result;
    }

    // -------- 数值约束 --------

    private static ValidationResult validateNumber(JsonSchema schema, Object value, ValidationResult result) {
        if (!(value instanceof Number numValue)) {
            return result;
        }
        double d = numValue.doubleValue();

        // integer 类型必须是整数
        if ("integer".equals(schema.getType()) && d != Math.rint(d)) {
            result = result.addError(String.format("整数类型要求小数部分为 0: %s", value));
        }

        // minimum
        Number minimum = schema.getMinimum();
        if (minimum != null) {
            boolean exclusive = Boolean.TRUE.equals(schema.getExclusiveMinimum());
            if (exclusive && d <= minimum.doubleValue()) {
                result = result.addError(String.format("值必须 > %s: %s", minimum, value));
            } else if (!exclusive && d < minimum.doubleValue()) {
                result = result.addError(String.format("值必须 >= %s: %s", minimum, value));
            }
        }

        // maximum
        Number maximum = schema.getMaximum();
        if (maximum != null) {
            boolean exclusive = Boolean.TRUE.equals(schema.getExclusiveMaximum());
            if (exclusive && d >= maximum.doubleValue()) {
                result = result.addError(String.format("值必须 < %s: %s", maximum, value));
            } else if (!exclusive && d > maximum.doubleValue()) {
                result = result.addError(String.format("值必须 <= %s: %s", maximum, value));
            }
        }

        // multipleOf
        Number multipleOf = schema.getMultipleOf();
        if (multipleOf != null && multipleOf.doubleValue() > 0) {
            double remainder = d % multipleOf.doubleValue();
            if (Math.abs(remainder) > 1e-10) {
                result = result.addError(String.format("值必须是 %s 的倍数: %s", multipleOf, value));
            }
        }
        return result;
    }

    // -------- 数组约束 --------

    private static ValidationResult validateArray(JsonSchema schema, Object value, ValidationResult result) {
        if (!(value instanceof List<?> listValue)) {
            return result;
        }

        // minItems
        Integer minItems = schema.getMinItems();
        if (minItems != null && listValue.size() < minItems) {
            result = result.addError(String.format(
                    "数组长度不足: 最小 %d，实际 %d", minItems, listValue.size()));
        }

        // maxItems
        Integer maxItems = schema.getMaxItems();
        if (maxItems != null && listValue.size() > maxItems) {
            result = result.addError(String.format(
                    "数组超长: 最大 %d，实际 %d", maxItems, listValue.size()));
        }

        // uniqueItems
        if (Boolean.TRUE.equals(schema.getUniqueItems())) {
            Set<Object> seen = new HashSet<>();
            for (Object item : listValue) {
                if (!seen.add(item)) {
                    result = result.addError(String.format(
                            "数组要求唯一元素，存在重复: %s", item));
                    break;
                }
            }
        }

        // items
        JsonSchema itemsSchema = schema.getItems();
        if (itemsSchema != null) {
            for (int i = 0; i < listValue.size(); i++) {
                ValidationResult itemResult = validate(itemsSchema, listValue.get(i));
                if (itemResult.hasErrors()) {
                    for (String err : itemResult.getErrors()) {
                        result = result.addError(String.format("数组元素 [%d]: %s", i, err));
                    }
                }
            }
        }
        return result;
    }

    // -------- 对象约束 --------

    private static ValidationResult validateObject(JsonSchema schema, Object value, ValidationResult result) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            return result;
        }

        // minProperties
        Integer minProperties = schema.getMinProperties();
        if (minProperties != null && mapValue.size() < minProperties) {
            result = result.addError(String.format(
                    "对象字段数不足: 最小 %d，实际 %d", minProperties, mapValue.size()));
        }

        // maxProperties
        Integer maxProperties = schema.getMaxProperties();
        if (maxProperties != null && mapValue.size() > maxProperties) {
            result = result.addError(String.format(
                    "对象字段过多: 最大 %d，实际 %d", maxProperties, mapValue.size()));
        }

        // required
        List<String> required = schema.getRequired();
        if (required != null) {
            for (String field : required) {
                if (!mapValue.containsKey(field)) {
                    result = result.addError(String.format("缺少必填字段: '%s'", field));
                }
            }
        }

        // properties
        Map<String, JsonSchema> properties = schema.getProperties();
        if (properties != null) {
            for (Map.Entry<String, JsonSchema> entry : properties.entrySet()) {
                if (mapValue.containsKey(entry.getKey())) {
                    ValidationResult propResult = validate(entry.getValue(), mapValue.get(entry.getKey()));
                    if (propResult.hasErrors()) {
                        for (String err : propResult.getErrors()) {
                            result = result.addError(String.format("字段 '%s': %s", entry.getKey(), err));
                        }
                    }
                }
            }
        }

        // additionalProperties = false 时禁止额外字段
        if (Boolean.FALSE.equals(schema.getAdditionalProperties())) {
            Map<String, JsonSchema> knownProps = schema.getProperties();
            if (knownProps != null) {
                for (Object key : mapValue.keySet()) {
                    if (!knownProps.containsKey(key)) {
                        result = result.addError(String.format(
                                "不允许的额外字段: '%s'", key));
                    }
                }
            }
        }
        return result;
    }

    // -------- 工具方法 --------

    private static String truncate(String value) {
        if (value.length() <= 50) {
            return value;
        }
        return value.substring(0, 50) + "...(truncated)";
    }
}
