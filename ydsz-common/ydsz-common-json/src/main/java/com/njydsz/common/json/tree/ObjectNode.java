package com.njydsz.common.json.tree;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import com.njydsz.common.json.YdszJson;

/**
 * JSON 对象节点
 *
 * <p>对标 Jackson ObjectNode / FastJSON2 JSONObject，表示一个 JSON 对象（key-value 结构），
 * 内部使用 LinkedHashMap 保持字段插入顺序。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li>支持动态添加/删除字段</li>
 *   <li>支持链式调用（Builder 模式）</li>
 *   <li>提供类型安全的字段访问（getString/getInteger/getLong/getBoolean 等）</li>
 *   <li>支持转换为 Map 和 JSON 字符串</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * ObjectNode node = new ObjectNode();
 * node.put("name", "John")
 *     .put("age", 30)
 *     .put("active", true);
 *
 * // 获取字段
 * String name = node.getString("name");
 * int age = node.getIntValue("age");
 * ObjectNode sub = node.getObjectNode("sub");
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ObjectNode extends JsonNode {

    private final LinkedHashMap<String, JsonNode> fields;

    /**
     * 创建空的 JSON 对象节点
     */
    public ObjectNode() {
        this.fields = new LinkedHashMap<>();
    }

    /**
     * 使用现有字段集合创建 JSON 对象节点
     *
     * @param fields 字段集合
     */
    public ObjectNode(Map<String, JsonNode> fields) {
        this.fields = new LinkedHashMap<>(fields);
    }

    /**
     * 添加字符串字段
     *
     * @param name 字段名
     * @param value 字段值，null 会被转换为 NullNode
     * @return 当前对象节点（支持链式调用）
     */
    public ObjectNode put(String name, String value) {
        fields.put(name, value != null ? new TextNode(value) : NullNode.getInstance());
        return this;
    }

    /**
     * 添加整数字段
     *
     * @param name 字段名
     * @param value 字段值
     * @return 当前对象节点（支持链式调用）
     */
    public ObjectNode put(String name, int value) {
        fields.put(name, new NumberNode(value));
        return this;
    }

    /**
     * 添加长整数字段
     *
     * @param name 字段名
     * @param value 字段值
     * @return 当前对象节点（支持链式调用）
     */
    public ObjectNode put(String name, long value) {
        fields.put(name, new NumberNode(value));
        return this;
    }

    /**
     * 添加双精度浮点数字段
     *
     * @param name 字段名
     * @param value 字段值
     * @return 当前对象节点（支持链式调用）
     */
    public ObjectNode put(String name, double value) {
        fields.put(name, new NumberNode(value));
        return this;
    }

    /**
     * 添加布尔字段
     *
     * @param name 字段名
     * @param value 字段值
     * @return 当前对象节点（支持链式调用）
     */
    public ObjectNode put(String name, boolean value) {
        fields.put(name, BooleanNode.of(value));
        return this;
    }

    /**
     * 添加 JSON 节点字段
     *
     * @param name 字段名
     * @param node 节点值，null 会被转换为 NullNode
     * @return 当前对象节点（支持链式调用）
     */
    public ObjectNode put(String name, JsonNode node) {
        fields.put(name, node != null ? node : NullNode.getInstance());
        return this;
    }

    /**
     * 设置字段值（如果字段已存在则更新，不存在则添加）
     *
     * @param name 字段名
     * @param node 节点值
     * @return 当前对象节点（支持链式调用）
     */
    public ObjectNode set(String name, JsonNode node) {
        return put(name, node);
    }

    /**
     * 移除指定字段
     *
     * @param name 要移除的字段名
     * @return 被移除的节点，如果字段不存在返回 null
     */
    public JsonNode remove(String name) {
        return fields.remove(name);
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public JsonNode get(String fieldName) {
        JsonNode node = fields.get(fieldName);
        return node != null ? node : MissingNode.getInstance();
    }

    @Override
    public String asText() {
        return toString();
    }

    @Override
    public String asText(String defaultValue) {
        return defaultValue;
    }

    @Override
    public int size() {
        return fields.size();
    }

    @Override
    public boolean has(String fieldName) {
        return fields.containsKey(fieldName);
    }

    @Override
    public Iterator<String> fieldNames() {
        return Collections.unmodifiableSet(fields.keySet()).iterator();
    }

    @Override
    public Map<String, JsonNode> asMap() {
        return Collections.unmodifiableMap(fields);
    }

    @Override
    public Object asValue() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : fields.entrySet()) {
            JsonNode node = entry.getValue();
            result.put(entry.getKey(), node != null ? node.asValue() : null);
        }
        return result;
    }

    /**
     * 深拷贝当前 ObjectNode 及其所有嵌套子节点。
     *
     * @return 全新的 ObjectNode 副本
     */
    @Override
    public ObjectNode deepCopy() {
        ObjectNode copy = new ObjectNode();
        for (Map.Entry<String, JsonNode> entry : fields.entrySet()) {
            JsonNode node = entry.getValue();
            copy.fields.put(entry.getKey(), node != null ? node.deepCopy() : NullNode.getInstance());
        }
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, JsonNode> entry : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"');\n            escapeJsonString(sb, entry.getKey());\n            sb.append('"');
            sb.append(':');
            sb.append(entry.getValue().toString());
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * JSON 字符串转义处理
     *
     * <p>将特殊字符转换为转义序列，确保生成合法的 JSON 字符串。</p>
     *
     * @param sb 字符串构建器
     * @param text 待转义的文本
     */
    static void escapeJsonString(StringBuilder sb, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;\n                case '\\': sb.append("\\\\"); break;\n                case '\n': sb.append("\\n"); break;\n                case '\r': sb.append("\\r"); break;\n                case '\t': sb.append("\\t"); break;\n                case '\b': sb.append("\\b"); break;\n                case '\f': sb.append("\\f"); break;\n                default:\n                    if (c < 0x20) {\n                        sb.append(String.format("\\u%04x", (int) c));\n                    } else {\n                        sb.append(c);\n                    }\n            }\n        }\n    }\n\n    // ==================== Map-like 便捷 getter（FastJSON2/Gson 风格） ====================\n\n    /**\n     * 获取字符串值，不存在或 null 返回 null。\n     *\n     * @param name 字段名\n     * @return 字符串值\n     */\n    public String getString(String name) {\n        JsonNode node = fields.get(name);\n        if (node == null || node.isNull() || node.isMissing()) {\n            return null;\n        }\n        return node.asText();\n    }\n\n    /**\n     * 获取整数值，不存在或 null 返回 null。\n     *\n     * @param name 字段名\n     * @return 整数值\n     */\n    public Integer getInteger(String name) {\n        JsonNode node = fields.get(name);\n        if (node == null || node.isNull() || node.isMissing()) {\n            return null;\n        }\n        if (node.isNumber()) {\n            return node.asInt();\n        }\n        try {\n            return Integer.parseInt(node.asText());\n        } catch (NumberFormatException e) {\n            return null;\n        }\n    }\n\n    /**\n     * 获取 int 基本类型值（为 null 时返回 0）。\n     *\n     * @param name 字段名\n     * @return int 值\n     */\n    public int getIntValue(String name) {\n        Integer value = getInteger(name);\n        return value != null ? value : 0;\n    }\n\n    /**\n     * 获取长整数值，不存在或 null 返回 null。\n     *\n     * @param name 字段名\n     * @return 长整数值\n     */\n    public Long getLong(String name) {\n        JsonNode node = fields.get(name);\n        if (node == null || node.isNull() || node.isMissing()) {\n            return null;\n        }\n        if (node.isNumber()) {\n            return node.asLong();\n        }\n        try {\n            return Long.parseLong(node.asText());\n        } catch (NumberFormatException e) {\n            return null;\n        }\n    }\n\n    /**\n     * 获取 long 基本类型值（为 null 时返回 0）。\n     *\n     * @param name 字段名\n     * @return long 值\n     */\n    public long getLongValue(String name) {\n        Long value = getLong(name);\n        return value != null ? value : 0L;\n    }\n\n    /**\n     * 获取双精度浮点数值，不存在或 null 返回 null。\n     *\n     * @param name 字段名\n     * @return 双精度浮点数值\n     */\n    public Double getDouble(String name) {\n        JsonNode node = fields.get(name);\n        if (node == null || node.isNull() || node.isMissing()) {\n            return null;\n        }\n        if (node.isNumber()) {\n            return node.asDouble();\n        }\n        try {\n            return Double.parseDouble(node.asText());\n        } catch (NumberFormatException e) {\n            return null;\n        }\n    }\n\n    /**\n     * 获取 double 基本类型值（为 null 时返回 0）。\n     *\n     * @param name 字段名\n     * @return double 值\n     */\n    public double getDoubleValue(String name) {\n        Double value = getDouble(name);\n        return value != null ? value : 0.0;\n    }\n\n    /**\n     * 获取浮点数值，不存在或 null 返回 null。\n     *\n     * @param name 字段名\n     * @return Float 值\n     */\n    public Float getFloat(String name) {\n        Double value = getDouble(name);\n        return value != null ? value.floatValue() : null;\n    }\n\n    /**\n     * 获取 float 基本类型值（为 null 时返回 0）。\n     *\n     * @param name 字段名\n     * @return float 值\n     */\n    public float getFloatValue(String name) {\n        Float value = getFloat(name);\n        return value != null ? value : 0.0f;\n    }\n\n    /**\n     * 获取布尔值，不存在或 null 返回 null。\n     *\n     * @param name 字段名\n     * @return 布尔值\n     */\n    public Boolean getBoolean(String name) {\n        return nodeToBoolean(fields.get(name));\n    }\n\n    /**\n     * 获取 boolean 基本类型值（为 null 时返回 false）。\n     *\n     * @param name 字段名\n     * @return boolean 值\n     */\n    public boolean getBooleanValue(String name) {\n        Boolean value = getBoolean(name);\n        return value != null ? value : false;\n    }\n\n    /**\n     * 获取 BigDecimal 值，不存在或 null 返回 null。\n     *\n     * @param name 字段名\n     * @return BigDecimal 值\n     */\n    public BigDecimal getBigDecimal(String name) {\n        return nodeToBigDecimal(fields.get(name));\n    }\n\n    /**\n     * 获取 BigInteger 值，不存在或 null 返回 null。\n     *\n     * @param name 字段名\n     * @return BigInteger 值\n     */\n    public BigInteger getBigInteger(String name) {\n        return nodeToBigInteger(fields.get(name));\n    }\n\n    /**\n     * 获取嵌套对象节点（推荐使用的命名风格），不存在或非对象返回 null。\n     *\n     * @param name 字段名\n     * @return ObjectNode 实例\n     * @since 1.1.0\n     */\n    public ObjectNode getObjectNode(String name) {\n        JsonNode node = fields.get(name);\n        if (node instanceof ObjectNode objNode) {\n            return objNode;\n        }\n        return null;\n    }\n\n    /**\n     * 获取嵌套数组节点（推荐使用的命名风格），不存在或非数组返回 null。\n     *\n     * @param name 字段名\n     * @return ArrayNode 实例\n     * @since 1.1.0\n     */\n    public ArrayNode getArrayNode(String name) {\n        JsonNode node = fields.get(name);\n        if (node instanceof ArrayNode arrNode) {\n            return arrNode;\n        }\n        return null;\n    }\n\n    /**\n     * 获取节点并转换为指定类型。\n     *\n     * @param name 字段名\n     * @param clazz 目标类型\n     * @param <T> 类型参数\n     * @return 转换后的对象\n     */\n    public <T> T getObject(String name, Class<T> clazz) {\n        JsonNode node = fields.get(name);\n        if (node == null || node.isNull() || node.isMissing()) {\n            return null;\n        }\n        return YdszJson.fromJson(node.toString(), clazz);\n    }\n\n    /**\n     * 获取字符串值或默认值。\n     *\n     * @param name 字段名\n     * @param defaultValue 默认值\n     * @return 字符串值或默认值\n     */\n    public String getStringOrDefault(String name, String defaultValue) {\n        String value = getString(name);\n        return value != null ? value : defaultValue;\n    }\n\n    /**\n     * 获取整数值或默认值。\n     *\n     * @param name 字段名\n     * @param defaultValue 默认值\n     * @return 整数值或默认值\n     */\n    public Integer getIntegerOrDefault(String name, Integer defaultValue) {\n        Integer value = getInteger(name);\n        return value != null ? value : defaultValue;\n    }\n\n    /**\n     * 获取长整数值或默认值。\n     *\n     * @param name 字段名\n     * @param defaultValue 默认值\n     * @return 长整数值或默认值\n     */\n    public Long getLongOrDefault(String name, Long defaultValue) {\n        Long value = getLong(name);\n        return value != null ? value : defaultValue;\n    }\n\n    /**\n     * 获取布尔值或默认值。\n     *\n     * @param name 字段名\n     * @param defaultValue 默认值\n     * @return 布尔值或默认值\n     */\n    public Boolean getBooleanOrDefault(String name, Boolean defaultValue) {\n        Boolean value = getBoolean(name);\n        return value != null ? value : defaultValue;\n    }\n\n    // ==================== Map-like 查询 ====================\n\n    /**\n     * 是否包含指定字段。\n     *\n     * @param name 字段名\n     * @return 包含返回 true\n     */\n    public boolean containsKey(String name) {\n        return fields.containsKey(name);\n    }\n\n    /**\n     * 是否为空对象。\n     *\n     * @return 为空返回 true\n     */\n    public boolean isEmpty() {\n        return fields.isEmpty();\n    }\n\n    /**\n     * 获取所有字段名。\n     *\n     * @return 字段名集合\n     */\n    public Set<String> keySet() {\n        return Collections.unmodifiableSet(fields.keySet());\n    }\n\n    /**\n     * 获取所有字段值。\n     *\n     * @return 字段值集合\n     */\n    public Collection<JsonNode> values() {\n        return Collections.unmodifiableCollection(fields.values());\n    }\n\n    /**\n     * 获取字段 entry 集合。\n     *\n     * @return entry 集合\n     */\n    public Set<Map.Entry<String, JsonNode>> entrySet() {\n        return Collections.unmodifiableSet(fields.entrySet());\n    }\n\n    // ==================== 通用 put（支持任意值） ====================\n\n    /**\n     * 添加任意值字段（自动转换为 JsonNode）。\n     *\n     * @param name 字段名\n     * @param value 字段值，null 转换为 NullNode\n     * @return 当前对象节点（支持链式调用）\n     */\n    public ObjectNode put(String name, Object value) {\n        if (value == null) {\n            fields.put(name, NullNode.getInstance());\n        } else if (value instanceof JsonNode node) {\n            fields.put(name, node);\n        } else if (value instanceof String str) {\n            fields.put(name, new TextNode(str));\n        } else if (value instanceof Boolean bool) {\n            fields.put(name, BooleanNode.of(bool));\n        } else if (value instanceof Number num) {\n            fields.put(name, new NumberNode(num));\n        } else if (value instanceof Map<?, ?> map) {\n            fields.put(name, ObjectNode.fromMap(map));\n        } else if (value instanceof List<?> list) {\n            fields.put(name, ArrayNode.fromList(list));\n        } else {\n            fields.put(name, TreeConverter.convertToJsonNode(value));\n        }\n        return this;\n    }\n\n    // ==================== 转换 ====================\n\n    /**\n     * 转换为 JSON 字符串。\n     *\n     * @return JSON 字符串\n     */\n    public String toJsonString() {\n        return toString();\n    }\n\n    @Override\n    public boolean equals(Object obj) {\n        if (this == obj) {\n            return true;\n        }\n        if (!(obj instanceof ObjectNode)) {\n            return false;\n        }\n        return fields.equals(((ObjectNode) obj).fields);\n    }\n\n    @Override\n    public int hashCode() {\n        return fields.hashCode();\n    }\n\n    /**\n     * 从 Map 创建 ObjectNode。\n     *\n     * @param map 源 Map，null 返回空 ObjectNode\n     * @return ObjectNode 实例\n     * @since 1.0.0\n     */\n    public static ObjectNode fromMap(Map<?, ?> map) {\n        ObjectNode node = new ObjectNode();\n        if (map == null) {\n            return node;\n        }\n        for (Map.Entry<?, ?> entry : map.entrySet()) {\n            String key = entry.getKey() instanceof String ? (String) entry.getKey() : String.valueOf(entry.getKey());\n            Object value = entry.getValue();\n            if (value == null) {\n                node.fields.put(key, NullNode.getInstance());\n            } else if (value instanceof JsonNode) {\n                node.fields.put(key, (JsonNode) value);\n            } else {\n                node.fields.put(key, TreeConverter.convertToJsonNode(value));\n            }\n        }\n        return node;\n    }\n}\n