package com.njydsz.common.json.schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.ObjectNode;

/**
 * JSON Schema 校验器（Draft-07 子集）。
 *
 * <p>对标 Everit/NetworkNT JSON Schema Validator，实现业务高频关键字：
 * {@code type}/{@code required}/{@code properties}/{@code minimum}/{@code maximum}/
 * {@code minLength}/{@code maxLength}/{@code pattern}/{@code enum}/{@code nullable}。</p>
 *
 * <p><b>适用场景：</b></p>
 * <ul>
 *   <li>接口入参轻量校验（替代部分 Bean Validation @NotNull/@Size 场景）</li>
 *   <li>配置中心下发配置的格式校验</li>
 *   <li>BFF 层对第三方 API 响应的结构校验</li>
 * </ul>
 *
 * <p><b>暂不支持：</b>{@code $ref}、{@code allOf}/{@code oneOf}、{@code if/then}、
 * {@code format}（可用 enum+pattern 组合替代）、{@code items} 数组逐项校验——
 * 若需完整 Draft-07 支持，建议引入 networknt/json-schema-validator 依赖。</p>
 *
 * <p><b>与主流实现对比：</b></p>
 * <ul>
 *   <li>对标 Everit：纯 POJO 解析无额外依赖，原生支持 YdszJson 树模型</li>
 *   <li>对标 NetworkNT：校验结果返回 {@link ValidationResult} 结构化对象而非抛异常</li>
 *   <li>对标 FastJSON2 JSONSchema：与 ydsz-common-json 序列化引擎无缝集成，无跨引擎转换开销</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.1
 */
public final class JsonSchemaValidator {

    private JsonSchemaValidator() {
        throw new UnsupportedOperationException();
    }

    /**
     * 校验 JSON 字符串是否满足 Schema 定义。
     *
     * @param json   待校验的 JSON 字符串
     * @param schema JSON Schema 字符串（Draft-07 子集）
     * @return 校验结果，永不为 null
     */
    public static ValidationResult validate(String json, String schema) {
        try {
            JsonNode dataNode = YdszJson.readTree(json);
            JsonNode schemaNode = YdszJson.readTree(schema);
            return validate(dataNode, schemaNode);
        } catch (Exception e) {
            List<String> errors = new ArrayList<>(1);
            errors.add("JSON 解析失败：" + e.getMessage());
            return ValidationResult.failure(errors);
        }
    }

    /**
     * 校验 JsonNode 是否满足 Schema 定义。
     *
     * @param data   待校验的 JSON 数据节点
     * @param schema Schema 节点（必须是 ObjectNode，包含 type/properties 等关键字）
     * @return 校验结果，永不为 null
     */
    public static ValidationResult validate(JsonNode data, JsonNode schema) {
        List<String> errors = new ArrayList<>();
        validateNode(data, schema, "$", errors);
        if (errors.isEmpty()) {
            return ValidationResult.success();
        }
        return ValidationResult.failure(errors);
    }

    /**
     * 递归校验节点。
     *
     * @param data   数据节点
     * @param schema Schema 片段
     * @param path   当前 JSON 路径（用于错误定位，如 {@code "$.user.age"}）
     * @param errors 错误收集器
     */
    private static void validateNode(JsonNode data, JsonNode schema, String path, List<String> errors) {
        if (schema == null || schema.isNull()) {
            return; // 空 schema 表示不限制
        }

        if (!(schema instanceof ObjectNode)) {
            errors.add(path + "：schema 片段必须是 JSON 对象");
            return;
        }

        ObjectNode schemaObj = (ObjectNode) schema;

        // 1. nullable 处理：若允许 null 且 data 为 null，跳过后续校验
        JsonNode nullableNode = schemaObj.get("nullable");
        boolean nullable = nullableNode != null && nullableNode.asBoolean(false);
        if (data == null || data.isNull()) {
            if (nullable) {
                return;
            }
            // null 校验会在 type 检查中捕获
        }

        // 2. type 校验
        JsonNode typeNode = schemaObj.get("type");
        if (typeNode != null) {
            String expectedType = typeNode.asText();
            if (nullable && data != null && data.isNull()) {
                // nullable=true 且数据为 null，跳过 type 校验
            } else if (!matchType(data, expectedType)) {
                errors.add(path + "：类型不匹配，期望 " + expectedType
                        + "，实际 " + getActualType(data));
                return;
            }
        }

        // 3. enum 校验
        JsonNode enumNode = schemaObj.get("enum");
        if (enumNode != null && enumNode.isArray()) {
            boolean matched = false;
            Iterator<JsonNode> enumValues = ((ArrayNode) enumNode).elements();
            while (!matched && enumValues.hasNext()) {
                matched = enumValues.next().equals(data);
            }
            if (!matched) {
                errors.add(path + "：值不在枚举范围内，当前值=" + data.asText());
            }
        }

        // 4. 类型专属校验
        if (data != null && !data.isNull()) {
            if (data.isNumber()) {
                validateNumber(data, schemaObj, path, errors);
            } else if (data.isTextual()) {
                validateString(data, schemaObj, path, errors);
            } else if (data instanceof ObjectNode) {
                validateObject(data, schemaObj, path, errors);
            }
        }
    }

    /**
     * 字符串类型校验（minLength/maxLength/pattern）。
     */
    private static void validateString(JsonNode data, ObjectNode schemaObj,
                                       String path, List<String> errors) {
        String text = data.asText();
        int length = text.length();

        JsonNode minLenNode = schemaObj.get("minLength");
        if (minLenNode != null && minLenNode.isNumber()) {
            int minLen = minLenNode.asInt();
            if (length < minLen) {
                errors.add(path + "：字符串长度 " + length + " 小于最小长度 " + minLen);
            }
        }

        JsonNode maxLenNode = schemaObj.get("maxLength");
        if (maxLenNode != null && maxLenNode.isNumber()) {
            int maxLen = maxLenNode.asInt();
            if (length > maxLen) {
                errors.add(path + "：字符串长度 " + length + " 超过最大长度 " + maxLen);
            }
        }

        JsonNode patternNode = schemaObj.get("pattern");
        if (patternNode != null && patternNode.isTextual()) {
            String regex = patternNode.asText();
            if (!Pattern.matches(regex, text)) {
                errors.add(path + "：字符串 \"" + text + "\" 不匹配正则 /" + regex + "/");
            }
        }
    }

    /**
     * 数值类型校验（minimum/maximum）。
     */
    private static void validateNumber(JsonNode data, ObjectNode schemaObj,
                                       String path, List<String> errors) {
        double value = data.asDouble();

        JsonNode minNode = schemaObj.get("minimum");
        if (minNode != null && minNode.isNumber()) {
            if (value < minNode.asDouble()) {
                errors.add(path + "：数值 " + value + " 小于最小值 " + minNode.asDouble());
            }
        }

        JsonNode maxNode = schemaObj.get("maximum");
        if (maxNode != null && maxNode.isNumber()) {
            if (value > maxNode.asDouble()) {
                errors.add(path + "：数值 " + value + " 超过最大值 " + maxNode.asDouble());
            }
        }
    }

    /**
     * 对象类型校验（required/properties）。
     */
    private static void validateObject(JsonNode data, ObjectNode schemaObj,
                                       String path, List<String> errors) {
        ObjectNode dataObj = (ObjectNode) data;

        // required 校验
        JsonNode requiredNode = schemaObj.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            Iterator<JsonNode> requiredFields = ((ArrayNode) requiredNode).elements();
            while (requiredFields.hasNext()) {
                String fieldName = requiredFields.next().asText();
                if (!dataObj.has(fieldName)) {
                    errors.add(path + "：必填字段 \"" + fieldName + "\" 缺失");
                }
            }
        }

        // properties 递归校验
        JsonNode propertiesNode = schemaObj.get("properties");
        if (propertiesNode instanceof ObjectNode) {
            ObjectNode properties = (ObjectNode) propertiesNode;
            for (Map.Entry<String, JsonNode> entry : properties.entrySet()) {
                String fieldName = entry.getKey();
                JsonNode fieldSchema = entry.getValue();
                JsonNode fieldValue = dataObj.get(fieldName);
                // 字段不存在且不满足 required 时，schema 中无约束则跳过
                String fieldPath = path + "." + fieldName;
                validateNode(fieldValue, fieldSchema, fieldPath, errors);
            }
        }
    }

    /**
     * 判断 JSON 节点是否符合指定的 JSON Schema 类型字符串。
     *
     * @param data         JSON 节点
     * @param expectedType 期望类型（string/number/integer/object/array/boolean/null）
     * @return true 表示类型匹配
     */
    private static boolean matchType(JsonNode data, String expectedType) {
        switch (expectedType) {
            case "string":
                return data.isTextual();
            case "number":
                return data.isNumber();
            case "integer":
                // JSON 不区分 int/double 语法，使用取整判断
                return data.isNumber() && data.asDouble() % 1 == 0;
            case "object":
                return data instanceof ObjectNode;
            case "array":
                return data.isArray();
            case "boolean":
                return data.isBoolean();
            case "null":
                return data.isNull();
            default:
                return false;
        }
    }

    /**
     * 获取节点的实际类型字符串（用于错误消息）。
     */
    private static String getActualType(JsonNode data) {
        if (data.isTextual()) {
            return "string";
        }
        if (data.isNumber()) {
            // JSON 不区分 int/double 语法，使用取整判断
            if (data.asDouble() % 1 == 0) {
                return "integer";
            }
            return "number";
        }
        if (data instanceof ObjectNode) {
            return "object";
        }
        if (data.isArray()) {
            return "array";
        }
        if (data.isBoolean()) {
            return "boolean";
        }
        if (data.isNull()) {
            return "null";
        }
        return "unknown";
    }
}
