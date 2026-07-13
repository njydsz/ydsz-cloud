package com.njydsz.pmis.common.json.tree;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JSON 对象节点
 *
 * <p>对标 Jackson ObjectNode，支持动态添加/删除字段。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class ObjectNode extends JsonNode {

    private final LinkedHashMap<String, JsonNode> fields;

    public ObjectNode() {
        this.fields = new LinkedHashMap<>();
    }

    public ObjectNode(Map<String, JsonNode> fields) {
        this.fields = new LinkedHashMap<>(fields);
    }

    /**
     * 添加字段
     */
    public ObjectNode put(String name, String value) {
        fields.put(name, value != null ? new TextNode(value) : NullNode.getInstance());
        return this;
    }

    public ObjectNode put(String name, int value) {
        fields.put(name, new NumberNode(value));
        return this;
    }

    public ObjectNode put(String name, long value) {
        fields.put(name, new NumberNode(value));
        return this;
    }

    public ObjectNode put(String name, double value) {
        fields.put(name, new NumberNode(value));
        return this;
    }

    public ObjectNode put(String name, boolean value) {
        fields.put(name, BooleanNode.of(value));
        return this;
    }

    public ObjectNode put(String name, JsonNode node) {
        fields.put(name, node != null ? node : NullNode.getInstance());
        return this;
    }

    /**
     * 设置字段（如果存在则更新）
     */
    public ObjectNode set(String name, JsonNode node) {
        return put(name, node);
    }

    /**
     * 移除字段
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
        return fields.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                JsonNode node = e.getValue();
                return node != null ? node.asValue() : null;
            }));
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
