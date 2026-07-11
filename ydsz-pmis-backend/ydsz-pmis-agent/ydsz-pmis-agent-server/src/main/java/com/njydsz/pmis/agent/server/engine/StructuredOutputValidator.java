package com.njydsz.pmis.agent.server.engine.llm;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 结构化输出 Schema 验证器（P4-7 落地）。
 *
 * <p>对标 OpenAI Structured Outputs / Coze 输出格式化：
 * <ul>
 *   <li>验证 LLM 返回的 JSON 是否符合预期的 JSON Schema</li>
 *   <li>支持类型检查、必填字段、枚举值、数组长度等约束</li>
 *   <li>验证失败时提供详细的错误信息，便于自动重试或提示 LLM 修正</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 * // 定义预期 Schema
 * Map&lt;String, Object&gt; schema = Map.of(
 *     "type", "object",
 *     "properties", Map.of(
 *         "thought", Map.of("type", "string"),
 *         "action", Map.of("type", "string"),
 *         "finalAnswer", Map.of("type", "string")
 *     ),
 *     "required", List.of("thought")
 * );
 *
 * // 验证 LLM 输出
 * ValidationResult result = StructuredOutputValidator.validate(llmOutput, schema);
 * if (!result.isValid()) {
 *     // 追加错误提示，让 LLM 重新生成
 *     String retryPrompt = result.getErrors().toString();
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-7)
 */
@Slf4j
public class StructuredOutputValidator {

    /**
     * 验证结果。
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }

        @Override
        public String toString() {
            return valid ? "VALID" : "INVALID: " + String.join("; ", errors);
        }
    }

    /**
     * 验证 JSON 字符串是否符合 Schema。
     *
     * @param jsonStr LLM 返回的 JSON 字符串
     * @param schema  预期的 JSON Schema（Map 形式）
     * @return 验证结果
     */
    public static ValidationResult validate(String jsonStr, Map<String, Object> schema) {
        if (jsonStr == null || jsonStr.isBlank()) {
            return ValidationResult.failure(List.of("JSON 字符串为空"));
        }
        if (schema == null || schema.isEmpty()) {
            return ValidationResult.success(); // 无 schema 约束
        }

        // 清理 markdown 代码块包裹
        String cleaned = LlmProvider.stripMarkdownCodeFence(jsonStr);

        Object json;
        try {
            json = JSON.parse(cleaned);
        } catch (Exception e) {
            return ValidationResult.failure(List.of("JSON 解析失败: " + e.getMessage()));
        }

        List<String> errors = new ArrayList<>();
        validateValue(json, schema, "$", errors);

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 递归验证 JSON 值。
     *
     * @param value  JSON 值
     * @param schema Schema 定义
     * @param path   当前路径（用于错误信息）
     * @param errors 错误收集列表
     */
    private static void validateValue(Object value, Map<String, Object> schema,
                                       String path, List<String> errors) {
        if (schema == null) return;

        String type = schema.get("type") == null ? "object" : schema.get("type").toString();

        // null 检查
        if (value == null) {
            if (!isOptional(schema)) {
                errors.add(path + ": 值为 null 但字段非可选");
            }
            return;
        }

        // 类型验证
        switch (type) {
            case "object":
                validateObject(value, schema, path, errors);
                break;
            case "array":
                validateArray(value, schema, path, errors);
                break;
            case "string":
                if (!(value instanceof String)) {
                    errors.add(path + ": 期望 string 类型, 实际 " + value.getClass().getSimpleName());
                }
                break;
            case "integer":
                if (!(value instanceof Integer) && !(value instanceof Long)) {
                    errors.add(path + ": 期望 integer 类型, 实际 " + value.getClass().getSimpleName());
                }
                break;
            case "number":
                if (!(value instanceof Number)) {
                    errors.add(path + ": 期望 number 类型, 实际 " + value.getClass().getSimpleName());
                }
                break;
            case "boolean":
                if (!(value instanceof Boolean)) {
                    errors.add(path + ": 期望 boolean 类型, 实际 " + value.getClass().getSimpleName());
                }
                break;
        }

        // 枚举验证
        Object enumObj = schema.get("enum");
        if (enumObj instanceof List<?> enumList && !enumList.isEmpty()) {
            String strValue = value.toString();
            if (!enumList.contains(strValue) && !enumList.contains(value)) {
                errors.add(path + ": 值 '" + strValue + "' 不在枚举 " + enumList + " 中");
            }
        }
    }

    /**
     * 验证 object 类型。
     */
    @SuppressWarnings("unchecked")
    private static void validateObject(Object value, Map<String, Object> schema,
                                        String path, List<String> errors) {
        if (!(value instanceof Map<?, ?> map)) {
            errors.add(path + ": 期望 object 类型, 实际 " + value.getClass().getSimpleName());
            return;
        }

        // 必填字段验证
        Object requiredObj = schema.get("required");
        if (requiredObj instanceof List<?> requiredList) {
            for (Object req : requiredList) {
                if (req != null && !map.containsKey(req.toString())) {
                    errors.add(path + "." + req + ": 必填字段缺失");
                }
            }
        }

        // 属性验证
        Object propertiesObj = schema.get("properties");
        if (propertiesObj instanceof Map<?, ?> properties) {
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String propName = entry.getKey().toString();
                if (map.containsKey(propName)) {
                    Object propSchema = entry.getValue();
                    if (propSchema instanceof Map<?, ?> ps) {
                        validateValue(map.get(propName), (Map<String, Object>) ps,
                                path + "." + propName, errors);
                    }
                }
            }
        }
    }

    /**
     * 验证 array 类型。
     */
    @SuppressWarnings("unchecked")
    private static void validateArray(Object value, Map<String, Object> schema,
                                       String path, List<String> errors) {
        if (!(value instanceof List<?> list)) {
            errors.add(path + ": 期望 array 类型, 实际 " + value.getClass().getSimpleName());
            return;
        }

        // 最小长度
        Object minItems = schema.get("minItems");
        if (minItems instanceof Number minNum && list.size() < minNum.intValue()) {
            errors.add(path + ": 数组长度 " + list.size() + " 小于最小值 " + minNum.intValue());
        }

        // 最大长度
        Object maxItems = schema.get("maxItems");
        if (maxItems instanceof Number maxNum && list.size() > maxNum.intValue()) {
            errors.add(path + ": 数组长度 " + list.size() + " 超过最大值 " + maxNum.intValue());
        }

        // 元素验证
        Object itemsSchema = schema.get("items");
        if (itemsSchema instanceof Map<?, ?> items) {
            for (int i = 0; i < list.size(); i++) {
                validateValue(list.get(i), (Map<String, Object>) items,
                        path + "[" + i + "]", errors);
            }
        }
    }

    /**
     * 判断字段是否可选（无 required 约束或不在 required 列表中）。
     */
    private static boolean isOptional(Map<String, Object> schema) {
        Object requiredObj = schema.get("required");
        return requiredObj == null;
    }
}
