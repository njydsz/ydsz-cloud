package com.njydsz.common.json.tree;

import java.util.*;

/**
 * JSON 数组节点
 *
 * <p>对标 Jackson ArrayNode，表示一个 JSON 数组（有序元素集合），
 * 内部使用 ArrayList 存储元素。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li>支持动态添加/删除元素</li>
 *   <li>支持链式调用（Builder 模式）</li>
 *   <li>提供类型安全的元素访问</li>
 *   <li>支持转换为 List 和 JSON 字符串</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * ArrayNode array = new ArrayNode();
 * array.add("hello")
 *      .add(42)
 *      .add(true);
 *
 * // 获取元素
 * JsonNode first = array.get(0);
 * String value = first.asText();
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ArrayNode extends JsonNode {

    private final List<JsonNode> elements;

    /**
     * 创建空的 JSON 数组节点
     */
    public ArrayNode() {
        this.elements = new ArrayList<>();
    }

    /**
     * 使用现有元素列表创建 JSON 数组节点
     *
     * @param elements 元素列表
     */
    public ArrayNode(List<JsonNode> elements) {
        this.elements = new ArrayList<>(elements);
    }

    /**
     * 添加 JSON 节点元素
     *
     * @param node 要添加的节点，null 会被转换为 NullNode
     * @return 当前数组节点（支持链式调用）
     */
    public ArrayNode add(JsonNode node) {
        elements.add(node != null ? node : NullNode.getInstance());
        return this;
    }

    /**
     * 添加字符串元素
     *
     * @param value 字符串值，null 会被转换为 NullNode
     * @return 当前数组节点（支持链式调用）
     */
    public ArrayNode add(String value) {
        elements.add(value != null ? new TextNode(value) : NullNode.getInstance());
        return this;
    }

    /**
     * 添加整数元素
     *
     * @param value 整数值
     * @return 当前数组节点（支持链式调用）
     */
    public ArrayNode add(int value) {
        elements.add(new NumberNode(value));
        return this;
    }

    /**
     * 添加长整数元素
     *
     * @param value 长整数值
     * @return 当前数组节点（支持链式调用）
     */
    public ArrayNode add(long value) {
        elements.add(new NumberNode(value));
        return this;
    }

    /**
     * 添加双精度浮点数元素
     *
     * @param value 双精度浮点数值
     * @return 当前数组节点（支持链式调用）
     */
    public ArrayNode add(double value) {
        elements.add(new NumberNode(value));
        return this;
    }

    /**
     * 添加布尔元素
     *
     * @param value 布尔值
     * @return 当前数组节点（支持链式调用）
     */
    public ArrayNode add(boolean value) {
        elements.add(BooleanNode.of(value));
        return this;
    }

    /**
     * 设置指定索引位置的元素
     *
     * @param index 元素索引（从 0 开始）
     * @param node 新的节点值
     * @return 当前数组节点（支持链式调用）
     * @throws IndexOutOfBoundsException 如果索引超出范围
     */
    public ArrayNode set(int index, JsonNode node) {
        elements.set(index, node);
        return this;
    }

    /**
     * 移除指定索引位置的元素
     *
     * @param index 要移除的元素索引（从 0 开始）
     * @return 被移除的节点
     * @throws IndexOutOfBoundsException 如果索引超出范围
     */
    public JsonNode remove(int index) {
        return elements.remove(index);
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public JsonNode get(int index) {
        if (index >= 0 && index < elements.size()) {
            return elements.get(index);
        }
        return MissingNode.getInstance();
    }

    @Override
    public String asText() {
        return toString();
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public boolean has(int index) {
        return index >= 0 && index < elements.size();
    }

    @Override
    public Iterator<JsonNode> elements() {
        return Collections.unmodifiableList(elements).iterator();
    }

    @Override
    public List<JsonNode> asList() {
        return Collections.unmodifiableList(elements);
    }

    @Override
    public Object asValue() {
        return elements.stream().map(node -> node != null ? node.asValue() : null).toList();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(elements.get(i).toString());
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ArrayNode)) {
            return false;
        }
        return elements.equals(((ArrayNode) obj).elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    /**
     * 从 List 创建 ArrayNode（适配器方法，用于从 JsonArray 迁移）。
     *
     * <p>此方法为 {@link com.njydsz.common.json.object.JsonArray}（已废弃）的迁移提供桥接。
     * JsonArray 继承自 ArrayList，可直接传入此方法。
     *
     * <pre>{@code
     * JsonArray legacy = YdszJson.parseArrayToJsonArray(json);
     * ArrayNode node = ArrayNode.fromList(legacy);
     * }</pre>
     *
     * @param list 源 List，null 返回空 ArrayNode
     * @return ArrayNode 实例
     * @since 1.0.0
     */
    public static ArrayNode fromList(List<?> list) {
        ArrayNode node = new ArrayNode();
        if (list == null) {
            return node;
        }
        for (Object value : list) {
            if (value == null) {
                node.elements.add(NullNode.getInstance());
            } else if (value instanceof JsonNode) {
                node.elements.add((JsonNode) value);
            } else {
                node.elements.add(TreeConverter.convertToJsonNode(value));
            }
        }
        return node;
    }
}
