package com.njydsz.common.json.schema;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * JSON Schema 验证器
 *
 * <p>验证 JSON 数据是否符合 Schema 定义，支持 JSON Schema Draft 07 的核心关键字，
 * 包括 allOf/anyOf/oneOf 组合关键字。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * JsonSchema schema = JsonSchema.object()
 *     .addProperty("name", JsonSchema.string().required())
 *     .addProperty("age", JsonSchema.integer().minimum(0).maximum(150));
 *
 * ValidationResult result = JsonSchemaValidator.validate(schema, jsonObject);
 * if (!result.isValid()) {
 *     System.err.println("验证失败：" + result.getErrors());
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public final class JsonSchemaValidator {

    private static final ConcurrentMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private JsonSchemaValidator() {
        throw new UnsupportedOperationException("JsonSchemaValidator is a utility class");
    }

    /**
     * 验证 JSON 数据
     */
    public static ValidationResult validate(JsonSchema schema, Object data) {
        if (schema == null) {
            return new ValidationResult(true);
        }

        // $ref 引用解析：如果当前 schema 有 $ref，用引用目标替代
        if (schema.getRef() != null) {
            JsonSchema resolved = schema.resolveRef();
            if (resolved != null) {
                return validate(resolved, data);
            }
        }

        ValidationResult result = new ValidationResult(true);

        // null 值检查
        if (data == null) {
            if ("null".equals(schema.getType())) {
                return result;
            } else if (schema.isRequired()) {
                result.addError("Required value is null");
                return result;
            } else {
                return result;
            }
        }

        // 类型检查
        validateType(schema, data, result, "");

        return result;
    }

    /**
     * 验证类型
     */
    private static void validateType(JsonSchema schema, Object data, ValidationResult result, String path) {
        String type = schema.getType();

        // 类型检查
        if (!matchesType(type, data)) {
            result.addError(path + ": Expected type '" + type + "' but got '" + getType(data) + "'");
            return;
        }

        // 枚举检查
        if (schema.getEnumValues() != null && !schema.getEnumValues().isEmpty()) {
            if (!schema.getEnumValues().contains(data)) {
                result.addError(path + ": Value must be one of " + schema.getEnumValues());
            }
        }

        // 根据类型进行特定验证
        switch (type) {
            case "string":
                validateString(schema, (String) data, result, path);
                break;
            case "number":
            case "integer":
                validateNumber(schema, (Number) data, result, path);
                break;
            case "array":
                validateArray(schema, (List<?>) data, result, path);
                break;
            case "object":
                validateObject(schema, (Map<?, ?>) data, result, path);
                break;
        }

        // 组合关键字验证（allOf / anyOf / oneOf）
        validateCombinators(schema, data, result, path);

        // const 关键字：数据必须等于固定值
        if (schema.getConstValue() != null) {
            if (!schema.getConstValue().equals(data)) {
                result.addError(path + ": Value must be " + schema.getConstValue() + " but got " + data);
            }
        }

        // not 关键字：数据不能匹配此 Schema
        if (schema.getNot() != null) {
            ValidationResult notResult = new ValidationResult(true);
            validateType(schema.getNot(), data, notResult, path + "/not");
            if (notResult.isValid()) {
                result.addError(path + ": Value must NOT match the 'not' schema");
            }
        }

        // if/then/else 条件关键字
        if (schema.getIfSchema() != null) {
            ValidationResult ifResult = new ValidationResult(true);
            validateType(schema.getIfSchema(), data, ifResult, path + "/if");
            if (ifResult.isValid()) {
                // 匹配 if 条件：必须匹配 then
                if (schema.getThenSchema() != null) {
                    validateType(schema.getThenSchema(), data, result, path + "/then");
                }
            } else {
                // 不匹配 if 条件：必须匹配 else
                if (schema.getElseSchema() != null) {
                    validateType(schema.getElseSchema(), data, result, path + "/else");
                }
            }
        }
    }

    /**
     * 验证组合关键字（allOf / anyOf / oneOf）。
     */
    private static void validateCombinators(JsonSchema schema, Object data, ValidationResult result, String path) {
        // allOf：所有 Schema 都必须验证通过
        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            for (int i = 0; i < schema.getAllOf().size(); i++) {
                ValidationResult subResult = new ValidationResult(true);
                validateType(schema.getAllOf().get(i), data, subResult, path + "/allOf[" + i + "]");
                if (!subResult.isValid()) {
                    result.addError(path + ": allOf[" + i + "] validation failed: " + subResult.getErrors());
                }
            }
        }

        // anyOf：至少一个 Schema 验证通过
        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
            boolean anyValid = false;
            for (int i = 0; i < schema.getAnyOf().size(); i++) {
                ValidationResult subResult = new ValidationResult(true);
                validateType(schema.getAnyOf().get(i), data, subResult, path);
                if (subResult.isValid()) {
                    anyValid = true;
                    break;
                }
            }
            if (!anyValid) {
                result.addError(path + ": Value does not match any of the anyOf schemas");
            }
        }

        // oneOf：恰好一个 Schema 验证通过
        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
            int matchCount = 0;
            for (JsonSchema subSchema : schema.getOneOf()) {
                ValidationResult subResult = new ValidationResult(true);
                validateType(subSchema, data, subResult, path);
                if (subResult.isValid()) {
                    matchCount++;
                }
            }
            if (matchCount == 0) {
                result.addError(path + ": Value does not match any of the oneOf schemas");
            } else if (matchCount > 1) {
                result.addError(path + ": Value matches " + matchCount + " of the oneOf schemas, expected exactly 1");
            }
        }
    }

    /**
     * 验证字符串
     */
    private static void validateString(JsonSchema schema, String str, ValidationResult result, String path) {
        // 最小长度
        if (schema.getMinLength() != null && str.length() < schema.getMinLength()) {
            result.addError(path + ": String length " + str.length() + " is less than minimum " + schema.getMinLength());
        }

        // 最大长度
        if (schema.getMaxLength() != null && str.length() > schema.getMaxLength()) {
            result.addError(path + ": String length " + str.length() + " is greater than maximum " + schema.getMaxLength());
        }

        // 正则表达式（带缓存）
        if (schema.getPattern() != null && !schema.getPattern().isEmpty()) {
            String patternStr = schema.getPattern();
            Pattern pattern = PATTERN_CACHE.computeIfAbsent(patternStr, Pattern::compile);
            if (!pattern.matcher(str).matches()) {
                result.addError(path + ": String does not match pattern '" + patternStr + "'");
            }
        }
    }

    /**
     * 验证数字
     */
    private static void validateNumber(JsonSchema schema, Number num, ValidationResult result, String path) {
        double value = num.doubleValue();

        // 最小值
        if (schema.getMinimum() != null && value < schema.getMinimum()) {
            result.addError(path + ": Value " + value + " is less than minimum " + schema.getMinimum());
        }

        // 最大值
        if (schema.getMaximum() != null && value > schema.getMaximum()) {
            result.addError(path + ": Value " + value + " is greater than maximum " + schema.getMaximum());
        }

        // 排除性最小值
        if (schema.getExclusiveMinimum() != null && value <= schema.getExclusiveMinimum()) {
            result.addError(path + ": Value " + value + " must be greater than " + schema.getExclusiveMinimum());
        }

        // 排除性最大值
        if (schema.getExclusiveMaximum() != null && value >= schema.getExclusiveMaximum()) {
            result.addError(path + ": Value " + value + " must be less than " + schema.getExclusiveMaximum());
        }

        // 倍数
        if (schema.getMultipleOf() != null) {
            double remainder = value % schema.getMultipleOf();
            if (remainder != 0 && Math.abs(remainder - schema.getMultipleOf()) > 1e-10) {
                result.addError(path + ": Value " + value + " is not a multiple of " + schema.getMultipleOf());
            }
        }
    }

    /**
     * 验证数组
     */
    private static void validateArray(JsonSchema schema, List<?> array, ValidationResult result, String path) {
        // 最小项数
        if (schema.getMinItems() != null && array.size() < schema.getMinItems()) {
            result.addError(path + ": Array size " + array.size() + " is less than minimum " + schema.getMinItems());
        }

        // 最大项数
        if (schema.getMaxItems() != null && array.size() > schema.getMaxItems()) {
            result.addError(path + ": Array size " + array.size() + " is greater than maximum " + schema.getMaxItems());
        }

        // uniqueItems 约束：数组中不能有重复元素
        if (Boolean.TRUE.equals(schema.getUniqueItems()) && array.size() > 1) {
            Set<Object> seen = new HashSet<>();
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (!seen.add(item)) {
                    result.addError(path + ": Duplicate item '" + item + "' at index " + i);
                    break;
                }
            }
        }

        // 验证数组项
        if (schema.getItems() != null) {
            JsonSchema itemSchema = schema.getItems();
            for (int i = 0; i < array.size(); i++) {
                validateType(itemSchema, array.get(i), result, path + "[" + i + "]");
            }
        }
    }

    /**
     * 验证对象
     */
    private static void validateObject(JsonSchema schema, Map<?, ?> obj, ValidationResult result, String path) {
        Map<String, JsonSchema> properties = schema.getProperties();
        List<String> required = schema.getRequiredProperties();
        JsonSchema additionalProperties = schema.getAdditionalProperties();

        // 检查最少属性数
        if (schema.getMinProperties() != null && obj.size() < schema.getMinProperties()) {
            result.addError(path + ": Object has " + obj.size() + " properties, minimum is " + schema.getMinProperties());
        }

        // 检查最多属性数
        if (schema.getMaxProperties() != null && obj.size() > schema.getMaxProperties()) {
            result.addError(path + ": Object has " + obj.size() + " properties, maximum is " + schema.getMaxProperties());
        }

        // 检查必填字段
        for (String requiredProp : required) {
            if (!obj.containsKey(requiredProp)) {
                result.addError(path + ": Missing required property '" + requiredProp + "'");
            }
        }

        // 验证每个属性
        Set<String> validatedProps = new HashSet<>();
        for (Object keyObj : obj.keySet()) {
            String key = (String) keyObj;
            Object value = obj.get(key);
            String propPath = path.isEmpty() ? key : path + "." + key;

            if (properties.containsKey(key)) {
                validateType(properties.get(key), value, result, propPath);
                validatedProps.add(key);
            } else if (additionalProperties != null) {
                validateType(additionalProperties, value, result, propPath);
            } else {
                // patternProperties 正则匹配
                validatePatternProperties(schema, key, value, result, propPath);
            }
        }
    }

    /**
     * 按 patternProperties 正则匹配并校验属性值。
     */
    private static void validatePatternProperties(JsonSchema schema, String key, Object value,
                                                   ValidationResult result, String path) {
        Map<String, JsonSchema> patternProps = schema.getPatternProperties();
        if (patternProps == null || patternProps.isEmpty()) {
            return;
        }
        for (Map.Entry<String, JsonSchema> entry : patternProps.entrySet()) {
            try {
                if (key.matches(entry.getKey())) {
                    validateType(entry.getValue(), value, result, path);
                    return;
                }
            } catch (java.util.regex.PatternSyntaxException ignored) {
                // 非法正则跳过
            }
        }
    }

    /**
     * 检查数据类型是否匹配
     */
    private static boolean matchesType(String type, Object data) {
        if (data == null) {
            return "null".equals(type);
        }

        switch (type) {
            case "string":
                return data instanceof String;
            case "number":
                return data instanceof Number;
            case "integer":
                return data instanceof Integer || data instanceof Long;
            case "boolean":
                return data instanceof Boolean;
            case "array":
                return data instanceof List;
            case "object":
                return data instanceof Map;
            case "null":
                return data == null;
            default:
                return false;
        }
    }

    /**
     * 获取数据的类型名称
     */
    private static String getType(Object data) {
        if (data == null) {
            return "null";
        } else if (data instanceof String) {
            return "string";
        } else if (data instanceof Number) {
            return "number";
        } else if (data instanceof Boolean) {
            return "boolean";
        } else if (data instanceof List) {
            return "array";
        } else if (data instanceof Map) {
            return "object";
        } else {
            return data.getClass().getSimpleName();
        }
    }
}
