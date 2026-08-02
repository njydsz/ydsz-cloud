package com.njydsz.common.json.tree;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JSON 对象节点
 *
 * <p>对标 Jackson ObjectNode，表示一个 JSON 对象（key-value 结构），
 * 内部使用 LinkedHashMap 保持字段插入顺序。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li>支持动态添加/删除字段</li>
 *   <li>支持链式调用（Builder 模式）</li>
 *   <li>提供类型安全的字段访问</li>
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
 * JsonNode nameNode = node.get("name");
 * String name = nameNode.asText();
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
            sb.append('"');
            escapeJsonString(sb, entry.getKey());
            sb.append('"');
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
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ObjectNode)) {
            return false;
        }
        return fields.equals(((ObjectNode) obj).fields);
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }
}
