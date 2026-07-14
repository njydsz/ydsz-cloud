package com.njydsz.pmis.common.json.tree;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JSON 节点基类
 *
 * <p>对标 Jackson JsonNode，提供树模型 JSON 操作。</p>
 *
 * <p><b>节点类型：</b></p>
 * <ul>
 *   <li>ObjectNode - JSON 对象</li>
 *   <li>ArrayNode - JSON 数组</li>
 *   <li>TextNode - JSON 字符串</li>
 *   <li>NumberNode - JSON 数值</li>
 *   <li>BooleanNode - JSON 布尔值</li>
 *   <li>NullNode - JSON null</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * ObjectNode node = YdszJson.tree("{\"name\":\"John\",\"age\":30}");
 * String name = node.get("name").asText();
 * int age = node.get("age").asInt();
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 * @since 1.3.0
 */
public abstract class JsonNode {

    /**
     * 判断是否为对象
     */
    public boolean isObject() {
        return false;
    }

    /**
     * 判断是否为数组
     */
    public boolean isArray() {
        return false;
    }

    /**
     * 判断是否为字符串
     */
    public boolean isTextual() {
        return false;
    }

    /**
     * 判断是否为数值
     */
    public boolean isNumber() {
        return false;
    }

    /**
     * 判断是否为布尔值
     */
    public boolean isBoolean() {
        return false;
    }

    /**
     * 判断是否为 null
     */
    public boolean isNull() {
        return false;
    }

    /**
     * 获取子节点
     *
     * @param fieldName 字段名
     * @return 子节点，不存在返回 MissingNode
     */
    public JsonNode get(String fieldName) {
        return MissingNode.getInstance();
    }

    /**
     * 获取子节点（数组索引）
     *
     * @param index 数组索引
     * @return 子节点
     */
    public JsonNode get(int index) {
        return MissingNode.getInstance();
    }

    /**
     * 获取子节点（支持路径表达式）
     *
     * @param path 路径表达式，如 "user/name"
     * @return 子节点
     */
    public JsonNode path(String path) {
        String[] parts = path.split("/");
        JsonNode current = this;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (current.isArray()) {
                try {
                    current = current.get(Integer.parseInt(part));
                } catch (NumberFormatException e) {
                    return MissingNode.getInstance();
                }
            } else {
                current = current.get(part);
            }
            if (current.isMissing()) {
                return current;
            }
        }
        return current;
    }

    /**
     * 转换为字符串
     */
    public String asText() {
        return "";
    }

    /**
     * 转换为字符串（带默认值）
     */
    public String asText(String defaultValue) {
        return asText();
    }

    /**
     * 转换为整数
     */
    public int asInt() {
        return 0;
    }

    /**
     * 转换为整数（带默认值）
     */
    public int asInt(int defaultValue) {
        return asInt();
    }

    /**
     * 转换为长整数
     */
    public long asLong() {
        return 0L;
    }

    /**
     * 转换为长整数（带默认值）
     */
    public long asLong(long defaultValue) {
        return asLong();
    }

    /**
     * 转换为双精度数
     */
    public double asDouble() {
        return 0.0;
    }

    /**
     * 转换为双精度数（带默认值）
     */
    public double asDouble(double defaultValue) {
        return asDouble();
    }

    /**
     * 转换为布尔值
     */
    public boolean asBoolean() {
        return false;
    }

    /**
     * 转换为布尔值（带默认值）
     */
    public boolean asBoolean(boolean defaultValue) {
        return asBoolean();
    }

    /**
     * 判断是否为缺失节点
     */
    public boolean isMissing() {
        return false;
    }

    /**
     * 获取子节点数量
     */
    public int size() {
        return 0;
    }

    /**
     * 是否有指定字段
     */
    public boolean has(String fieldName) {
        return false;
    }

    /**
     * 是否有指定索引
     */
    public boolean has(int index) {
        return false;
    }

    /**
     * 字段名迭代器（仅对象）
     */
    public Iterator<String> fieldNames() {
        return Collections.emptyIterator();
    }

    /**
     * 元素迭代器（仅数组）
     */
    public Iterator<JsonNode> elements() {
        return Collections.emptyIterator();
    }

    /**
     * 转换为 Map
     */
    public Map<String, JsonNode> asMap() {
        return Collections.emptyMap();
    }

    /**
     * 转换为 List
     */
    public List<JsonNode> asList() {
        return Collections.emptyList();
    }

    /**
     * 转换为原始值
     */
    public Object asValue() {
        return null;
    }

    @Override
    public abstract String toString();

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);
}
