package com.njydsz.pmis.common.json.tree;

import java.util.*;

/**
 * JSON 数组节点
 */
public final class ArrayNode extends JsonNode {

    private final List<JsonNode> elements;

    public ArrayNode() {
        this.elements = new ArrayList<>();
    }

    public ArrayNode(List<JsonNode> elements) {
        this.elements = new ArrayList<>(elements);
    }

    /**
     * 添加元素
     */
    public ArrayNode add(JsonNode node) {
        elements.add(node != null ? node : NullNode.getInstance());
        return this;
    }

    public ArrayNode add(String value) {
        elements.add(value != null ? new TextNode(value) : NullNode.getInstance());
        return this;
    }

    public ArrayNode add(int value) {
        elements.add(new NumberNode(value));
        return this;
    }

    public ArrayNode add(long value) {
        elements.add(new NumberNode(value));
        return this;
    }

    public ArrayNode add(double value) {
        elements.add(new NumberNode(value));
        return this;
    }

    public ArrayNode add(boolean value) {
        elements.add(BooleanNode.of(value));
        return this;
    }

    /**
     * 设置元素
     */
    public ArrayNode set(int index, JsonNode node) {
        elements.set(index, node);
        return this;
    }

    /**
     * 移除元素
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
        return elements.stream().map(JsonNode::asValue).toList();
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
}
